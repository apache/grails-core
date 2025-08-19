package org.grails.orm.hibernate

import grails.gorm.DetachedCriteria
import grails.gorm.MultiTenant
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.annotation.Entity
import groovy.transform.EqualsAndHashCode
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.query.Query
import org.hibernate.Hibernate
import org.hibernate.LockMode
import org.hibernate.Session as NativeSession
import spock.lang.Ignore
import spock.lang.Issue

class HibernateGormStaticApiSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([HibernateGormStaticApiEntity])
    }

    void "Test that get returns the correct instance"() {
        given:
        def entity = new HibernateGormStaticApiEntity(name: "test").save(flush: true, failOnError: true)

        when:
        def instance = HibernateGormStaticApiEntity.get(entity.id)

        then:
        instance.id == entity.id
        instance.name == 'test'
    }

    void "Test that read returns a read-only instance"() {
        given:
        def entity = new HibernateGormStaticApiEntity(name: "test").save(flush: true, failOnError: true)
        def entityId = entity.id
        session.clear()

        when:
        def instance = HibernateGormStaticApiEntity.read(entityId)
        instance.name = "modified"
        session.flush()

        and: "the instance is reloaded from the database"
        session.clear()
        def reloadedInstance = HibernateGormStaticApiEntity.get(entityId)

        then:
        "the change was not persisted"
        reloadedInstance.name == "test"
    }

    void "Test that load returns"() {
        given:
        def entity = new HibernateGormStaticApiEntity(name: "test").save(flush: true, failOnError: true)
        session.clear()

        when:
        def instance = HibernateGormStaticApiEntity.load(entity.id)

        then:
        instance.id == entity.id
        instance.name == 'test'

    }

    void "Test that getAll returns all instances"() {
        given:
        new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when:
        def instances = HibernateGormStaticApiEntity.getAll()

        then:
        instances.size() == 2
        instances.find { it.name == 'test1' }
        instances.find { it.name == 'test2' }
    }

    void "Test that getAll with a list of ids returns correct instances"() {
        given:
        def e1 = new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(failOnError: true)
        def e3 = new HibernateGormStaticApiEntity(name: "test3").save(flush: true, failOnError: true)

        when:
        def instances = HibernateGormStaticApiEntity.getAll([e1.id, e3.id])

        then:
        instances.size() == 2
        instances.find { it.id == e1.id }
        instances.find { it.id == e3.id }
    }

    void "Test that count returns the correct number of instances"() {
        given:
        new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when:
        def count = HibernateGormStaticApiEntity.count()

        then:
        count == 2
    }

    void "Test that exists returns true for an existing instance"() {
        given:
        def entity = new HibernateGormStaticApiEntity(name: "test").save(flush: true, failOnError: true)

        when:
        def exists = HibernateGormStaticApiEntity.exists(entity.id)

        then:
        exists
    }

    void "Test that exists returns false for a non-existent instance"() {
        when:
        def exists = HibernateGormStaticApiEntity.exists(-1L)

        then:
        !exists
    }

    void "Test findWhere returns a single instance"() {
        given:
        new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when:
        def instance = HibernateGormStaticApiEntity.findWhere(name: 'test1')

        then:
        instance.name == 'test1'
    }

    void "Test findAllWhere returns multiple instances"() {
        given:
        new HibernateGormStaticApiEntity(name: "test").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test").save(flush: true, failOnError: true)

        when:
        def instances = HibernateGormStaticApiEntity.findAllWhere(name: 'test')

        then:
        instances.size() == 2
    }

    void "Test findAll with HQL"() {
        given:
        new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when:
        def instances = HibernateGormStaticApiEntity.findAll("from HibernateGormStaticApiEntity where name like 'test%'")

        then:
        instances.size() == 2
    }

    void "Test find with DetachedCriteria from where query"() {
        given:
        new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when:
        def criteria = HibernateGormStaticApiEntity.where { name == "test2" }
        def instance = HibernateGormStaticApiEntity.find(criteria)

        then:
        instance.name == "test2"
    }

    void "Test findAll with DetachedCriteria from where query"() {
        given:
        new HibernateGormStaticApiEntity(name: "test").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "other").save(flush: true, failOnError: true)

        when:
        def criteria = HibernateGormStaticApiEntity.where { name == "test" }
        def instances = HibernateGormStaticApiEntity.findAll(criteria)

        then:
        instances.size() == 2
    }

    void "Test count"() {
        given:
        new HibernateGormStaticApiEntity(name: "test").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "other").save(flush: true, failOnError: true)

        when:
        def count = HibernateGormStaticApiEntity.count()

        then:
        count == 3
    }



    void "Test merge"() {
        given:
        def entity = new HibernateGormStaticApiEntity(name: "test").save(flush: true, failOnError: true)
        session.clear()
        entity.name = "modified"

        when:
        def merged = HibernateGormStaticApiEntity.merge(entity)
        session.flush()
        session.clear()
        def updated = HibernateGormStaticApiEntity.get(entity.id)

        then:
        merged.name == "modified"
        updated.name == "modified"
    }

    void "Test refresh"() {
        given:
        def entity = new HibernateGormStaticApiEntity(name: "test").save(flush: true, failOnError: true)
        entity.name = "modified"

        when:
        HibernateGormStaticApiEntity.refresh(entity)

        then:
        entity.name == "test"
    }

    void "Test withTransaction"() {
        when:
        HibernateGormStaticApiEntity.withTransaction { status ->
            new HibernateGormStaticApiEntity(name: "test").save(failOnError: true)
            status.setRollbackOnly()
        }
        def count = HibernateGormStaticApiEntity.count()

        then:
        count == 0
    }

    void "Test withSession"() {
        when:
        def result = HibernateGormStaticApiEntity.withSession { s ->
            s.getIdentifier(new HibernateGormStaticApiEntity(name: "test"))
        }

        then:
        result == null
    }

    void "Test withNewSession"() {
        given:
        new HibernateGormStaticApiEntity(name: "outer").save(flush: true, failOnError: true)

        when:
        HibernateGormStaticApiEntity.withNewSession {
            new HibernateGormStaticApiEntity(name: "inner").save(flush: true, failOnError: true)
        }
        def count = HibernateGormStaticApiEntity.count()

        then:
        count == 2
    }

    void "Test executeUpdate"() {
        given:
        new HibernateGormStaticApiEntity(name: "test").save(flush: true, failOnError: true)

        when:
        def updated = HibernateGormStaticApiEntity.executeUpdate("update HibernateGormStaticApiEntity set name = 'updated' where name = 'test'")
        def instance = HibernateGormStaticApiEntity.first()

        then:
        updated == 1
        instance.name == 'updated'
    }

    void "Test lock"() {
        given:
        def entity = new HibernateGormStaticApiEntity(name: "test").save(flush: true, failOnError: true)
        session.clear()


        when:
        def newEntity = HibernateGormStaticApiEntity.lock(entity.id)


        then:
        entity.id == newEntity.id
    }

    void "Test that save does not flush immediately"() {
        when:
        def entity = new HibernateGormStaticApiEntity(name: "test")
        entity.save(failOnError: true)
        def found = HibernateGormStaticApiEntity.findWhere(name: 'test')

        then:
        "The instance is found in the session even without a flush"
        found != null
    }

    void "Test find with example throws exception"() {
        when:
        HibernateGormStaticApiEntity.find(new HibernateGormStaticApiEntity())

        then:
        thrown(UnsupportedOperationException)
    }

    void "Test first method"() {
        given:
        new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when:
        def instance = HibernateGormStaticApiEntity.first()

        then:
        instance.name == 'test1'
    }

    void "Test last method"() {
        given:
        new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when:
        def instance = HibernateGormStaticApiEntity.last()

        then:
        instance.name == 'test2'
    }

    void "Test find with named parameters"() {
        given:
        new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when:
        def instance = HibernateGormStaticApiEntity.find("from HibernateGormStaticApiEntity where name = :name", [name: 'test2'])

        then:
        instance.name == 'test2'
    }

    void "Test find with positional parameters"() {
        given:
        new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when:
        def instance = HibernateGormStaticApiEntity.find("from HibernateGormStaticApiEntity where name = ?1", ['test2'])

        then:
        instance.name == 'test2'
    }



    void "Test executeQuery with positional params"() {
        given:
        new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when:
        def entities = HibernateGormStaticApiEntity.executeQuery("from HibernateGormStaticApiEntity h where h.name like ?1", ['test%'])

        then:
        entities.size() == 2
        entities.collect{ it.name}.containsAll(['test1', 'test2'])
    }

    void "Test executeQuery with named params"() {
        given:
        new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when:
        def names = HibernateGormStaticApiEntity.executeQuery("select h.name from HibernateGormStaticApiEntity h where h.name like :name", [name: 'test%'])

        then:
        names.size() == 2
        names.contains('test1')
        names.contains('test2')
    }

    void "Test findAll with positional parameters"() {
        given:
        new HibernateGormStaticApiEntity(name: "test").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "other").save(flush: true, failOnError: true)

        when:
        def instances = HibernateGormStaticApiEntity.findAll("from HibernateGormStaticApiEntity where name = ?1", ['test'])

        then:
        instances.size() == 2
    }

    void "Test findAll with example throws exception"() {
        when:
        HibernateGormStaticApiEntity.findAll(new HibernateGormStaticApiEntity())

        then:
        thrown(UnsupportedOperationException)
    }

    void "Test getAll with long varargs"() {
        given:
        def e1 = new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(failOnError: true)
        def e3 = new HibernateGormStaticApiEntity(name: "test3").save(flush: true, failOnError: true)

        when:
        def instances = HibernateGormStaticApiEntity.getAll(e1.id, e3.id)

        then:
        instances.size() == 2
        instances.find { it.id == e1.id }
        instances.find { it.id == e3.id }
    }

    void "Test list method"() {
        given:
        new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when:
        def instances = HibernateGormStaticApiEntity.list(sort: "name", order: "desc")

        then:
        instances.size() == 2
        instances[0].name == 'test2'
        instances[1].name == 'test1'
    }

    void "Test createCriteria"() {
        when:
        def criteria = HibernateGormStaticApiEntity.createCriteria()

        then:
        criteria != null
    }

    void "Test executeUpdate with named params"() {
        given:
        new HibernateGormStaticApiEntity(name: "test").save(flush: true, failOnError: true)

        when:
        def updated = HibernateGormStaticApiEntity.executeUpdate("update HibernateGormStaticApiEntity set name = :newName where name = :oldName", [newName: 'updated', oldName: 'test'])
        def instance = HibernateGormStaticApiEntity.first()

        then:
        updated == 1
        instance.name == 'updated'
    }

    void "Test executeUpdate with positional params"() {
        given:
        new HibernateGormStaticApiEntity(name: "test").save(flush: true, failOnError: true)

        when:
        def updated = HibernateGormStaticApiEntity.executeUpdate("update HibernateGormStaticApiEntity set name = ? where name = ?", ['updated', 'test'])
        def instance = HibernateGormStaticApiEntity.first()

        then:
        updated == 1
        instance.name == 'updated'
    }
}

@Entity
class HibernateGormStaticApiEntity {
    String name
}

//@EqualsAndHashCode
//@Entity
//class Author implements MultiTenant<Author> {
//    Integer tenantId
//    String name
//    static hasMany = [books: ApiSpecBook]
//
//    boolean validate(List fields) {
//        true
//    }
//
//    Serializable getAssociationId(String associationName) {
//        null
//    }
//
//    Object propertyMissing(String name) {
//        throw new MissingPropertyException(name, getClass())
//    }
//
//    Object getPersistentValue(String name) {
//        null
//    }
//}
//
//@EqualsAndHashCode
//@Entity
//class ApiSpecBook implements MultiTenant<ApiSpecBook> {
//    Integer tenantId
//    String title
//    static belongsTo = [author: Author]
//
//    boolean validate(List fields) {
//        true
//    }
//
//    Serializable getAssociationId(String associationName) {
//        null
//    }
//
//    Object propertyMissing(String name) {
//        throw new MissingPropertyException(name, getClass())
//    }
//
//    Object getPersistentValue(String name) {
//        null
//    }
//}