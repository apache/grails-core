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
package grails.gorm.specs.hasmany

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.collection.PersistentCollection
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

class ListCollectionSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(getClass().getPackage())

    @Rollback
    void "test legs are not loaded eagerly"() {
        given:
        new Animal(name: "Chloe")
            .addToLegs(new Leg())
            .addToLegs(new Leg())
            .addToLegs(new Leg())
            .addToLegs(new Leg())
            .save(flush: true, failOnError: true)
        datastore.currentSession.flush()
        datastore.currentSession.clear()
        ProxyHandler ph = datastore.mappingContext.proxyHandler

        when:
        Animal animal = Animal.load(1)
        animal = ph.unwrap(animal)

        then:
        ph.isProxy(animal.legs) && !ph.isInitialized(animal.legs)
    }
}

@Entity
class Animal {
    String name

    List legs
    static hasMany = [legs: Leg]
}

@Entity
class Leg {

}