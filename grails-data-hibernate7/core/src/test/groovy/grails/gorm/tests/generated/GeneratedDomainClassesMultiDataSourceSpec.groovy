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
package grails.gorm.tests.generated

import grails.gorm.annotation.Entity
import grails.unbootable.CrossDataSourceRef
import org.hibernate.MappingException
import org.hibernate.Session
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Specification

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore

/**
 * Several data sources, each entity mapped to one of them (or to all): every data source has its own session factory and its
 * own generated classes, and the tables of each database are those the classic binding of Grails 8 created, stated here.
 */
class GeneratedDomainClassesMultiDataSourceSpec extends Specification {

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> classes) {
        datastore?.close()
        long stamp = System.nanoTime()
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                'dataSource.url'                 : "jdbc:h2:mem:gdcMdDefault${stamp};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'            : 'create-drop',
                'dataSource.dialect'             : H2Dialect.name,
                'dataSources.second.url'         : "jdbc:h2:mem:gdcMdSecond${stamp};LOCK_TIMEOUT=10000".toString(),
                'dataSources.second.dbCreate'    : 'create-drop',
                'hibernate.hbm2ddl.auto'         : 'create-drop',
                'hibernate.cache.queries'        : 'false',
                'hibernate.cache.use_query_cache': 'false',
        ]), classes as Class[])
        return datastore
    }

    private static Map<String, List<String>> tables(javax.sql.DataSource dataSource) {
        Map<String, List<String>> result = new TreeMap<String, List<String>>()
        dataSource.connection.withCloseable { java.sql.Connection connection ->
            connection.createStatement().withCloseable { java.sql.Statement statement ->
                statement.executeQuery('''select c.table_name, c.column_name, c.data_type, c.is_nullable,
                        (select count(*) from information_schema.table_constraints t
                          where t.table_name = c.table_name and t.constraint_type = 'FOREIGN KEY')
                        from information_schema.columns c where c.table_schema = 'PUBLIC'
                        order by c.table_name, c.column_name''').with { java.sql.ResultSet rs ->
                    while (rs.next()) {
                        result.computeIfAbsent(rs.getString(1)) { new ArrayList<String>() }
                                .add("${rs.getString(2)} ${rs.getString(3)} ${rs.getString(4)} fks=${rs.getInt(5)}".toString())
                    }
                }
            }
        }
        return result
    }

    private Map scenario() {
        HibernateDatastore booted = boot([GdcMdPrimary, GdcMdSecond, GdcMdSecondChild, GdcMdAll])
        Map result = [:]
        GdcMdPrimary.withTransaction { new GdcMdPrimary(name: 'primary').save(flush: true, failOnError: true) }
        GdcMdSecond.withTransaction {
            GdcMdSecond second = new GdcMdSecond(name: 'second')
            second.addToChildren(new GdcMdSecondChild(label: 'child'))
            second.save(flush: true, failOnError: true)
        }
        GdcMdAll.withTransaction { new GdcMdAll(name: 'in the default').save(flush: true, failOnError: true) }
        GdcMdAll.second.withTransaction { new GdcMdAll(name: 'in the second').save(flush: true, failOnError: true) }
        result.counts = [
                primary    : GdcMdPrimary.withNewSession { GdcMdPrimary.count() },
                second     : GdcMdSecond.withNewSession { GdcMdSecond.count() },
                child      : GdcMdSecond.withNewSession { GdcMdSecond.first().children*.label },
                allDefault : GdcMdAll.withNewSession { GdcMdAll.list()*.name },
                allSecond  : GdcMdAll.second.withNewSession { GdcMdAll.second.list()*.name },
        ]
        result.urls = [
                primary: GdcMdPrimary.withNewSession { Session s -> s.doReturningWork { it.metaData.getURL() } },
                second : GdcMdSecond.withNewSession { Session s -> s.doReturningWork { it.metaData.getURL() } },
        ]
        result.defaultTables = tables(booted.connectionSources.defaultConnectionSource.dataSource)
        result.secondTables = tables(booted.connectionSources.getConnectionSource('second').dataSource)
        result.mapped = [
                default: booted.getDatastoreForConnection('dataSource').sessionFactory.metamodel.entities*.javaType*.simpleName.sort(),
                second : booted.getDatastoreForConnection('second').sessionFactory.metamodel.entities*.javaType*.simpleName.sort(),
        ]
        return result
    }

    def "each entity lives in the data source it is mapped to, and the databases are built as classic binding built them"() {
        when:
        Map generated = scenario()

        then: 'the rows are in the right database, through the named data source api and through a lazy collection'
        generated.counts == [
                primary   : 1, second: 1, child: ['child'],
                allDefault: ['in the default'], allSecond: ['in the second'],
        ]

        and: 'each session factory maps only its entities, as real classes'
        generated.mapped == [
                default: ['GdcMdAll', 'GdcMdPrimary'],
                second : ['GdcMdAll', 'GdcMdSecond', 'GdcMdSecondChild'],
        ]

        and: 'the tables, columns and foreign keys of both databases are the ones classic binding created'
        generated.defaultTables == [
                GDC_MD_ALL    : ['ID BIGINT NO fks=0', 'NAME CHARACTER VARYING YES fks=0', 'VERSION BIGINT NO fks=0'],
                GDC_MD_PRIMARY: ['ID BIGINT NO fks=0', 'NAME CHARACTER VARYING YES fks=0', 'VERSION BIGINT NO fks=0'],
        ]
        generated.secondTables == [
                GDC_MD_ALL         : ['ID BIGINT NO fks=0', 'NAME CHARACTER VARYING YES fks=0', 'VERSION BIGINT NO fks=0'],
                GDC_MD_SECOND      : ['ID BIGINT NO fks=0', 'NAME CHARACTER VARYING YES fks=0', 'VERSION BIGINT NO fks=0'],
                GDC_MD_SECOND_CHILD: ['ID BIGINT NO fks=1', 'LABEL CHARACTER VARYING YES fks=1', 'PARENT_ID BIGINT YES fks=1', 'VERSION BIGINT NO fks=1'],
        ]

        and: 'each session talks to the database of its data source'
        generated.urls.primary.contains('gdcMdDefault')
        generated.urls.second.contains('gdcMdSecond')
    }

    def "a data source added at run time gets the generated classes of the entities mapped to all data sources"() {
        given:
        boot([GdcMdPrimary, GdcMdAll])

        when:
        datastore.connectionSources.addConnectionSource('late', [
                dbCreate: 'create-drop',
                url     : "jdbc:h2:mem:gdcMdLate${System.nanoTime()};LOCK_TIMEOUT=10000".toString(),
        ])
        GdcMdAll.late.withTransaction { new GdcMdAll(name: 'late').save(flush: true, failOnError: true) }

        then:
        GdcMdAll.late.withNewSession { GdcMdAll.late.list()*.name } == ['late']
        GdcMdAll.withNewSession { GdcMdAll.count() } == 0
        datastore.getDatastoreForConnection('late').sessionFactory.metamodel.entity(GdcMdAll).javaType == GdcMdAll
    }

    def "an association across data sources is refused by name, as a mapping exception"() {
        when:
        boot([CrossDataSourceRef, GdcMdSecond, GdcMdSecondChild])

        then:
        Exception e = thrown()
        Throwable root = e
        while (root.cause != null) {
            root = root.cause
        }
        root instanceof MappingException
        root.message.contains(GdcMdSecond.name)
        root.message.contains(CrossDataSourceRef.name)
        root.message.contains('data source')
    }
}

@Entity
class GdcMdPrimary {
    String name
}

@Entity
class GdcMdSecond {
    String name
    static hasMany = [children: GdcMdSecondChild]
    static mapping = {
        datasource 'second'
    }
}

@Entity
class GdcMdSecondChild {
    String label
    static belongsTo = [parent: GdcMdSecond]
    static mapping = {
        datasource 'second'
    }
}

@Entity
class GdcMdAll {
    String name
    static mapping = {
        datasource 'ALL'
    }
}
