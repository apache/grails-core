package grails.gorm.specs.hibernatequery

import grails.gorm.DetachedCriteria
import grails.gorm.specs.HibernateGormDatastoreSpec
import jakarta.persistence.criteria.JoinType
import org.apache.grails.data.testing.tck.domains.*
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.AbstractHibernateSession
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.query.HibernateQuery
import spock.lang.Ignore


class HibernateQuerySpec extends HibernateGormDatastoreSpec {


    HibernateQuery hibernateQuery
    HibernateQuery petHibernateQuery
    HibernateQuery eagerHibernateQuery

    Person oldBob

    def setup() {
        HibernateDatastore hibernateDatastore = manager.hibernateDatastore
        AbstractHibernateSession session = hibernateDatastore.connect() as AbstractHibernateSession
        hibernateQuery = new HibernateQuery(session, hibernateDatastore.getMappingContext().getPersistentEntity(Person.typeName))
        petHibernateQuery = new HibernateQuery(session, hibernateDatastore.getMappingContext().getPersistentEntity(Pet.typeName))
        eagerHibernateQuery = new HibernateQuery(session, hibernateDatastore.getMappingContext().getPersistentEntity(EagerOwner.typeName))
        oldBob = new Person(firstName: "Bob", lastName: "Builder", age: 50).save(flush: true)
    }

    def setupSpec() {
        manager.domainClasses.clear()
        manager.domainClasses.addAll([Person, Pet, Face,EagerOwner])
    }

    def equals() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 51).save(flush: true)
        hibernateQuery.eq("age", 50)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def equalsJoins() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 51).save(flush: true)
        oldBob.addToPets(new Pet(name: "Lucky")).save(flush:"true")
        hibernateQuery.join("pets").eq("pets.name", "Lucky")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def ne() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 51).save(flush: true)
        hibernateQuery.ne("age", 51)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def eqProperty() {
        given:
        def oldMajor = new Person(firstName: "Major", lastName: "Major", age: 51).save(flush: true)
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

//    def leProperty() {
//        given:
//        def oldEager = new EagerOwner(column1: 1, column2: 2).save(flush: true)
//        eagerHibernateQuery.leProperty("column1", "column2")
//        when:
//        def newEager = eagerHibernateQuery.singleResult()
//        then:
//        oldEager == newEager
//    }

//    def ltProperty() {
//        given:
//        def oldEager = new EagerOwner(column1: 1, column2: 2).save(flush: true)
//        eagerHibernateQuery.ltProperty("column1", "column2")
//        when:
//        def newEager = eagerHibernateQuery.singleResult()
//        then:
//        oldEager == newEager
//    }

//    def geProperty() {
//        given:
//        def oldEager = new EagerOwner(column1: 2, column2: 1).save(flush: true)
//        eagerHibernateQuery.geProperty("column1", "column2")
//        when:
//        def newEager = eagerHibernateQuery.singleResult()
//        then:
//        oldEager == newEager
//    }
//
//    def gtProperty() {
//        given:
//        def oldEager = new EagerOwner(column1: 2, column2: 1).save(flush: true)
//        eagerHibernateQuery.gtProperty("column1", "column2")
//        when:
//        def newEager = eagerHibernateQuery.singleResult()
//        then:
//        oldEager == newEager
//    }


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

    @Ignore("Must add custom functionality")
    def rlike() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        hibernateQuery.rlike("firstName", "/Bob*/")
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
        Query.Criterion lastNameWrong = new Query.Equals("lastName", "Rogers")
        Query.Criterion ageCorrect = new Query.Equals("age", 50)
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
        Query.Criterion ageIncorrect = new Query.Equals("age", 51)
        hibernateQuery.not(lastNameWrong, ageIncorrect)
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
                .join("pets")

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

//    @Ignore("Exits subquery is broken")
    /**
     * org.grails.orm.hibernate.query.PredicateGenerator.getPredicates()
     * else if (criterion instanceof Query.Exists c)
     select
     p1_0.id,
     p1_0.age,
     p1_0.face_id,
     p1_0.first_name,
     p1_0.last_name,
     p1_0.my_boolean_property,
     p1_0.version
     from
     person p1_0
     where
     exists(select
     1
     from
     pet p2_0
     where
     p2_0.owner_id=?)
     offset
     ? rows
     */
    def exists() {
        given:
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        new Pet(name: "Lucky", owner: oldBob).save(flush:true)
        hibernateQuery.exists(
                new DetachedCriteria(Pet).property("name").eq("owner", oldBob)
        )
        when:
        def list = hibernateQuery.list()
        then:
        list.size() == 1
        oldBob == list.get(0)
    }

    /**
     *  org.grails.orm.hibernate.query.PredicateGenerator.getPredicates()
     * else if (criterion instanceof Query.NotExists c)
     select
     p1_0.id,
     p1_0.age,
     p1_0.face_id,
     p1_0.first_name,
     p1_0.last_name,
     p1_0.my_boolean_property,
     p1_0.version
     from
     person p1_0
     where
     not exists(select
     1
     from
     pet p2_0
     where
     p2_0.owner_id=?)
     offset
     ? rows
     */
    def notExists() {
        given:
        def fred = new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        def oldPet = new Pet(name: "Lucky")
        oldBob.addToPets(oldPet)
        oldBob.save(flush: true)
        hibernateQuery.notExits(new DetachedCriteria(Pet).eq("owner", fred))
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
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
//        def eagerOwner= new EagerOwner( pets :[new Pet(name:"Lucky")])
//        hibernateQuery.join("pets", JoinType.LEFT)
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
        hibernateQuery.join("pets").projections().property("pets.name").distinct("pets.name")
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

}


