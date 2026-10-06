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
        registerGeneratedDomainClasses(GddVehicle, GddCar, GddTruck)
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
