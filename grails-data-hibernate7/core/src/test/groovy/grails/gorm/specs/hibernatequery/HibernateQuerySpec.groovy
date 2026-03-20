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

package grails.gorm.specs.hibernatequery

import grails.gorm.DetachedCriteria
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.apache.grails.data.testing.tck.domains.CommonTypes
import org.apache.grails.data.testing.tck.domains.EagerOwner
import org.apache.grails.data.testing.tck.domains.Face
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.domains.Pet
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.HibernateSession
import org.grails.orm.hibernate.query.HibernateQuery
import jakarta.persistence.criteria.JoinType
import java.io.Serializable

class HibernateQuerySpec extends HibernateGormDatastoreSpec {

    Person oldBob
    HibernateQuery hibernateQuery
    HibernateQuery eagerHibernateQuery

    def setup() {
        oldBob = new Person(firstName: "Bob", lastName: "Builder", age: 50).save(flush: true)
        hibernateQuery = new HibernateQuery(session, getPersistentEntity(Person))
        eagerHibernateQuery = new HibernateQuery(session, getPersistentEntity(EagerOwner))
    }

    def setupSpec() {
        manager.addAllDomainClasses([Person, Pet, Face, EagerOwner, CommonTypes, HibernateQuerySpecBigDecimalEntity])
    }

    def equals() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 51).save(flush: true)
        hibernateQuery.eq("firstName", "Bob")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def equalsJoins() {
        given:
        oldBob.addToPets(new Pet(name: "Lucky")).save(flush: true)
        hibernateQuery.join("pets").eq("pets.name", "Lucky")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def ne() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 51).save(flush: true)
        hibernateQuery.ne("firstName", "Fred")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def eqProperty() {
        given:
        def oldMajor = new Person(firstName: "Major", lastName: "Major", age: 50).save(flush: true)
        hibernateQuery.eqProperty("firstName", "lastName")
        when:
        def newMajor = hibernateQuery.singleResult()
        then:
        oldMajor == newMajor
    }

    def neProperty() {
        given:
        hibernateQuery.neProperty("firstName", "lastName")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def leProperty() {
        given:
        def oldEager = new EagerOwner(column1: 1, column2: 2).save(flush: true)
        eagerHibernateQuery.leProperty("column1", "column2")
        when:
        def newEager = eagerHibernateQuery.singleResult()
        then:
        oldEager == newEager
    }

    def ltProperty() {
        given:
        def oldEager = new EagerOwner(column1: 1, column2: 2).save(flush: true)
        eagerHibernateQuery.ltProperty("column1", "column2")
        when:
        def newEager = eagerHibernateQuery.singleResult()
        then:
        oldEager == newEager
    }

    def geProperty() {
        given:
        def oldEager = new EagerOwner(column1: 2, column2: 1).save(flush: true)
        eagerHibernateQuery.geProperty("column1", "column2")
        when:
        def newEager = eagerHibernateQuery.singleResult()
        then:
        oldEager == newEager
    }

    def gtProperty() {
        given:
        def oldEager = new EagerOwner(column1: 2, column2: 1).save(flush: true)
        eagerHibernateQuery.gtProperty("column1", "column2")
        when:
        def newEager = eagerHibernateQuery.singleResult()
        then:
        oldEager == newEager
    }


//    @Ignore("Need better implementation of Predicate")
    def idEq() {
        given:
        Person oldFred = new Person(firstName: "Fred", lastName: "Rogers", age: 51).save(flush: true)
        hibernateQuery.idEq(oldFred.id)
        when:
        def newFred = hibernateQuery.singleResult()
        then:
        oldFred == newFred
    }

    def gt() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        hibernateQuery.gt("age", 49)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def ge() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        hibernateQuery.ge("age", 50)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def le() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        hibernateQuery.le("age", 50)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def lt() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        hibernateQuery.lt("age", 51)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }


    def like() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        hibernateQuery.like("firstName", "Bo%")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }


    def ilike() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        hibernateQuery.ilike("firstName", "BO%")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def rlike() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        hibernateQuery.rlike("firstName", "Bob.*")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def and() {
        given:
        new Person(firstName: "Bob", lastName: "Builder", age: 51).save(flush: true)
        Query.Criterion lastName = new Query.Equals("lastName", "Builder")
        Query.Criterion age = new Query.Equals("age", 50)
        hibernateQuery.and(lastName, age)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def or() {
        given:
        new Person(firstName: "Bob", lastName: "Builder", age: 51).save(flush: true)
        def lastNameWrong = new Query.Equals("lastName", "Rogers")
        def  ageCorrect = new Query.Equals("age", 50)

        hibernateQuery.or(lastNameWrong, ageCorrect)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def not() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 51).save(flush: true)
        Query.Criterion lastNameWrong = new Query.Equals("lastName", "Rogers")
        Query.Criterion firstNameWrong = new Query.Equals("firstName", "Fred")
        hibernateQuery.not([lastNameWrong,firstNameWrong])
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def isEmpty() {
        given:
        hibernateQuery.isEmpty("pets")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def isNotEmpty() {
        Pet pet = new Pet(name: "Lucky")
        oldBob.addToPets(pet)
        oldBob.save(flush: true)
        given:
        hibernateQuery.isNotEmpty("pets")

        when:
        Person newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
        oldBob.pets == newBob.pets
    }

    def isNull() {
        given:
        hibernateQuery.isNull("face")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def isNotNull() {
        new Person(firstName: "Fred", age: 52).save(flush: true)
        given:
        hibernateQuery.isNotNull("lastName")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def allEq() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        given:
        hibernateQuery.allEq(["firstName": "Bob", "lastName": "Builder"])
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def inSubQuery() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        hibernateQuery.in("firstName",
            new DetachedCriteria(Person)
                    .eq("lastName", "Builder")
                    .property("firstName")
        )
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def notInSubQuery() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        hibernateQuery.notIn("firstName",
                new DetachedCriteria(Person)
                        .eq("lastName", "Rogers")
                        .property("firstName")
        )
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def exists() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        new Pet(name: "Lucky", owner: oldBob).save(flush:true)
        hibernateQuery.exists(
                new DetachedCriteria(Pet)
        )

        when:
        def list = hibernateQuery.list()
        then:
        list.size() == 1
        oldBob == list.get(0)
    }


    def notExists() {
        given:
        def newBob = new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        new Pet(name: "Lucky", owner: newBob).save(flush:true)
        hibernateQuery.notExits(new DetachedCriteria(Pet))
        when:
        def result = hibernateQuery.singleResult()
        then:
        oldBob == result
    }

    def greaterThanAll() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        new Pet(name: "Lucky", age: 1, owner: oldBob).save(flush:true)

        def property = new DetachedCriteria(Pet)
                .eq("age", 1)
                .eq("name", "Lucky")
                .property("age")
        given:
        hibernateQuery.gtAll("age", property)
        when:
        def bobs = hibernateQuery.list()
        then:
        bobs.size() == 2
    }


    def lessThanEqualsAll() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        new Pet(name: "Lucky", age: 52, owner: oldBob).save(flush:true)
        given:
        hibernateQuery.leAll("age", new DetachedCriteria(Pet)
                .eq("age", 52)
                .eq("name", "Lucky")
                .property("age")
        )
        when:
        def bobs = hibernateQuery.list()
        then:
        bobs.size() == 2
    }

    def lessThanAll() {
        new Person(firstName: "Fred", lastName: "Builder", age: 52).save(flush: true)
        new Pet(name: "Lucky", age: 100, owner: oldBob).save(flush:true)
        given:
        hibernateQuery.ltAll("age",  new DetachedCriteria(Pet)
                .eq("age", 100)
                .eq("name", "Lucky")
                .property("age")
        )
        when:
        def bobs = hibernateQuery.list()
        then:
        bobs.size() == 2
    }


    def greaterThanEqualsAll() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        new Pet(name: "Lucky", age: 48, owner: oldBob).save(flush:true)
        given:
        hibernateQuery.geAll("age", new DetachedCriteria(Pet)
                .eq("age", 48)
                .eq("name", "Lucky")
                .property("age")
        )
        when:
        def bobs = hibernateQuery.list()
        then:
        bobs.size() == 2
    }

    def greaterThanSome() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        new Pet(name: "Lucky", age: 1, owner: oldBob).save(flush:true)
        given:
        hibernateQuery.gtSome("age", new DetachedCriteria(Pet)
                .eq("age", 1)
                .eq("name", "Pluto")
                .property("age")
        )
        when:
        def bobs = hibernateQuery.list()
        then:
        bobs.size() == 2
    }



    def lessThanEqualsSome() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        new Pet(name: "Lucky", age: 52, owner: oldBob).save(flush:true)
        given:
        hibernateQuery.leSome("age", new DetachedCriteria(Pet)
                .eq("age", 52)
                .eq("name", "Pluto")
                .property("age")
        )
        when:
        def bobs = hibernateQuery.list()
        then:
        bobs.size() == 2
    }

    def lessThanSome() {
        new Person(firstName: "Fred", lastName: "Builder", age: 52).save(flush: true)
        new Pet(name: "Lucky", age: 100, owner: oldBob).save(flush:true)
        given:
        hibernateQuery.ltSome( "age", new DetachedCriteria(Pet)
                .eq("age", 100)
                .eq("name", "Pluto")
                .property("age")
        )
        when:
        def bobs = hibernateQuery.list()
        then:
        bobs.size() == 2
    }


    def greaterThanEqualsSome() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        new Pet(name: "Lucky", age: 48, owner: oldBob).save(flush:true)
        given:
        hibernateQuery.geSome("age", new DetachedCriteria(Pet)
                .eq("age", 48)
                .eq("name", "Pluto")
                .property("age")
        )
        when:
        def bobs = hibernateQuery.list()
        then:
        bobs.size() == 2
    }

    def equalsAll() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        new Pet(name: "Lucky", age: 50, owner: oldBob).save(flush:true)
        given:
        hibernateQuery.eqAll( "age", new DetachedCriteria(Pet)
                .eq("age", 50)
                .eq("name", "Lucky")
                .property("age")
        )
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }



    def inList() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        given:
        hibernateQuery.in("age", [50, 51])
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def between() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        given:
        hibernateQuery.between("age", 49, 51)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def betweenBigDecimal() {
        given:
        HibernateDatastore hibernateDatastore = manager.hibernateDatastore
        HibernateSession session = hibernateDatastore.connect() as HibernateSession
        HibernateQuery query = new HibernateQuery(session, hibernateDatastore.getMappingContext().getPersistentEntity(HibernateQuerySpecBigDecimalEntity.typeName))
        new HibernateQuerySpecBigDecimalEntity(amount: 10.5G).save(flush: true, failOnError: true)
        new HibernateQuerySpecBigDecimalEntity(amount: 20.5G).save(flush: true, failOnError: true)
        new HibernateQuerySpecBigDecimalEntity(amount: 30.5G).save(flush: true, failOnError: true)

        query.between("amount", 15.0G, 25.0G)

        when:
        def results = query.list()

        then:
        results.size() == 1
        results[0].amount == 20.5G
    }

    def inListArray() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        given:
        hibernateQuery.in("age", [50, 52])
        when:
        def results = hibernateQuery.list()
        then:
        results.size() == 2
        results*.firstName.sort() == ["Bob", "Fred"]
    }

    def countDistinct() {
        new Person(firstName: "Bob", lastName: "The Builder", age: 25).save(flush: true)
        given:
        hibernateQuery.projections().countDistinct("firstName")
        when:
        def count = hibernateQuery.singleResult()
        then:
        count == 1 // Both are "Bob"
    }

    def joinWithProjection() {
        given:
        oldBob.addToPets(new Pet(name:"Lucky")).save(flush:true)
        hibernateQuery.join("pets").projections().property("pets.name").property("lastName")
        when:
        def answers = hibernateQuery.singleResult()
        then:
        answers[0] == "Lucky"
        answers[1] == "Builder"

    }

    def leftJoin() {
        given:
        hibernateQuery.join("pets", JoinType.LEFT)
        when:
        Person newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
        oldBob.pets == newBob.pets
    }

//    def makeLazy() {
//        given:
//        def eagerOwner= new EagerOwner( pets :[new Pet(name:\"Lucky\")])
//        hibernateQuery.join(\"pets\", JoinType.LEFT)
//        when:
//        Person newBob = hibernateQuery.singleResult()
//        then:
//        oldBob == newBob
//        oldBob.pets == newBob.pets
//    }

    def orderByAge() {
        def fred = new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        oldBob.addToPets(new Pet(name:"Lucky",age:1)).save(flush:true)
        fred.addToPets(new Pet(name:"Tom",age:2)).save(flush:true)
        given:
        hibernateQuery.join("pets")
                        .order(new Query.Order("pets.age", Query.Order.Direction.DESC))
        when:
        def bobs = hibernateQuery.list()
        then:
        bobs.size() == 2
        oldBob == bobs[1]
    }

    def orderByNameIgnoreCase() {
        def fred = new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        def walt = new Person(firstName: "Walt", lastName: "Disney", age: 50).save(flush: true)
        oldBob.addToPets(new Pet(name:"Lucky",age:1)).save(flush:true)
        fred.addToPets(new Pet(name:"Angel",age:2)).save(flush:true)
        walt.addToPets(new Pet(name:"angel",age:2)).save(flush:true)
        given:
        hibernateQuery.join("pets")
                .order(new Query.Order("pets.name", Query.Order.Direction.ASC).ignoreCase())
        when:
        def bobs = hibernateQuery.list()
        then:
        bobs.size() == 3
        oldBob == bobs[2]
    }

    def projectionProperty() {
        given:
        oldBob.addToPets(new Pet(name:"Lucky")).save(flush:true)
        oldBob.addToPets(new Pet(name:"Lucky")).save(flush:true)
        hibernateQuery.join("pets").projections().distinct("pets.name")
        when:
        def petName = hibernateQuery.singleResult()
        then:
        petName == "Lucky"
    }

    def projectionId() {
        given:
        hibernateQuery.projections().id()
        when:
        def id = hibernateQuery.singleResult()
        then:
        id == oldBob.id
    }

    def count() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        given:
        hibernateQuery.projections().count()
        when:
        def count = hibernateQuery.singleResult()
        then:
        count == 2
    }

    def max() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        given:
        hibernateQuery.projections().max("age")
        when:
        def age = hibernateQuery.singleResult()
        then:
        age == 50
    }

    def min() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        given:
        hibernateQuery.projections().min("age")
        when:
        def age = hibernateQuery.singleResult()
        then:
        age == 50
    }

    def sum() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        given:
        hibernateQuery.projections().sum("age")
        when:
        def age = hibernateQuery.singleResult()
        then:
        age == 102
    }

    def avg() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        given:
        hibernateQuery.projections().avg("age")
        when:
        def age = hibernateQuery.singleResult()
        then:
        age == 51
    }

    def sumBigDecimal() {
        given:
        HibernateDatastore hibernateDatastore = manager.hibernateDatastore
        HibernateSession session = hibernateDatastore.connect() as HibernateSession
        HibernateQuery query = new HibernateQuery(session, hibernateDatastore.getMappingContext().getPersistentEntity(HibernateQuerySpecBigDecimalEntity.typeName))
        new HibernateQuerySpecBigDecimalEntity(amount: 100.0G).save(flush: true, failOnError: true)
        new HibernateQuerySpecBigDecimalEntity(amount: 200.0G).save(flush: true, failOnError: true)

        query.projections().sum("amount")

        when:
        def sum = query.singleResult()

        then:
        sum == 300.0G
    }

    def avgBigDecimal() {
        given:
        HibernateDatastore hibernateDatastore = manager.hibernateDatastore
        HibernateSession session = hibernateDatastore.connect() as HibernateSession
        HibernateQuery query = new HibernateQuery(session, hibernateDatastore.getMappingContext().getPersistentEntity(HibernateQuerySpecBigDecimalEntity.typeName))
        new HibernateQuerySpecBigDecimalEntity(amount: 100.0G).save(flush: true, failOnError: true)
        new HibernateQuerySpecBigDecimalEntity(amount: 200.0G).save(flush: true, failOnError: true)

        query.projections().avg("amount")

        when:
        def avg = query.singleResult()

        then:
        avg == 150.0G
    }

    def groupByLastNameAverageAge() {
        def fred = new Person(firstName: "Fred", lastName: "Rogers", age: 52)
        fred.save(flush: true)
        oldBob.addToPets(new Pet(name:"Lucky",age:4)).save(flush:true)
        fred.addToPets(new Pet(name:"Lucky",age:2)).save(flush:true)
        given:
        hibernateQuery.join("pets")
                .projections()
                .groupProperty("pets.name")
                .avg("pets.age")
        when:
        def result = hibernateQuery.singleResult()
        then:
        result[0] == "Lucky"
        result[1] == 3
    }

    def sizeEquals() {
        given:
        new Pet(name: "Lucky", age: 48, owner: oldBob).save(flush:true)
        hibernateQuery.sizeEq("pets", 1)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def sizeGe() {
        given:
        new Pet(name: "Lucky", age: 48, owner: oldBob).save(flush:true)
        hibernateQuery.sizeGe("pets", 0)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def sizeGt() {
        given:
        new Pet(name: "Lucky", age: 48, owner: oldBob).save(flush:true)
        hibernateQuery.sizeGt("pets", 0)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def sizeLe() {
        given:
        new Pet(name: "Lucky", age: 48, owner: oldBob).save(flush:true)
        hibernateQuery.sizeLe("pets", 2)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def sizeLt() {
        given:
        new Pet(name: "Lucky", age: 48, owner: oldBob).save(flush:true)
        hibernateQuery.sizeLt("pets", 2)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def maxResults() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        hibernateQuery.maxResults(1).order(Query.Order.asc("age"))
        when:
        def bobs = hibernateQuery.list()
        then:
        bobs.size() == 1
        bobs[0] == oldBob

    }

    def notCriterion() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 51).save(flush: true)
        hibernateQuery.not(new Query.Equals("firstName", "Fred"))
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def andClosure() {
        given:
        new Person(firstName: "Bob", lastName: "Builder", age: 51).save(flush: true)
        hibernateQuery.and {
            eq "lastName", "Builder"
            eq "age", 50
        }
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def orClosure() {
        given:
        new Person(firstName: "Bob", lastName: "Builder", age: 51).save(flush: true)
        hibernateQuery.or {
            eq "lastName", "Rogers"
            eq "age", 50
        }
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def notClosure() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 51).save(flush: true)
        hibernateQuery.not {
            eq "firstName", "Fred"
        }
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def firstResult() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        hibernateQuery.firstResult(1).order(Query.Order.asc("age"))
        when:
        def bobs = hibernateQuery.list()
        then:
        bobs.size() == 1
        bobs[0].firstName == "Fred"
    }

    def select() {
        given:
        hibernateQuery.select("firstName")
        when:
        def names = hibernateQuery.list()
        then:
        names.size() == 1
        names[0] == "Bob"
    }

    def sizeNe() {
        given:
        new Pet(name: "Lucky", age: 48, owner: oldBob).save(flush:true)
        hibernateQuery.sizeNe("pets", 0)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def distinct() {
        given:
        new Person(firstName: "Bob", lastName: "Builder", age: 50).save(flush: true)
        hibernateQuery.projections().distinct("firstName")
        when:
        def results = hibernateQuery.list()
        then:
        results.size() == 1
        results[0] == "Bob"
    }


    def distinctQuery() {
        given:
        new Person(firstName: "Bob", lastName: "Builder", age: 50).save(flush: true)
        hibernateQuery.select("firstName").distinct()
        when:
        def results = hibernateQuery.list()
        then:
        results.size() == 1
        results[0] == "Bob"
    }

    def countMethod() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 51).save(flush: true)
        hibernateQuery.count()
        when:
        def count = hibernateQuery.singleResult()
        then:
        count == 2
    }

    def addCriterion() {
        given:
        hibernateQuery.add(new Query.Equals("firstName", "Bob"))
        when:
        def bob = hibernateQuery.singleResult()
        then:
        bob == oldBob
    }

    def addDetachedCriteria() {
        given:
        hibernateQuery.add(new DetachedCriteria(Person).eq("firstName", "Bob"))
        when:
        def bob = hibernateQuery.singleResult()
        then:
        bob == oldBob
    }

    def addJunctionCriterion() {
        given:
        hibernateQuery.add(new Query.Disjunction(), new Query.Equals("firstName", "Bob"))
        when:
        def bob = hibernateQuery.singleResult()
        then:
        bob == oldBob
    }

    def andList() {
        given:
        hibernateQuery.and([new Query.Equals("firstName", "Bob"), new Query.Equals("age", 50)])
        when:
        def bob = hibernateQuery.singleResult()
        then:
        bob == oldBob
    }

    def orList() {
        given:
        hibernateQuery.or([new Query.Equals("firstName", "Fred"), new Query.Equals("firstName", "Bob")])
        when:
        def bob = hibernateQuery.singleResult()
        then:
        bob == oldBob
    }

    def notList() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 51).save(flush: true)
        hibernateQuery.not([new Query.Equals("firstName", "Fred")])
        when:
        def bob = hibernateQuery.singleResult()
        then:
        bob == oldBob
    }

    def lock() {
        given:
        hibernateQuery.eq("firstName", "Bob").lock(true)
        when:
        def bob = hibernateQuery.singleResult()
        then:
        bob == oldBob
    }

    def cloneQuery() {
        given:
        hibernateQuery.eq("firstName", "Bob").max(10).offset(5)
        when:
        HibernateQuery cloned = (HibernateQuery) hibernateQuery.clone()
        then:
        cloned != hibernateQuery
        cloned.max == hibernateQuery.max
        cloned.offset == hibernateQuery.offset
        cloned.hibernateCriteria != null
    }

    def queryArguments() {
        given:
        hibernateQuery.setFetchSize(100)
        hibernateQuery.setTimeout(10)
        hibernateQuery.setHibernateFlushMode(org.hibernate.FlushMode.COMMIT)
        hibernateQuery.setReadOnly(true)
        hibernateQuery.eq("firstName", "Bob")
        when:
        def bob = hibernateQuery.singleResult()
        then:
        bob == oldBob
    }

    def listWithSession() {
        given:
        hibernateQuery.eq("firstName", "Bob")
        when:
        def session = sessionFactory.openSession()
        def results = hibernateQuery.list(session)
        session.close()
        then:
        results.size() == 1
        results[0] == oldBob
    }

    def singleResultWithSession() {
        given:
        hibernateQuery.eq("firstName", "Bob")
        when:
        def session = sessionFactory.openSession()
        def result = hibernateQuery.singleResult(session)
        session.close()
        then:
        result == oldBob
    }

    def scroll() {
        given:
        hibernateQuery.eq("firstName", "Bob")
        when:
        def scroll = hibernateQuery.scroll()
        then:
        scroll != null
    }

    def scrollWithSession() {
        given:
        hibernateQuery.eq("firstName", "Bob")
        when:
        def session = sessionFactory.openSession()
        def scroll = hibernateQuery.scroll(session)
        session.close()
        then:
        scroll != null
    }

    def equalsAllQueryable() {
        given:
        new Pet(name: "Lucky", age: 50, owner: oldBob).save(flush:true)
        hibernateQuery.eqAll("age", new DetachedCriteria(Pet).eq("name", "Lucky").property("age"))
        when:
        def result = hibernateQuery.singleResult()
        then:
        result == oldBob
    }

    def testCreateQuery() {
        when:
        def associationQuery = hibernateQuery.createQuery("pets")
        then:
        associationQuery != null
        associationQuery.getEntity() != null
    }

    def "test query publishes PreQueryEvent and PostQueryEvent"() {
        given:
        int preEvents = 0
        int postEvents = 0
        manager.hibernateDatastore.getApplicationEventPublisher().addApplicationListener(new org.springframework.context.ApplicationListener<org.grails.datastore.mapping.query.event.AbstractQueryEvent>() {
            @Override
            void onApplicationEvent(org.grails.datastore.mapping.query.event.AbstractQueryEvent event) {
                if (event instanceof org.grails.datastore.mapping.query.event.PreQueryEvent) {
                    preEvents++
                } else if (event instanceof org.grails.datastore.mapping.query.event.PostQueryEvent) {
                    postEvents++
                }
            }
        })

        when:
        hibernateQuery.eq("firstName", "Bob").list()

        then:
        preEvents > 0
        postEvents > 0
    }
}



@grails.persistence.Entity
class HibernateQuerySpecBigDecimalEntity implements Serializable {
    Long id
    Long version
    BigDecimal amount
}
