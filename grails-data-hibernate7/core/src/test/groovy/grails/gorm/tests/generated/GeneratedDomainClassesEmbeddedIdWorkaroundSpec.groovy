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
import java.sql.ResultSet
import java.sql.Statement
import java.util.concurrent.atomic.AtomicInteger

import grails.persistence.Entity
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider
import org.hibernate.engine.spi.SessionFactoryImplementor
import spock.lang.Specification

import org.grails.orm.hibernate.HibernateDatastore

/**
 * The supported way to move an embedded object that is a part of a composite identifier (<code>id composite: ['code',
 * 'address']</code> with <code>address</code> in <code>static embedded</code>) to the generated-domain-class binding, which
 * refuses it: the properties of the embedded class become plain properties of the identifier. The spec proves, through the
 * public datastore API, that the flattened entity keeps the schema of the embedded one in both bindings (tables, columns,
 * primary key columns and their order, foreign keys of a many-to-one and of a hasMany, as H2 states them), that rows the
 * embedded entity wrote stay readable, and that a transient accessor keeps <code>customer.address</code> compiling.
 */
class GeneratedDomainClassesEmbeddedIdWorkaroundSpec extends Specification {

    private static final AtomicInteger BOOTS = new AtomicInteger()

    private static final String CUSTOMER_PRIMARY_KEY =
            'ALTER TABLE "PUBLIC"."EID_CUSTOMER" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_#" PRIMARY KEY("ADDRESS_CITY", "ADDRESS_STREET", "CODE");'

    private static final List<Class> EMBEDDED = [EidEmbCustomer, EidEmbRegion]
    private static final List<Class> FLATTENED = [EidFlatCustomer, EidFlatRegion]
    private static final List<Class> WITH_ACCESSOR = [EidAccessorCustomer, EidAccessorRegion]

    private static Map<String, Object> config(String url, boolean generated, String dbCreate = 'create-drop') {
        return [
                'dataSource.url'                  : url,
                'dataSource.dbCreate'             : dbCreate,
                'hibernate.generatedDomainClasses': generated,
        ]
    }

    private static String freshUrl() {
        return "jdbc:h2:mem:eidWorkaround${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString()
    }

    /** The schema H2 holds after the boot, as its own script without data, in a stable order and without generated names. */
    private static List<String> schema(List<Class> classes, boolean generated) {
        HibernateDatastore datastore = new HibernateDatastore(config(freshUrl(), generated), classes as Class[])
        try {
            return script(datastore)
        } finally {
            datastore.close()
        }
    }

    private static List<String> script(HibernateDatastore datastore) {
        ConnectionProvider provider = ((SessionFactoryImplementor) datastore.sessionFactory).serviceRegistry.getService(ConnectionProvider)
        Connection connection = provider.connection
        try {
            Statement statement = connection.createStatement()
            ResultSet rows = statement.executeQuery('SCRIPT NODATA')
            List<String> statements = []
            while (rows.next()) {
                String line = rows.getString(1)
                if (!line.startsWith('--') && !line.startsWith('CREATE USER')) {
                    statements << line.replaceAll(/(?i)\b(CONSTRAINT|PRIMARY_KEY|CONSTRAINT_INDEX)_[0-9A-F]+\b/, '$1_#').trim()
                }
            }
            return statements.sort(false)
        } finally {
            provider.closeConnection(connection)
        }
    }

    private static boolean isPurchaseKey(String statement) {
        return statement.contains('"EID_PURCHASE"') && statement.contains('FOREIGN KEY')
    }

    private static String purchaseKey(List<String> script) {
        return script.find { String statement -> isPurchaseKey(statement) }
    }

    def "an embedded object as a part of the identifier is replaced by its properties, with the schema unchanged"() {
        given:
        List<String> embedded = schema(EMBEDDED, false)

        expect: 'the embedded entity creates the primary key over the columns of the embedded properties, in this order'
        embedded.contains(CUSTOMER_PRIMARY_KEY)
        embedded.any { it.contains('"ADDRESS_CITY" CHARACTER VARYING(255) NOT NULL') }

        and: 'the flattened entity creates every table, column, key and constraint the same'
        schema(FLATTENED, generated) == embedded

        where:
        generated << [false, true]
    }

    def "the generated-class binding refuses the embedded version and points at the flattened one"() {
        when:
        schema(EMBEDDED, true)

        then:
        RuntimeException failure = thrown()
        failure.toString().contains('Composite identifier part [address] of [grails.gorm.tests.generated.EidEmbCustomer] is an embedded object')
        failure.toString().contains('Map the properties of the embedded class as plain properties of the identifier')
        failure.toString().contains('Native Domain Binding chapter')
    }

    def "a transient accessor keeps the schema and adds no column"() {
        expect:
        schema(WITH_ACCESSOR, generated) == schema(EMBEDDED, false)

        where:
        generated << [false, true]
    }

    def "a many-to-one to the flattened entity references the identifier columns in the identifier order, in both bindings"() {
        given:
        List<String> classic = schema(FLATTENED + EidFlatPurchase, false)

        expect:
        schema(FLATTENED + EidFlatPurchase, true) == classic
        purchaseKey(classic) ==
                'ALTER TABLE "PUBLIC"."EID_PURCHASE" ADD CONSTRAINT "PUBLIC"."FKRBA3TSBPYXEJHWMBNOVA4F9VV" FOREIGN KEY' +
                '("EID_CUSTOMER_ADDRESS_CITY", "EID_CUSTOMER_ADDRESS_STREET", "EID_CUSTOMER_CODE") REFERENCES ' +
                '"PUBLIC"."EID_CUSTOMER"("ADDRESS_CITY", "ADDRESS_STREET", "CODE") NOCHECK;'
        classic.any { it.contains('"EID_CUSTOMER_ADDRESS_CITY" CHARACTER VARYING(255),') }
    }

    def "the domain binder cannot state the foreign key to an embedded identifier: it does not boot, and with the columns spelled out it pairs them wrongly"() {
        when:
        schema(EMBEDDED + EidEmbPurchase, false)

        then:
        RuntimeException failure = thrown()
        failure.toString().contains("maps to 2 columns but 3 columns are required")

        when:
        List<String> explicit = schema(EMBEDDED + EidEmbExplicitPurchase, false)

        then: 'the tables and columns are the flattened ones, the foreign key pairs street with street, but city with code and code with city'
        explicit.findAll { !isPurchaseKey(it) } == schema(FLATTENED + EidFlatPurchase, true).findAll { !isPurchaseKey(it) }
        purchaseKey(explicit).contains('("EID_CUSTOMER_ADDRESS_STREET", "EID_CUSTOMER_ADDRESS_CITY", "EID_CUSTOMER_CODE")')
        purchaseKey(explicit).contains('("ADDRESS_STREET", "CODE", "ADDRESS_CITY")')
    }

    def "a hasMany bound through a join table to a composite identifier boots in neither binding, flattened or not"() {
        when:
        schema([EidFlatCustomer, EidFlatRegion, EidFlatTeam], generated)

        then:
        RuntimeException failure = thrown()
        failure.toString().contains(message)

        where:
        generated | message
        false     | 'must have same number of columns as the referenced primary key'
        true      | 'the collection is bound through a join table'
    }

    private static void shutdown(String url) {
        Connection connection = java.sql.DriverManager.getConnection(url)
        try {
            connection.createStatement().execute('SHUTDOWN')
        } finally {
            connection.close()
        }
    }

    private static String existingDatabase() {
        return "jdbc:h2:mem:eidExisting${BOOTS.incrementAndGet()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000".toString()
    }

    private static void writeEmbedded(String url) {
        HibernateDatastore datastore = new HibernateDatastore(config(url, false, 'create'), EMBEDDED as Class[])
        try {
            EidEmbRegion.withTransaction {
                EidEmbRegion region = new EidEmbRegion(name: 'eu').save(failOnError: true)
                new EidEmbCustomer(code: 'c1', address: new EidAddress(street: 's1', city: 'x1'), name: 'one', region: region)
                        .save(failOnError: true)
                new EidEmbCustomer(code: 'c2', address: new EidAddress(street: 's2', city: 'x2'), name: 'two', region: region)
                        .save(failOnError: true, flush: true)
            }
        } finally {
            datastore.close()
        }
    }

    def "rows the embedded entity wrote stay readable and writable through the flattened entity, on the same database"() {
        given:
        String url = existingDatabase()
        writeEmbedded(url)
        HibernateDatastore datastore = new HibernateDatastore(config(url, generated, 'validate'), FLATTENED as Class[])
        Map found = [:]

        when:
        EidFlatCustomer.withTransaction {
            found.byKey = EidFlatCustomer.get(new EidFlatCustomer(code: 'c1', addressStreet: 's1', addressCity: 'x1'))?.name
            found.missing = EidFlatCustomer.get(new EidFlatCustomer(code: 'c1', addressStreet: 's2', addressCity: 'x1'))
            found.byFinder = EidFlatCustomer.findByAddressStreetAndAddressCity('s2', 'x2')?.name
            found.byWhere = EidFlatCustomer.where { addressCity == 'x1' }.list()*.code
            found.count = EidFlatCustomer.count()
            found.region = EidFlatRegion.list().first().customers*.name.sort()
            found.customerRegion = EidFlatCustomer.findByCode('c2').region.name
        }
        EidFlatCustomer.withTransaction {
            EidFlatCustomer one = EidFlatCustomer.get(new EidFlatCustomer(code: 'c1', addressStreet: 's1', addressCity: 'x1'))
            one.name = 'uno'
            one.save(failOnError: true)
            new EidFlatCustomer(code: 'c3', addressStreet: 's3', addressCity: 'x3', name: 'three', region: one.region)
                    .save(failOnError: true, flush: true)
        }
        EidFlatCustomer.withTransaction {
            found.updated = EidFlatCustomer.get(new EidFlatCustomer(code: 'c1', addressStreet: 's1', addressCity: 'x1')).name
            found.total = EidFlatCustomer.count()
            found.region3 = EidFlatRegion.list().first().customers*.code.sort()
        }
        datastore.close()
        HibernateDatastore back = new HibernateDatastore(config(url, false, 'validate'), EMBEDDED as Class[])
        EidEmbCustomer.withTransaction {
            found.back = EidEmbCustomer.get(new EidEmbCustomer(code: 'c3', address: new EidAddress(street: 's3', city: 'x3'))).name
            found.backCount = EidEmbCustomer.count()
        }

        then:
        found == [byKey: 'one', missing: null, byFinder: 'two', byWhere: ['c1'], count: 2, region: ['one', 'two'], customerRegion: 'eu',
                  updated: 'uno', total: 3, region3: ['c1', 'c2', 'c3'], back: 'three', backCount: 3]

        cleanup:
        back?.close()
        shutdown(url)

        where:
        generated << [false, true]
    }

    def "the transient accessor rebuilds the embedded object from the rows the embedded entity wrote, and is not persisted"() {
        given:
        String url = existingDatabase()
        writeEmbedded(url)
        HibernateDatastore datastore = new HibernateDatastore(config(url, generated, 'validate'), WITH_ACCESSOR as Class[])
        Map found = [:]

        when:
        EidAccessorCustomer.withTransaction {
            EidAccessorCustomer one = EidAccessorCustomer.get(new EidAccessorCustomer(address: new EidAddress(street: 's1', city: 'x1'), code: 'c1'))
            found.name = one.name
            found.address = [one.address.street, one.address.city]
            new EidAccessorCustomer(code: 'c9', address: new EidAddress(street: 'nine', city: 'xn'), name: 'nine').save(failOnError: true, flush: true)
        }
        EidAccessorCustomer.withTransaction {
            EidAccessorCustomer nine = EidAccessorCustomer.findByAddressStreet('nine')
            found.saved = [nine.addressStreet, nine.addressCity, nine.address.city]
            found.persistent = datastore.mappingContext.getPersistentEntity(EidAccessorCustomer.name).persistentProperties*.name.contains('address')
        }

        then:
        found == [name: 'one', address: ['s1', 'x1'], saved: ['nine', 'xn', 'xn'], persistent: false]

        cleanup:
        datastore?.close()
        shutdown(url)

        where:
        generated << [false, true]
    }
}

@Entity
class EidEmbExplicitPurchase {
    String reference
    EidEmbCustomer customer
    static mapping = {
        table 'eid_purchase'
        customer {
            column name: 'eid_customer_address_city'
            column name: 'eid_customer_address_street'
            column name: 'eid_customer_code'
        }
    }
}

class EidAddress implements Serializable {
    String street
    String city
}

@Entity
class EidEmbCustomer implements Serializable {
    String code
    EidAddress address
    String name
    EidEmbRegion region
    static embedded = ['address']
    static mapping = {
        table 'eid_customer'
        id composite: ['code', 'address']
    }
}

@Entity
class EidEmbPurchase {
    String reference
    EidEmbCustomer customer
    static mapping = {
        table 'eid_purchase'
    }
}

@Entity
class EidEmbRegion {
    String name
    static hasMany = [customers: EidEmbCustomer]
    static mapping = {
        table 'eid_region'
    }
}

@Entity
class EidEmbTeam {
    String name
    static hasMany = [members: EidEmbCustomer]
    static mapping = {
        table 'eid_team'
    }
}

@Entity
class EidFlatCustomer implements Serializable {
    String code
    String addressStreet
    String addressCity
    String name
    EidFlatRegion region
    static mapping = {
        table 'eid_customer'
        id composite: ['code', 'addressStreet', 'addressCity']
        addressStreet column: 'address_street'
        addressCity column: 'address_city'
    }
}

@Entity
class EidFlatPurchase {
    String reference
    EidFlatCustomer customer
    static mapping = {
        table 'eid_purchase'
    }
}

@Entity
class EidFlatRegion {
    String name
    static hasMany = [customers: EidFlatCustomer]
    static mapping = {
        table 'eid_region'
    }
}

@Entity
class EidFlatTeam {
    String name
    static hasMany = [members: EidFlatCustomer]
    static mapping = {
        table 'eid_team'
    }
}

@Entity
class EidAccessorCustomer implements Serializable {
    String code
    String addressStreet
    String addressCity
    String name
    EidAccessorRegion region
    static transients = ['address']
    static mapping = {
        table 'eid_customer'
        id composite: ['code', 'addressStreet', 'addressCity']
        addressStreet column: 'address_street'
        addressCity column: 'address_city'
    }

    EidAddress getAddress() {
        return new EidAddress(street: addressStreet, city: addressCity)
    }

    void setAddress(EidAddress address) {
        addressStreet = address?.street
        addressCity = address?.city
    }
}

@Entity
class EidAccessorPurchase {
    String reference
    EidAccessorCustomer customer
    static mapping = {
        table 'eid_purchase'
    }
}

@Entity
class EidAccessorRegion {
    String name
    static hasMany = [customers: EidAccessorCustomer]
    static mapping = {
        table 'eid_region'
    }
}

@Entity
class EidAccessorTeam {
    String name
    static hasMany = [members: EidAccessorCustomer]
    static mapping = {
        table 'eid_team'
    }
}
