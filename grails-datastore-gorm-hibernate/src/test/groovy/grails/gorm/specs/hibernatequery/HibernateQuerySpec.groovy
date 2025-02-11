package grails.gorm.specs.hibernatequery

import grails.gorm.DetachedCriteria
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.tests.Person
import grails.gorm.tests.Pet
import grails.persistence.Entity
import jakarta.persistence.criteria.JoinType
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
        HibernateDatastore hibernateDatastore = setupClass.hibernateDatastore
        AbstractHibernateSession session = hibernateDatastore.connect() as AbstractHibernateSession
        hibernateQuery = new HibernateQuery(session, hibernateDatastore.getMappingContext().getPersistentEntity(Person.typeName))
        petHibernateQuery = new HibernateQuery(session, hibernateDatastore.getMappingContext().getPersistentEntity(Pet.typeName))
        eagerHibernateQuery = new HibernateQuery(session, hibernateDatastore.getMappingContext().getPersistentEntity(EagerOwner.typeName))
        oldBob = new Person(firstName: "Bob", lastName: "Builder", age: 50).save(flush: true)
    }

    List getDomainClasses() {
        [Person,Pet, EagerOwner]
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


    @Ignore("Need better implementation of Predicate")
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
        oldBob.lastName = null
        oldBob.save(flush: true)
        given:
        hibernateQuery.isNull("lastName")
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
        def oldPet = new Pet(name: "Lucky")
        oldBob.addToPets(oldPet)
        oldBob.save(flush: true)
        petHibernateQuery.inList("owner",
            new DetachedCriteria(Person).eq("lastName", "Builder")
        )
        when:
        def newPet = petHibernateQuery.singleResult()
        then:
        oldPet == newPet
    }

    def notInSubQuery() {
        given:
        def oldPet = new Pet(name: "Lucky")
        oldBob.addToPets(oldPet)
        oldBob.save(flush: true)
        petHibernateQuery.notIn("owner",
                new DetachedCriteria(Person).eq("lastName", "Rogers")
        )
        when:
        def newPet = petHibernateQuery.singleResult()
        then:
        oldPet == newPet
    }

    @Ignore("Not sure why this is not passing")
    /**
     * @see org.grails.orm.hibernate.query.PredicateGenerator.getPredicates()
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
        def oldPet = new Pet(name: "Lucky")
        oldBob.addToPets(oldPet)
        oldBob.save(flush: true)
        hibernateQuery.exists(new DetachedCriteria(Pet).eq("owner", oldBob))
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    @Ignore("Not sure why this is not passing")
    /**
     * @see org.grails.orm.hibernate.query.PredicateGenerator.getPredicates()
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
        given:
        hibernateQuery.gtAll("age", new DetachedCriteria(Person).eq("age",48).property("age"))
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }


    def lessThanEqualsAll() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        given:
        hibernateQuery.leAll("age", new DetachedCriteria(Person).eq("age",50).property("age"))
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def lessThanAll() {
        new Person(firstName: "Fred", lastName: "Builder", age: 52).save(flush: true)
        given:
        hibernateQuery.ltAll("age", new DetachedCriteria(Person).eq("age",52).property("age"))
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }


    def greaterThanEqualsAll() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        given:
        hibernateQuery.geAll("age", new DetachedCriteria(Person).eq("age",50).property("age"))
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def greaterThanSome() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        given:
        hibernateQuery.gtSome("age", new DetachedCriteria(Person)
                .eq("age",48)
                .eq("firstName","Chuck")
                .property("age"))
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }


    def lessThanEqualsSome() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 52).save(flush: true)
        given:
        hibernateQuery.leSome("age",
                new DetachedCriteria(Person)
                        .eq("age",50)
                        .eq("firstName","Chuck")
                        .property("age"))
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def lessThanSome() {
        new Person(firstName: "Fred", lastName: "Builder", age: 52).save(flush: true)
        given:
        hibernateQuery.ltSome("age", new DetachedCriteria(Person)
                .eq("age",52)
                .eq("firstName","Chuck")
                .property("age"))
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }


    def greaterThanEqualsSome() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        given:
        hibernateQuery.geSome("age", new DetachedCriteria(Person)
                .eq("age",50)
                .eq("firstName","Chuck")
                .property("age"))
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def equalsAll() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        given:
        hibernateQuery.eqAll("age", new DetachedCriteria(Person).eq("age",50).property("age"))
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

    def makeLazy() {
        given:
        def eagerOwner= new EagerOwner( pets :[new Pet(name:"Lucky")])
        hibernateQuery.join("pets", JoinType.LEFT)
        when:
        Person newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
        oldBob.pets == newBob.pets
    }

    def orderByAge() {
        new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        given:
        hibernateQuery.order(new Query.Order("age", Query.Order.Direction.DESC))
        when:
        def bobs = hibernateQuery.list()
        then:
        bobs.size() == 2
        oldBob == bobs[0]
    }

    def orderByNameIgnoreCase() {
        def fred = new Person(firstName: "Fred", lastName: "Rogers", age: 48).save(flush: true)
        new Person(firstName: "Bob", lastName: "builder", age: 50).save(flush: true)
        given:
        hibernateQuery.order(new Query.Order("lastName", Query.Order.Direction.DESC).ignoreCase())
        when:
        def bobs = hibernateQuery.list()
        then:
        bobs.size() == 3
        fred == bobs[0]
    }

    def projectionProperty() {
        given:
        hibernateQuery.projections().property("lastName")
        when:
        def lastName = hibernateQuery.singleResult()
        then:
        lastName == "Builder"
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
        new Person(firstName: "Fred", lastName: "Builder", age: 52).save(flush: true)
        given:
        hibernateQuery.projections().groupProperty("lastName").avg("age")
        when:
        def result = hibernateQuery.singleResult()
        then:
        result[0] == "Builder"
        result[1] == 51
    }

    def sizeEquals() {
        given:
        Pet pet = new Pet(name: "Lucky")
        oldBob.addToPets(pet)
        oldBob.save(flush: true)
        hibernateQuery.sizeEq("pets", 1)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def sizeGe() {
        given:
        Pet pet = new Pet(name: "Lucky")
        oldBob.addToPets(pet)
        oldBob.save(flush: true)
        hibernateQuery.sizeGe("pets", 1)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def sizeGt() {
        given:
        Pet pet = new Pet(name: "Lucky")
        oldBob.addToPets(pet)
        oldBob.save(flush: true)
        hibernateQuery.sizeGt("pets", 0)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def sizeLe() {
        given:
        Pet pet = new Pet(name: "Lucky")
        oldBob.addToPets(pet)
        oldBob.save(flush: true)
        hibernateQuery.sizeGe("pets", 1)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def sizeLt() {
        given:
        Pet pet = new Pet(name: "Lucky")
        oldBob.addToPets(pet)
        oldBob.save(flush: true)
        hibernateQuery.sizeLt("pets", 2)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

}

@Entity
class EagerOwner implements Serializable {
    Set<Pet> pets = [] as Set
    Integer column1
    Integer column2
    static hasMany = [pets: Pet]
    static mapping = {
        pets lazy : false
    }
}
