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
package grails.orm

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.FetchMode
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import spock.lang.Specification

class HibernateCriteriaBuilderDirectSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([DirectSpecPerson, DirectSpecPet])
    }

    def setup() {
        new DirectSpecPerson(name: "Fred", age: 40).save()
        new DirectSpecPerson(name: "Barney", age: 35).save()
        new DirectSpecPerson(name: "Wilma", age: 38).save(flush: true)
    }

    void "test exists and notExists closures"() {
        given:
        def builder = new HibernateCriteriaBuilder(DirectSpecPerson, manager.hibernateDatastore.sessionFactory, manager.hibernateDatastore)
        
        when:
        def results = builder.list {
            exists {
                projections { property("id") }
                eq("name", "Fred")
            }
        }
        
        then:
        results.size() == 3 // Every person exists if Fred exists

        when:
        results = builder.list {
            notExists {
                projections { property("id") }
                eq("name", "NoSuchPerson")
            }
        }
        
        then:
        results.size() == 3
    }

    void "test property comparison methods"() {
        given:
        new DirectSpecPerson(name: "Equal", age: 20, score: 20).save(flush: true)
        def builder = new HibernateCriteriaBuilder(DirectSpecPerson, manager.hibernateDatastore.sessionFactory, manager.hibernateDatastore)
        
        expect:
        builder.list { eqProperty("age", "score") }.size() == 1
        builder.list { neProperty("age", "score") }.size() == 3
        builder.list { gtProperty("age", "score") }.size() == 3
        builder.list { geProperty("age", "score") }.size() == 4
        builder.list { ltProperty("age", "score") }.size() == 0
        builder.list { leProperty("age", "score") }.size() == 1
    }

    void "test all and some subquery methods"() {
        given:
        def builder = new HibernateCriteriaBuilder(DirectSpecPerson, manager.hibernateDatastore.sessionFactory, manager.hibernateDatastore)
        
        when:
        def results = builder.list {
            gtAll("age", {
                projections { property("age") }
                eq("name", "Barney")
            })
        }
        
        then:
        results*.name.sort() == ["Fred", "Wilma"]

        when:
        results = builder.list {
            leSome("age", {
                projections { property("age") }
                eq("name", "Barney")
            })
        }
        
        then:
        results*.name.sort() == ["Barney"]
        
        when: "Testing other variants"
        results = builder.list {
            geSome("age", { projections { property("age") }; eq("name", "Fred") })
            ltSome("age", { projections { property("age") }; eq("name", "Fred") })
            eqAll("age", { projections { property("age") }; eq("name", "Fred") })
            ltAll("age", { projections { property("age") }; eq("name", "Fred") })
            leAll("age", { projections { property("age") }; eq("name", "Fred") })
        }
        
        then:
        noExceptionThrown()
    }

    void "test size comparison methods"() {
        given:
        def p = DirectSpecPerson.findByName("Fred")
        p.addToPets(name: "Dino")
        p.save(flush: true)
        def builder = new HibernateCriteriaBuilder(DirectSpecPerson, manager.hibernateDatastore.sessionFactory, manager.hibernateDatastore)
        
        expect:
        builder.list { sizeEq("pets", 1) }.size() == 1
        builder.list { sizeGt("pets", 0) }.size() == 1
        builder.list { sizeGe("pets", 1) }.size() == 1
        builder.list { sizeLe("pets", 0) }.size() == 2
        builder.list { sizeLt("pets", 1) }.size() == 2
        builder.list { sizeNe("pets", 0) }.size() == 1
    }

    void "test other criteria methods"() {
        given:
        def builder = new HibernateCriteriaBuilder(DirectSpecPerson, manager.hibernateDatastore.sessionFactory, manager.hibernateDatastore)
        
        expect:
        builder.list { lte("age", 35) }.size() == 1
        builder.list { gte("age", 40) }.size() == 1
        builder.list { idEquals(DirectSpecPerson.findByName("Fred").id) }.size() == 1
        builder.list { eq("name", "red", [ignoreCase: true]) }.size() == 1 // matches Fred via like %red%
    }
}

@Entity
class DirectSpecPerson {
    String name
    int age
    int score = 0
    Set<DirectSpecPet> pets
    static hasMany = [pets: DirectSpecPet]
}

@Entity
class DirectSpecPet {
    String name
    static belongsTo = [person: DirectSpecPerson]
}
