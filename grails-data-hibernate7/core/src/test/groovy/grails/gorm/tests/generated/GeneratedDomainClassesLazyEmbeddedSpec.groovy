/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package grails.gorm.tests.generated

import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.hibernate.mapping.PersistentClass

/**
 * An embedded property mapped {@code lazy: true} is bound as a lazy attribute, as the domain binder binds it. Hibernate's
 * annotation binder ignores {@code @Basic(fetch = LAZY)} on an {@code @Embedded}, so the generated-class binding sets the flag
 * on the bound property; the spec reads it from the metadata of a real boot and checks the data still round-trips.
 */
class GeneratedDomainClassesLazyEmbeddedSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        registerGeneratedDomainClasses(GdcLazyHome, GdcPlainHome)
    }

    def "an embedded property mapped lazy is a lazy property of the bound entity, one mapped by default is not"() {
        when:
        PersistentClass lazyHome = datastore.metadata.getEntityBinding(GdcLazyHome.name)
        PersistentClass plainHome = datastore.metadata.getEntityBinding(GdcPlainHome.name)

        then:
        lazyHome.getProperty('address').lazy
        !plainHome.getProperty('address').lazy
    }

    def "the embedded values of a lazy embedded property round-trip"() {
        given:
        new GdcLazyHome(name: 'h', address: new GdcAddress(street: 's', city: 'c')).save(flush: true)
        sessionFactory.currentSession.clear()

        when:
        GdcLazyHome loaded = GdcLazyHome.findByName('h')

        then:
        loaded.address.street == 's'
        loaded.address.city == 'c'
    }
}

@Entity
class GdcLazyHome {

    String name
    GdcAddress address

    static embedded = ['address']

    static mapping = {
        address lazy: true
    }
}

@Entity
class GdcPlainHome {

    String name
    GdcAddress address

    static embedded = ['address']
}

class GdcAddress {

    String street
    String city
}
