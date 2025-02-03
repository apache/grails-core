package grails.gorm.tests.hibernatequery

import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.gorm.tests.Person
import grails.gorm.tests.Pet
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.AbstractHibernateSession
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.query.HibernateQuery
import spock.lang.Ignore


class HibernateQuerySpec extends HibernateGormDatastoreSpec {


    HibernateQuery hibernateQuery

    Person oldBob

    def setup() {
        HibernateDatastore hibernateDatastore = setupClass.hibernateDatastore
        AbstractHibernateSession session = hibernateDatastore.connect() as AbstractHibernateSession
        hibernateQuery = new HibernateQuery(session, hibernateDatastore.getMappingContext().getPersistentEntity(Person.typeName))
        oldBob = new Person(firstName:"Bob", lastName:"Builder", age: 50).save(flush: true)
    }

    List getDomainClasses() {
        [Person]
    }

    def equals() {
        given:
            new Person(firstName:"Fred", lastName:"Rogers", age: 51).save(flush: true)
            hibernateQuery.eq("age", 50)
        when:
            def newBob = hibernateQuery.singleResult()
        then:
           oldBob == newBob
    }

    @Ignore("Need better implementation of Predicate")
    def idEq() {
        given:
        Person oldFred = new Person(firstName:"Fred", lastName:"Rogers", age: 51).save(flush: true)
        hibernateQuery.idEq(oldFred.id)
        when:
        def newFred = hibernateQuery.singleResult()
        then:
        oldFred == newFred
    }

    def gt() {
        given:
        new Person(firstName:"Fred", lastName:"Rogers", age: 48).save(flush: true)
        hibernateQuery.gt("age", 49)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def ge() {
        given:
        new Person(firstName:"Fred", lastName:"Rogers", age: 48).save(flush: true)
        hibernateQuery.ge("age", 50)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def le() {
        given:
        new Person(firstName:"Fred", lastName:"Rogers", age: 52).save(flush: true)
        hibernateQuery.le("age", 50)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def lt() {
        given:
        new Person(firstName:"Fred", lastName:"Rogers", age: 52).save(flush: true)
        hibernateQuery.lt("age", 51)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }


    def like() {
        given:
        new Person(firstName:"Fred", lastName:"Rogers", age: 52).save(flush: true)
        hibernateQuery.like("firstName", "Bo%")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }


    def ilike() {
        given:
        new Person(firstName:"Fred", lastName:"Rogers", age: 52).save(flush: true)
        hibernateQuery.ilike("firstName", "BO%")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    @Ignore("Must add custom functionality")
    def rlike() {
        given:
        new Person(firstName:"Fred", lastName:"Rogers", age: 52).save(flush: true)
        hibernateQuery.rlike("firstName", "/Bob*/")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def and() {
        given:
        new Person(firstName:"Bob", lastName:"Builder", age: 51).save(flush: true)
        Query.Criterion lastName = new Query.Equals("lastName", "Builder")
        Query.Criterion age = new Query.Equals("age", 50)
        hibernateQuery.and(lastName,age)
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def or() {
        given:
        new Person(firstName:"Bob", lastName:"Builder", age: 51).save(flush: true)
        Query.Criterion lastNameWrong = new Query.Equals("lastName", "Rogers")
        Query.Criterion ageCorrect = new Query.Equals("age", 50)
        hibernateQuery.or(lastNameWrong,ageCorrect)
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
        oldBob.addToPets(new Pet()).save(flush: true)
        given:
        hibernateQuery.isNotEmpty("pets")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def isNull() {
        oldBob.lastName = null
        oldBob.save(flush:true)
        given:
        hibernateQuery.isNull("lastName")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def isNotNull() {
        new Person(firstName:"Fred", age: 52).save(flush: true)
        given:
        hibernateQuery.isNotNull("lastName")
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def allEq() {
        new Person(firstName:"Fred", lastName:"Rogers", age: 52).save(flush: true)
        given:
        hibernateQuery.allEq(["firstName":"Bob","lastName":"Builder"])
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

    def inList() {
        new Person(firstName:"Fred", lastName:"Rogers", age: 52).save(flush: true)
        given:
        hibernateQuery.in("age", [50,51])
        when:
        def newBob = hibernateQuery.singleResult()
        then:
        oldBob == newBob
    }

}
