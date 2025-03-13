package grails.gorm.tests

import grails.gorm.DetachedCriteria
import grails.gorm.tck.Person
import org.grails.datastore.mapping.query.jpa.JpaQueryBuilder
import org.springframework.dao.InvalidDataAccessResourceUsageException

/**
 * Test for JPA builder
 */
class JpaQueryBuilderSpec extends GormDatastoreSpec{

    void "Test update query with ilike criterion"() {
        given:"Some criteria"
            DetachedCriteria criteria = new DetachedCriteria(Person).build {
                eq 'age', 10
                ilike 'firstName', 'Bob'
            }

        when:"A jpa query is built"
            def builder = new JpaQueryBuilder(session.mappingContext.getPersistentEntity(Person.name),criteria.criteria)
            def queryInfo = builder.buildUpdate(firstName:"Fred")

        then:"The query is valid"
            queryInfo.query == 'UPDATE grails.gorm.tck.Person person SET person.firstName=:p1 WHERE (person.age=:p2 AND lower(person.firstName) like lower(:p3))'
    }

    void "Test update query with subquery"() {
        given:"Some criteria"
        DetachedCriteria criteria = new DetachedCriteria(Person).build {
            notIn("age", new DetachedCriteria(Person).build {
                eq('lastName', 'Simpson')
            }.distinct('age'))
        }

        when:"A jpa query is built"
        def builder = new JpaQueryBuilder(session.mappingContext.getPersistentEntity(Person.name),criteria.criteria)
        def queryInfo = builder.buildUpdate(firstName:"Fred")

        then:"The query is valid"
        queryInfo.query == 'UPDATE grails.gorm.tck.Person person SET person.firstName=:p1 WHERE (person.age NOT IN (SELECT person1.age FROM grails.gorm.tck.Person person1 WHERE person1.lastName=:p2))'
        queryInfo.parameters == ["Fred", "Simpson"]

    }

    void "Test exception is thrown in join with delete"() {
        given:"Some criteria"
            DetachedCriteria criteria = new DetachedCriteria(Person).build {
                pets {
                    eq 'name', 'Ted'
                }
                eq 'firstName', 'Bob'
            }

        when:"A jpa query is built"
            def builder = new JpaQueryBuilder(session.mappingContext.getPersistentEntity(Person.name),criteria.criteria)
            builder.buildDelete()

        then:"The query throws an exception"
            def e = thrown(InvalidDataAccessResourceUsageException)
            e.message == 'Joins cannot be used in a DELETE or UPDATE operation'

    }

    void "Test build update property natural ordering and hibernate compatible"() {
        given:"Some criteria"
            DetachedCriteria criteria = new DetachedCriteria(Person).build {
                eq 'firstName', 'Bob'
            }

        when:"A jpa query is built"
            def builder = new JpaQueryBuilder(session.mappingContext.getPersistentEntity(Person.name),criteria.criteria)
            builder.hibernateCompatible = true
            def queryInfo = builder.buildUpdate(firstName:'Bob updated', age:30)

        then:"The query is valid"
            queryInfo.query != null
            queryInfo.query == 'UPDATE grails.gorm.tck.Person person SET person.age=:p1, person.firstName=:p2 WHERE (person.firstName=:p3)'
            queryInfo.parameters == [30,"Bob updated", "Bob"]
    }

    void "Test build update property natural ordering"() {
        given:"Some criteria"
            DetachedCriteria criteria = new DetachedCriteria(Person).build {
                eq 'firstName', 'Bob'
            }

        when:"A jpa query is built"
            def builder = new JpaQueryBuilder(session.mappingContext.getPersistentEntity(Person.name),criteria.criteria)
            def queryInfo = builder.buildUpdate(firstName:'Bob updated', age:30)

        then:"The query is valid"
            queryInfo.query != null
            queryInfo.query == 'UPDATE grails.gorm.tck.Person person SET person.age=:p1, person.firstName=:p2 WHERE (person.firstName=:p3)'
            queryInfo.parameters == [30,"Bob updated", "Bob"]
    }

    void "Test build update"() {
        given:"Some criteria"
            DetachedCriteria criteria = new DetachedCriteria(Person).build {
                eq 'firstName', 'Bob'
            }

        when:"A jpa query is built"
            def builder = new JpaQueryBuilder(session.mappingContext.getPersistentEntity(Person.name),criteria.criteria)
            def queryInfo = builder.buildUpdate(age:30)

        then:"The query is valid"
            queryInfo.query != null
            queryInfo.query == 'UPDATE grails.gorm.tck.Person person SET person.age=:p1 WHERE (person.firstName=:p2)'
            queryInfo.parameters == [30, "Bob"]
    }

    void "Test build delete"() {
        given:"Some criteria"
            DetachedCriteria criteria = new DetachedCriteria(Person).build {
                eq 'firstName', 'Bob'
            }

        when:"A jpa query is built"
            def builder = new JpaQueryBuilder(session.mappingContext.getPersistentEntity(Person.name),criteria.criteria)
            def queryInfo = builder.buildDelete()

        then:"The query is valid"
            queryInfo.query != null
            queryInfo.query == 'DELETE grails.gorm.tck.Person person WHERE (person.firstName=:p1)'
            queryInfo.parameters == ["Bob"]
    }

    void "Test build simple select hibernate compatible"() {
        given:"Some criteria"
            DetachedCriteria criteria = new DetachedCriteria(Person).build {
                eq 'firstName', 'Bob'
            }

        when:"A jpa query is built"
            def builder = new JpaQueryBuilder(session.mappingContext.getPersistentEntity(Person.name),criteria.criteria)
            builder.hibernateCompatible = true
            def query = builder.buildSelect().query

        then:"The query is valid"
            query != null
            query == 'SELECT DISTINCT person FROM grails.gorm.tck.Person AS person WHERE (person.firstName=:p1)'
    }

    void "Test build simple select"() {
        given:"Some criteria"
            DetachedCriteria criteria = new DetachedCriteria(Person).build {
                eq 'firstName', 'Bob'
            }

        when:"A jpa query is built"
            def builder = new JpaQueryBuilder(session.mappingContext.getPersistentEntity(Person.name),criteria.criteria)
            def query = builder.buildSelect().query

        then:"The query is valid"
            query != null
            query == 'SELECT DISTINCT person FROM grails.gorm.tck.Person AS person WHERE (person.firstName=:p1)'
    }

    void "Test build select with or"() {
        given:"Some criteria"
            DetachedCriteria criteria = new DetachedCriteria(Person).build {
                or {
                    eq 'firstName', 'Bob'
                    eq 'firstName', 'Fred'
                }
            }

        when:"A jpa query is built"
            def builder = new JpaQueryBuilder(session.mappingContext.getPersistentEntity(Person.name),criteria.criteria)
            final queryInfo = builder.buildSelect()

        then:"The query is valid"
            queryInfo.query!= null
            queryInfo.query == 'SELECT DISTINCT person FROM grails.gorm.tck.Person AS person WHERE ((person.firstName=:p1 OR person.firstName=:p2))'
            queryInfo.parameters == ['Bob', 'Fred']

    }

    void "Test build DELETE with an empty criteria or build {}"() {
        given:
        DetachedCriteria criteria = new DetachedCriteria(Person).build {}

        when: "A jpa query is built"
        def builder = new JpaQueryBuilder(session.mappingContext.getPersistentEntity(Person.name), criteria.criteria)
        final queryInfo = builder.buildDelete()

        then: "The query is valid"
        queryInfo.query!=null
        queryInfo.query == 'DELETE grails.gorm.tck.Person person'
        queryInfo.parameters == []
    }


    void "Test build SELECT with an empty criteria or build {}"() {
        given:
        DetachedCriteria criteria = new DetachedCriteria(Person).build {}

        when: "A jpa query is built"
        def builder = new JpaQueryBuilder(session.mappingContext.getPersistentEntity(Person.name), criteria.criteria)
        final queryInfo = builder.buildSelect()

        then: "The query is valid"
        queryInfo.query!=null
        queryInfo.query == 'SELECT DISTINCT person FROM grails.gorm.tck.Person AS person'
        queryInfo.parameters == null
    }

    void "Test build UPDATE with an empty criteria or build {}"() {
        given:
        DetachedCriteria criteria = new DetachedCriteria(Person).build {}

        when: "A jpa query is built"
        def builder = new JpaQueryBuilder(session.mappingContext.getPersistentEntity(Person.name), criteria.criteria)
        final queryInfo = builder.buildUpdate(firstName:"Fred")

        then: "The query is valid"
        queryInfo.query!=null
        queryInfo.query == 'UPDATE grails.gorm.tck.Person person SET person.firstName=:p1'
        queryInfo.parameters == ["Fred"]
    }
}
