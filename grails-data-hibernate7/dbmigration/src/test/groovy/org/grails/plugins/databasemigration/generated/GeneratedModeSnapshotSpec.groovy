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

import grails.gorm.annotation.Entity
import liquibase.CatalogAndSchema
import liquibase.Liquibase
import liquibase.database.Database
import liquibase.database.core.H2Database
import liquibase.database.jvm.JdbcConnection
import liquibase.diff.DiffResult
import liquibase.diff.compare.CompareControl
import liquibase.diff.output.DiffOutputControl
import liquibase.diff.output.changelog.DiffToChangeLog
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.serializer.core.xml.XMLChangeLogSerializer
import liquibase.snapshot.DatabaseSnapshot
import liquibase.snapshot.SnapshotControl
import liquibase.snapshot.SnapshotGeneratorFactory
import liquibase.structure.DatabaseObject
import liquibase.structure.core.Column
import liquibase.structure.core.ForeignKey
import liquibase.structure.core.Index
import liquibase.structure.core.PrimaryKey
import liquibase.structure.core.Sequence
import liquibase.structure.core.Table
import liquibase.structure.core.UniqueConstraint
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Specification

import org.grails.orm.hibernate.HibernateDatastore
import org.grails.plugins.databasemigration.liquibase.GormDatabase

/**
 * The Liquibase snapshot and the changelog that dbm-gorm-diff and dbm-generate-gorm-changelog build from the Hibernate
 * mapping model are the same whether the domain binder or the generated domain classes bound it: tables, columns and
 * their types, primary keys, foreign keys, unique constraints, indexes and sequences. The differences that remain are
 * the known ones of the generated mode, each pinned by its own feature.
 */
class GeneratedModeSnapshotSpec extends Specification {

    private static final List<Class> BASE = [
            SnapAuthor, SnapBook, SnapVehicle, SnapCar, SnapAccount, SnapSavings, SnapShape, SnapCircle, SnapSequenced, SnapPlain]

    private static final List<Class> ALL = BASE + [SnapStudent, SnapCourse, SnapEnrollment, SnapNatural]

    @AutoCleanup
    HibernateDatastore datastore

    private GormDatabase boot(boolean generated, List<Class> classes) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'                  : "jdbc:h2:mem:snapSpec${generated}${System.nanoTime()};LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1".toString(),
                'dataSource.dialect'              : H2Dialect.name,
                'dataSource.dbCreate'             : 'none',
                'hibernate.generatedDomainClasses': generated,
        ], classes as Class[])
        return new GormDatabase(new H2Dialect(), datastore)
    }

    private static List<String> snapshotLines(GormDatabase database) {
        DatabaseSnapshot snapshot = SnapshotGeneratorFactory.instance.createSnapshot(
                new CatalogAndSchema(null, null), database,
                new SnapshotControl(database, Table, Column, PrimaryKey, ForeignKey, UniqueConstraint, Index, Sequence))
        List<String> lines = []
        [Table, Column, PrimaryKey, ForeignKey, UniqueConstraint, Index, Sequence].each { Class<? extends DatabaseObject> type ->
            for (DatabaseObject object : snapshot.get(type)) {
                Map<String, String> attributes = new TreeMap<String, String>()
                for (String name : object.attributes) {
                    if (name != 'snapshotId' && name != 'schema' && name != 'name') {
                        Object value = object.getAttribute(name, Object)
                        attributes[name] = value instanceof Collection ?
                                value.collect { String.valueOf(it) }.sort().toString() : String.valueOf(value)
                    }
                }
                // Liquibase names the index behind a constraint with four random letters
                lines << "${type.simpleName} ${object.name} ${attributes}".toString().replaceAll(/_[A-Z]{4}_IX/, '_XXXX_IX')
            }
        }
        return lines.sort()
    }

    /**
     * The known differences that the features below pin are hidden: the order of the columns of a primary key and the
     * name Hibernate gives the unique key of a natural id.
     */
    private static List<String> withoutKnownDifferences(List<String> lines) {
        return lines.collect { String line ->
            line.replaceAll(/(?i)(on [\w.]+)\(([^)]*)\)/) { List<String> match ->
                match[1] + '(' + match[2].split(', ').toList().sort().join(', ') + ')'
            }.replaceAll(/UK[0-9a-z]{20,}/, 'UK<hash>')
        }.sort()
    }

    private static String changeLog(GormDatabase reference) {
        Class.forName('org.h2.Driver')
        java.sql.Connection connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:snapTarget${System.nanoTime()}", 'SA', '')
        Database target = new H2Database()
        target.setConnection(new JdbcConnection(connection))
        try {
            Set<Class<? extends DatabaseObject>> types = [Table, Column, PrimaryKey, ForeignKey, UniqueConstraint, Index, Sequence] as Set
            DiffResult diff = new Liquibase((String) null, new ClassLoaderResourceAccessor(), target)
                    .diff(reference, target, new CompareControl(types))
            ByteArrayOutputStream out = new ByteArrayOutputStream()
            new DiffToChangeLog(diff, new DiffOutputControl(false, false, false, null))
                    .print(new PrintStream(out), new XMLChangeLogSerializer())
            return out.toString('UTF-8')
        } finally {
            target.close()
        }
    }

    /**
     * Each change set without its generated id, its lines in sorted order (the order of the columns of a table is a known
     * difference), and the change sets sorted.
     */
    private static List<String> canonicalChangeLog(String xml) {
        String stripped = xml.replaceAll(/ id="[^"]*"/, '').replaceAll(/_[A-Z]{4}_IX/, '_XXXX_IX')
        return stripped.split(/<changeSet/).drop(1).collect { String block ->
            block.readLines()*.trim().findAll { it }.sort().join(' | ')
        }.sort()
    }

    def "the Liquibase snapshot of the generated mapping equals the snapshot of the binder mapping"() {
        when:
        List<String> binder = snapshotLines(boot(false, ALL))
        List<String> generated = snapshotLines(boot(true, ALL))

        then: 'the snapshot has every kind of object'
        ['Table', 'Column', 'PrimaryKey', 'ForeignKey', 'UniqueConstraint', 'Index', 'Sequence'].every { String type ->
            binder.any { it.startsWith(type + ' ') }
        }
        binder.size() > 100

        and: 'nothing differs besides the known differences'
        withoutKnownDifferences(binder) - withoutKnownDifferences(generated) == []
        withoutKnownDifferences(generated) - withoutKnownDifferences(binder) == []
    }

    def "the primary key of a composite identifier lists its columns in the order Hibernate sorts them, and the natural id key has Hibernate's own name"() {
        when:
        List<String> binder = snapshotLines(boot(false, ALL))
        List<String> generated = snapshotLines(boot(true, ALL))
        Closure<String> primaryKey = { List<String> lines -> lines.find { it.startsWith('PrimaryKey snap_enrollmentPK') } }
        Closure<String> naturalKey = { List<String> lines ->
            (lines.find { it.startsWith('UniqueConstraint UK') && it.contains('snap_natural') } =~ /UniqueConstraint (UK\w+)/)[0][1]
        }

        then: 'the binder keeps the order of the mapping, Hibernate sorts the parts of the identifier'
        primaryKey(binder).contains('ON HIBERNATE.snap_enrollment(student, course)')
        primaryKey(generated).contains('ON HIBERNATE.snap_enrollment(course, student)')

        and: 'both name the natural id key from its table and columns'
        naturalKey(binder) != naturalKey(generated)
    }

    def "the changelog generated from the generated mapping equals the one generated from the binder mapping"() {
        when:
        List<String> binder = canonicalChangeLog(changeLog(boot(false, BASE)))
        List<String> generated = canonicalChangeLog(changeLog(boot(true, BASE)))

        then:
        binder.size() > 15
        binder.any { it.contains('<createSequence') }
        binder.any { it.contains('autoIncrement="true"') }
        binder.any { it.contains('<addForeignKeyConstraint') }
        binder - generated == []
        generated - binder == []
    }
}

enum SnapStatus { OPEN, CLOSED }

class SnapAddress {
    String street
    String city
}

@Entity
class SnapAuthor {
    String name
    SnapStatus status
    BigDecimal balance
    SnapAddress home
    static embedded = ['home']
    static hasMany = [books: SnapBook]
    static constraints = {
        name maxSize: 50, nullable: false
        balance scale: 2, max: 9999999999.99G
    }
    static mapping = {
        name index: 'snap_author_name_idx'
        status enumType: 'string'
    }
}

@Entity
class SnapBook {
    String title
    String isbn
    static belongsTo = [author: SnapAuthor]
    static hasMany = [tags: String]
    static constraints = {
        isbn unique: true, nullable: true
    }
}

@Entity
class SnapStudent {
    String name
    static hasMany = [courses: SnapCourse]
}

@Entity
class SnapCourse {
    String title
    static belongsTo = SnapStudent
    static hasMany = [students: SnapStudent]
}

@Entity
class SnapVehicle {
    String plate
}

@Entity
class SnapCar extends SnapVehicle {
    Integer doors
}

@Entity
class SnapAccount {
    String owner
    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
class SnapSavings extends SnapAccount {
    BigDecimal rate
}

@Entity
class SnapShape {
    String color
    static mapping = {
        tablePerHierarchy false
        tablePerConcreteClass true
        id generator: 'sequence', params: [sequence_name: 'snap_shape_seq']
    }
}

@Entity
class SnapCircle extends SnapShape {
    Integer radius
}

@Entity
class SnapEnrollment implements Serializable {
    String student
    String course
    Integer grade
    static mapping = {
        id composite: ['student', 'course']
    }
}

@Entity
class SnapSequenced {
    String label
    static mapping = {
        id generator: 'sequence', params: [sequence_name: 'snap_seq']
    }
}

@Entity
class SnapNatural {
    String code
    String description
    static mapping = {
        id natural: 'code'
        version false
    }
}

@Entity
class SnapPlain {
    String first
    String last
    String full
    static mapping = {
        full formula: "first || ' ' || last"
        version false
    }
}
