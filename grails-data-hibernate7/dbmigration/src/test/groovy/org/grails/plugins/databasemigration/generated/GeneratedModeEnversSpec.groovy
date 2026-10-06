/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.plugins.databasemigration.generated

import groovy.sql.Sql
import grails.gorm.annotation.Entity
import org.hibernate.Session
import org.hibernate.boot.spi.MetadataImplementor
import org.hibernate.dialect.H2Dialect
import org.hibernate.envers.AuditReader
import org.hibernate.envers.AuditReaderFactory
import org.hibernate.envers.Audited
import org.hibernate.envers.NotAudited
import org.hibernate.envers.RevisionType
import org.hibernate.mapping.Table
import spock.lang.AutoCleanup
import spock.lang.PendingFeature
import spock.lang.Specification

import org.grails.orm.hibernate.HibernateDatastore

/**
 * Hibernate Envers over the generated domain classes. Envers builds its audit mappings from the entities bound when its
 * contributor runs, reading the annotations of the bound classes, so the generated classes are bound before it and carry
 * the Envers annotations of the domain classes. The audit tables, the revisions and the instances the audit queries return
 * are those of binder mode, with the differences that are named in the features below.
 */
class GeneratedModeEnversSpec extends Specification {

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(boolean generated, List<Class> classes, Map extra = [:]) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'                  : "jdbc:h2:mem:enversSpec${generated}${System.nanoTime()};LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1".toString(),
                'dataSource.dialect'              : H2Dialect.name,
                'dataSource.dbCreate'             : 'create-drop',
                'hibernate.hbm2ddl.auto'          : 'create',
                'hibernate.cache.queries'         : 'false',
                'hibernate.cache.use_query_cache' : 'false',
                'hibernate.generatedDomainClasses': generated,
        ] + extra, classes as Class[])
        return datastore
    }

    private static Map<String, List<String>> columnsByTable(HibernateDatastore datastore) {
        Map<String, List<String>> tables = [:]
        for (Table table : ((MetadataImplementor) datastore.metadata).collectTableMappings()) {
            tables[table.name.toLowerCase()] = table.columns*.name*.toLowerCase().sort()
        }
        return tables
    }

    private static Map bookHistory() {
        Long bookId
        Long novelId
        EnversBook.withTransaction {
            bookId = new EnversBook(title: 'one', pages: 10, secret: 's').save(flush: true, failOnError: true).id
            novelId = new EnversNovel(title: 'tale', pages: 300, secret: 't', genre: 'drama').save(flush: true, failOnError: true).id
        }
        EnversBook.withTransaction {
            EnversBook book = EnversBook.get(bookId)
            book.title = 'two'
            book.save(flush: true, failOnError: true)
        }
        EnversBook.withTransaction {
            EnversBook.get(bookId).delete(flush: true)
        }
        Map result = [:]
        EnversBook.withNewSession { Session session ->
            AuditReader reader = AuditReaderFactory.get(session)
            List<Number> revisions = reader.getRevisions(EnversBook, bookId)
            result.revisionCount = revisions.size()
            result.history = revisions.collect { Number revision ->
                EnversBook found = reader.find(EnversBook, bookId, revision)
                found == null ? null : [found.title, found.pages, found.getClass() == EnversBook]
            }
            List rows = reader.createQuery().forRevisionsOfEntity(EnversBook, false, true).resultList
            result.revisionTypes = rows.collect { Object[] row -> ((RevisionType) row[2]).name() }.sort()
            result.rowClasses = rows.collect { Object[] row -> row[0].getClass().simpleName }.sort()
            result.entitiesOnly = reader.createQuery().forRevisionsOfEntity(EnversBook, true, true).resultList
                    *.getClass()*.simpleName.sort()
            EnversBook novel = reader.find(EnversBook, novelId, reader.getRevisions(EnversBook, novelId).first())
            result.novel = [novel.getClass().simpleName, novel instanceof EnversNovel ? ((EnversNovel) novel).genre : null]
            result.atRevision = reader.createQuery().forEntitiesAtRevision(EnversBook, revisions.first()).resultList
                    .collect { EnversBook found -> [found.getClass().simpleName, found.title] }.sort { it[1] }
        }
        return result
    }

    def "an audited domain class has audit tables, revisions and audit queries that return real instances in generated mode"() {
        given:
        HibernateDatastore generated = boot(true, [EnversBook, EnversNovel])

        expect: 'the audit tables are mapped'
        columnsByTable(generated).keySet().containsAll(['envers_book_aud', 'revinfo'])

        when:
        Map history = bookHistory()

        then: 'each change is a revision and the audit queries return the domain classes'
        history.revisionCount == 3
        history.revisionTypes == ['ADD', 'ADD', 'DEL', 'MOD']
        history.history == [['one', 10, true], ['two', 10, true], null]
        history.rowClasses == ['EnversBook', 'EnversBook', 'EnversBook', 'EnversNovel']
        history.entitiesOnly == ['EnversBook', 'EnversBook', 'EnversBook', 'EnversNovel']
        history.novel == ['EnversNovel', 'drama']
        history.atRevision.collect { it[0] }.toSet() == ['EnversBook', 'EnversNovel'].toSet()
    }

    def "generated mode audits the same entities and writes the same revisions as binder mode"() {
        when:
        HibernateDatastore binderDatastore = boot(false, [EnversBook, EnversNovel])
        Map binderTables = columnsByTable(binderDatastore)
        Map binderHistory = bookHistory()
        HibernateDatastore generatedDatastore = boot(true, [EnversBook, EnversNovel])
        Map generatedTables = columnsByTable(generatedDatastore)
        Map generatedHistory = bookHistory()

        then: 'the same tables exist'
        binderTables.keySet() == generatedTables.keySet()

        and: 'the audited entities are bound alike and the revision tables are equal'
        binderTables.findAll { String name, List<String> columns -> !name.endsWith('_aud') } ==
                generatedTables.findAll { String name, List<String> columns -> !name.endsWith('_aud') }

        and: 'the audit table of the entity differs only in the columns named in the next feature'
        binderTables['envers_book_aud'] - generatedTables['envers_book_aud'] == ['secret']
        generatedTables['envers_book_aud'] - binderTables['envers_book_aud'] == []

        and: 'the revisions and the audit queries return the same'
        binderHistory == generatedHistory
    }

    def "a field level @NotAudited and the optimistic locking version are honoured the way Envers does for annotated entities"() {
        when:
        Map binder = columnsByTable(boot(false, [EnversBook, EnversNovel]))
        Map generated = columnsByTable(boot(true, [EnversBook, EnversNovel]))
        Map generatedWithVersion = columnsByTable(boot(true, [EnversBook, EnversNovel],
                ['hibernate.additionalProperties': ['org.hibernate.envers.do_not_audit_optimistic_locking_field': 'false']]))

        then: 'binder mode audits both, because Envers reads the getter of a property accessed property and the Groovy field is the one annotated, and the version has no @Version annotation to recognise'
        binder['envers_book_aud'].containsAll(['secret', 'version'])

        and: 'generated mode puts the annotation on the field Envers reads, and audits the version as binder mode does'
        !generated['envers_book_aud'].contains('secret')
        generated['envers_book_aud'].contains('version')

        and: 'Envers\' own setting, which a user can still set, gives the same'
        generatedWithVersion['envers_book_aud'].contains('version')
        !generatedWithVersion['envers_book_aud'].contains('secret')
    }

    def "a user value of the Envers optimistic locking setting wins over the generated mode default"() {
        when:
        Map generated = columnsByTable(boot(true, [EnversVersioned],
                ['hibernate.additionalProperties': ['org.hibernate.envers.do_not_audit_optimistic_locking_field': 'true']]))

        then:
        !generated['envers_versioned_aud'].contains('version')
        generated['envers_versioned_aud'].contains('name')
    }

    private static Map versionAudit(HibernateDatastore datastore, String table, String versionColumn) {
        Map result = [:]
        Sql sql = new Sql((javax.sql.DataSource) datastore.connectionSources.defaultConnectionSource.dataSource)
        try {
            result.rows = versionColumn == null ? null :
                    sql.rows("select revtype, $versionColumn as v from $table order by rev".toString())
                            .collect { [it.revtype, it.v] }
            result.columns = sql.rows("select column_name, data_type, is_nullable from information_schema.columns where lower(table_name) = ${table} order by column_name")
                    .collect { [it.column_name.toLowerCase(), it.data_type, it.is_nullable] }
        } finally {
            sql.close()
        }
        return result
    }

    private static void changeTwice(Class type, String field) {
        Long id
        type.withTransaction {
            id = type.newInstance(name: 'a').save(flush: true, failOnError: true).id
        }
        2.times { int i ->
            type.withTransaction {
                def found = type.get(id)
                found.name = "b${i}".toString()
                found.save(flush: true, failOnError: true)
            }
        }
        type.withTransaction {
            type.get(id).delete(flush: true)
        }
    }

    def "the audit table and its rows carry the optimistic locking version as in binder mode for #type.simpleName"() {
        when:
        HibernateDatastore binderDatastore = boot(false, [type])
        changeTwice(type, 'name')
        Map binder = versionAudit(binderDatastore, table, versionColumn)
        HibernateDatastore generatedDatastore = boot(true, [type])
        changeTwice(type, 'name')
        Map generated = versionAudit(generatedDatastore, table, versionColumn)

        then: 'the audit table has the same columns, types and nullability'
        binder.columns == generated.columns
        binder.columns*.getAt(0).contains(versionColumn) == hasVersion

        and: 'the version written at each revision is the same'
        binder.rows == generated.rows
        !hasVersion || binder.rows*.getAt(1).any { it != null }

        where:
        type                | table                    | versionColumn | hasVersion
        EnversVersioned     | 'envers_versioned_aud'   | 'version'     | true
        EnversUnversioned   | 'envers_unversioned_aud' | null          | false
        EnversCustomVersion | 'envers_custom_version_aud' | 'lock_no'  | true
    }

    private static Map shelfHistory() {
        Long shelfId
        EnversShelf.withTransaction {
            EnversShelf shelf = new EnversShelf(name: 'shelf')
            shelf.addToItems(new EnversItem(label: 'a'))
            shelf.addToItems(new EnversItem(label: 'b'))
            shelfId = shelf.save(flush: true, failOnError: true).id
        }
        EnversShelf.withTransaction {
            EnversShelf shelf = EnversShelf.get(shelfId)
            shelf.addToItems(new EnversItem(label: 'c'))
            shelf.save(flush: true, failOnError: true)
        }
        Map result = [:]
        EnversShelf.withNewSession { Session session ->
            AuditReader reader = AuditReaderFactory.get(session)
            List<Number> revisions = reader.getRevisions(EnversShelf, shelfId)
            result.revisionCount = revisions.size()
            result.itemLabels = revisions.collect { Number revision ->
                EnversShelf found = reader.find(EnversShelf, shelfId, revision)
                found.items*.label.sort()
            }
            result.itemClasses = reader.find(EnversShelf, shelfId, revisions.last()).items*.getClass()*.simpleName.toSet()
        }
        return result
    }

    def "a hasMany collection of audited entities is audited in generated mode"() {
        given:
        boot(true, [EnversShelf, EnversItem])

        when:
        Map history = shelfHistory()

        then:
        columnsByTable(datastore).keySet().containsAll(['envers_shelf_aud', 'envers_item_aud'])
        history.revisionCount == 2
        history.itemLabels == [['a', 'b'], ['a', 'b', 'c']]
        history.itemClasses == ['EnversItem'].toSet()
    }

    @PendingFeature(reason = 'binder mode: Envers builds the audit mapping of a hasMany collection before the domain binder has run its second passes and fails with a NullPointerException on the missing collection key')
    def "a hasMany collection of audited entities is audited in binder mode"() {
        given:
        boot(false, [EnversShelf, EnversItem])

        expect:
        shelfHistory().itemLabels == [['a', 'b'], ['a', 'b', 'c']]
    }
}

@Audited
@Entity
class EnversBook {
    String title
    Integer pages
    @NotAudited
    String secret
}

@Audited
@Entity
class EnversNovel extends EnversBook {
    String genre
}

@Audited
@Entity
class EnversShelf {
    String name
    static hasMany = [items: EnversItem]
}

@Audited
@Entity
class EnversItem {
    String label
    static belongsTo = [shelf: EnversShelf]
}

@Audited
@Entity
class EnversVersioned {
    String name
}

@Audited
@Entity
class EnversUnversioned {
    String name
    static mapping = {
        version false
    }
}

@Audited
@Entity
class EnversCustomVersion {
    String name
    static mapping = {
        version 'lock_no'
    }
}
