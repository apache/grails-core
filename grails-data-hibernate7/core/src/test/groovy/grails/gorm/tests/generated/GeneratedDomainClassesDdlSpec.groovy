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

import java.sql.Connection

import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.hibernate.Session
import org.hibernate.mapping.RootClass

/**
 * The schema the generated-domain-class mode creates, as a user with an existing database sees it: the constraints, keys and
 * nullability Hibernate's annotation binder adds on its own are the ones the domain binder never created, so they must not
 * appear. {@code GeneratedDomainClassesDdlDifferentialSpec} compares the two modes over every scanned domain; the features here
 * pin each case against H2's own catalog.
 */
class GeneratedDomainClassesDdlSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(GddVehicle, GddCar, GddTruck, GddStudent, GddSchool, GddTeacher, GddBadge, GddToken, GddMember, GddAccount, GddPair, GddEdition, GddImprint, GddSequenced)
    }

    private List<String> checkClauses(String table) {
        Session session = sessionFactory.openSession()
        try {
            return session.doReturningWork { Connection connection ->
                List<String> clauses = []
                connection.createStatement().withCloseable { statement ->
                    statement.executeQuery(
                            'select cc.CHECK_CLAUSE from INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc ' +
                                    'join INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc on cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME ' +
                                    "and cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA where upper(tc.TABLE_NAME) = '${table.toUpperCase()}'".toString()
                    ).withCloseable { rows ->
                        while (rows.next()) {
                            clauses << rows.getString(1)
                        }
                    }
                }
                return clauses
            } as List<String>
        } finally {
            session.close()
        }
    }

    private List<String> query(String sql) {
        Session session = sessionFactory.openSession()
        try {
            return session.doReturningWork { Connection connection ->
                List<String> found = []
                connection.createStatement().withCloseable { statement ->
                    statement.executeQuery(sql).withCloseable { rows ->
                        while (rows.next()) {
                            found << rows.getString(1)
                        }
                    }
                }
                return found
            } as List<String>
        } finally {
            session.close()
        }
    }

    private List<String> constraintTypes(String table) {
        return query("select CONSTRAINT_TYPE from INFORMATION_SCHEMA.TABLE_CONSTRAINTS where upper(TABLE_NAME) = '${table.toUpperCase()}'".toString())
    }

    private List<String> nullableColumns(String table) {
        return query("select COLUMN_NAME from INFORMATION_SCHEMA.COLUMNS where upper(TABLE_NAME) = '${table.toUpperCase()}' and IS_NULLABLE = 'YES'".toString())
                *.toLowerCase()
    }

    void "the join table of a unidirectional one-to-many has a unique key over its nullable columns, not a primary key, as with the domain binder"() {
        given:
        String table = datastore.metadata.getCollectionBinding(GddTeacher.name + '.badges').collectionTable.name

        expect:
        'PRIMARY KEY' !in constraintTypes(table)
        constraintTypes(table).count('UNIQUE') == 1
        nullableColumns(table).size() == 2
    }

    void "the join table of a many-to-many has the primary key over its not null columns, as with the domain binder"() {
        expect:
        constraintTypes('gdd_student_schools').count('PRIMARY KEY') == 1
        nullableColumns('gdd_student_schools').isEmpty()
    }

    void "the key column of a collection of values stays nullable, as with the domain binder"() {
        expect:
        'gdd_student_id' in nullableColumns('gdd_student_nicknames')
    }

    void "a many-to-many still saves and loads through the join table"() {
        when:
        GddStudent student = new GddStudent(name: 's')
        student.addToSchools(new GddSchool(name: 'x'))
        student.nicknames = ['a', 'b'] as Set
        student.save(flush: true)
        session.clear()

        then:
        GddStudent.get(student.id).schools*.name == ['x']
        GddStudent.get(student.id).nicknames == ['a', 'b'] as Set
    }

    private List<String> uniqueConstraintNames(String table) {
        return query("select CONSTRAINT_NAME from INFORMATION_SCHEMA.TABLE_CONSTRAINTS where CONSTRAINT_TYPE = 'UNIQUE' and upper(TABLE_NAME) = '${table.toUpperCase()}'".toString())
    }

    private List<String> uniqueColumns(String table) {
        return query('select k.COLUMN_NAME from INFORMATION_SCHEMA.KEY_COLUMN_USAGE k join INFORMATION_SCHEMA.TABLE_CONSTRAINTS t ' +
                'on k.CONSTRAINT_NAME = t.CONSTRAINT_NAME and k.CONSTRAINT_SCHEMA = t.CONSTRAINT_SCHEMA ' +
                "where t.CONSTRAINT_TYPE = 'UNIQUE' and upper(t.TABLE_NAME) = '${table.toUpperCase()}' order by k.ORDINAL_POSITION".toString())*.toLowerCase()
    }

    void "a unique group is an unnamed constraint over its columns, as with the domain binder, not one named by Hibernate"() {
        expect:
        uniqueConstraintNames('gdd_member').size() == 1
        !uniqueConstraintNames('gdd_member')[0].toUpperCase().startsWith('UK')
        uniqueColumns('gdd_member').toSet() == ['org', 'login'].toSet()
    }

    void "the unique key of a natural id takes its columns in the order the mapping names the properties, and has no name of its own"() {
        expect:
        uniqueColumns('gdd_account') == ['zeta', 'alpha']
        !uniqueConstraintNames('gdd_account')[0].toUpperCase().startsWith('UK')
    }

    private List<String> importedKeys(String table) {
        Session session = sessionFactory.openSession()
        try {
            return session.doReturningWork { Connection connection ->
                List<String> found = []
                connection.metaData.getImportedKeys(null, null, table.toUpperCase()).withCloseable { rows ->
                    while (rows.next()) {
                        found << "${rows.getString('FKCOLUMN_NAME')}->${rows.getString('PKCOLUMN_NAME')}".toString().toLowerCase()
                    }
                }
                return found
            } as List<String>
        } finally {
            session.close()
        }
    }

    void "the primary key of a composite identifier takes the order of the unique group over its columns, as with the domain binder"() {
        expect: "the group names world first, where Hibernate would order two columns of one size by name"
        query("select k.COLUMN_NAME from INFORMATION_SCHEMA.KEY_COLUMN_USAGE k join INFORMATION_SCHEMA.TABLE_CONSTRAINTS t " +
                "on k.CONSTRAINT_NAME = t.CONSTRAINT_NAME and k.CONSTRAINT_SCHEMA = t.CONSTRAINT_SCHEMA " +
                "where t.CONSTRAINT_TYPE = 'PRIMARY KEY' and upper(t.TABLE_NAME) = 'GDD_PAIR' order by k.ORDINAL_POSITION")*.toLowerCase() == ['world', 'hello']
        uniqueConstraintNames('gdd_pair').isEmpty()
    }

    void "a foreign key to a composite identifier names the referenced columns in the order of the sorted parts, as with the domain binder"() {
        expect: "the primary key of the edition is ordered by size (number first), the foreign key follows the parts by name"
        importedKeys('gdd_imprint') == ['gdd_edition_isbn->isbn', 'gdd_edition_number->number']
    }

    void "a foreign key to a composite identifier still saves and loads"() {
        when:
        GddEdition edition = new GddEdition(isbn: 'x', number: 2, label: 'l').save(flush: true)
        new GddImprint(edition: edition, code: 'c').save(flush: true)
        session.clear()

        then:
        GddImprint.list().size() == 1
        GddImprint.list()[0].edition.label == 'l'
    }

    void "the table of a table id generator has its columns in the order Hibernate gives a table it knows when it orders the columns, as with the domain binder"() {
        expect: "the generator's own order is sequence_name, next_val; by size the integer comes first"
        query("select COLUMN_NAME from INFORMATION_SCHEMA.COLUMNS where upper(TABLE_NAME) = 'GDD_IDS' order by ORDINAL_POSITION")*.toLowerCase() == ['next_val', 'sequence_name']
    }

    void "an entity with a table id generator still gets its ids"() {
        when:
        GddSequenced first = new GddSequenced(name: 'a').save(flush: true)
        GddSequenced second = new GddSequenced(name: 'b').save(flush: true)

        then:
        first.id != null
        second.id != null
        first.id != second.id
    }

    void "an identifier mapped with type uuid-binary is a binary column, not the database's uuid type, as with the domain binder"() {
        when:
        GddToken token = new GddToken(name: 't').save(flush: true)
        session.clear()

        then:
        query("select DATA_TYPE from INFORMATION_SCHEMA.COLUMNS where upper(TABLE_NAME) = 'GDD_TOKEN' and upper(COLUMN_NAME) = 'ID'") == ['BINARY']
        GddToken.get(token.id).name == 't'
    }

    void "a single-table hierarchy gets no check constraint over its discriminator values, as with the domain binder"() {
        expect:
        checkClauses('gdd_vehicle').isEmpty()
    }

    void "a primitive property of a single-table subclass adds no not-null check to the table, as with the domain binder"() {
        expect:
        checkClauses('gdd_vehicle').isEmpty()
        datastore.metadata.getEntityBinding(GddCar.name).getProperty('doors').columns[0].nullable
        datastore.metadata.getEntityBinding(GddVehicle.name).getProperty('wheels').columns[0].nullable
    }

    void "the discriminator of the root is not forced, so root queries do not filter on it"() {
        given:
        RootClass root = (RootClass) datastore.metadata.getEntityBinding(GddVehicle.name)

        expect:
        !root.forceDiscriminator
        root.discriminator != null
    }

    void "the instances of the hierarchy still save, load polymorphically and keep their discriminator"() {
        when:
        new GddCar(name: 'c', doors: 4, sporty: true).save(flush: true)
        new GddTruck(name: 't', axles: 3).save(flush: true)
        session.clear()

        then:
        GddVehicle.list()*.getClass().toSet() == [GddCar, GddTruck].toSet()
        GddVehicle.count() == 2
    }
}

@Entity
class GddVehicle {
    String name
    int wheels
}

@Entity
class GddCar extends GddVehicle {
    int doors
    boolean sporty
}

@Entity
class GddTruck extends GddVehicle {
    Integer axles
}

@Entity
class GddStudent {
    String name
    Set<String> nicknames
    static hasMany = [schools: GddSchool, nicknames: String]
}

@Entity
class GddSchool {
    String name
    static hasMany = [students: GddStudent]
    static belongsTo = GddStudent
}

@Entity
class GddTeacher {
    String name
    static hasMany = [badges: GddBadge]
}

@Entity
class GddBadge {
    String name
}

@Entity
class GddToken {
    UUID id
    String name
    static mapping = {
        id generator: 'uuid2', type: 'uuid-binary'
    }
}

@Entity
class GddMember {
    String org
    String login
    static constraints = {
        login unique: 'org'
    }
}

@Entity
class GddAccount {
    String zeta
    String alpha
    static mapping = {
        id natural: ['zeta', 'alpha']
    }
}

@Entity
class GddPair implements Serializable {
    Long hello
    Long world
    static constraints = {
        hello unique: 'world'
    }
    static mapping = {
        version false
        id composite: ['hello', 'world']
    }
}

@Entity
class GddEdition implements Serializable {
    String isbn
    Integer number
    String label
    static mapping = {
        id composite: ['isbn', 'number']
    }
}

@Entity
class GddImprint implements Serializable {
    GddEdition edition
    String code
    static mapping = {
        id composite: ['edition', 'code']
    }
}

@Entity
class GddSequenced {
    String name
    static mapping = {
        id generator: 'table', params: [table_name: 'gdd_ids', segment_value: 'gdd_table']
    }
}
