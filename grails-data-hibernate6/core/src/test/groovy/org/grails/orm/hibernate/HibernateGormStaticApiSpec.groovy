package org.grails.orm.hibernate


import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.annotation.Entity
import grails.gorm.specs.entities.Club
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.exceptions.GrailsQueryException

class HibernateGormStaticApiSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([HibernateGormStaticApiEntity,Club])
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


    void "TestwithSession"() {
        when:
        HibernateGormStaticApiEntity.withSession { s ->
            // In Hibernate 6, getIdentifier on a transient (not associated) instance throws TransientObjectException
            s.getIdentifier(new HibernateGormStaticApiEntity(name: "test"))
        }

        then:
        thrown(org.springframework.dao.InvalidDataAccessApiUsageException)
    }

    //TODO no transaction is in progress
    void "Test withNewSession"() {
        given:
        new HibernateGormStaticApiEntity(name: "outer").save(flush: true, failOnError: true)

        when:
        session.clear()
        new HibernateGormStaticApiEntity(name: "inner").save(flush: true, failOnError: true)
        session.clear()

        def count = HibernateGormStaticApiEntity.count()

        then:
        count == 2
    }

    void "Test executeUpdate"() {
        given:
        def entity = new HibernateGormStaticApiEntity(name: "test").save(flush: true, failOnError: true)
        def entityId = entity.id

        when:
        def updatedCount = HibernateGormStaticApiEntity.executeUpdate("update HibernateGormStaticApiEntity set name = 'updated' where name = 'test'")
        session.clear()
        def instance = HibernateGormStaticApiEntity.get(entityId)

        then:
        updatedCount == 1
        instance.name == 'updated'

        cleanup:
        HibernateGormStaticApiEntity.findAll().each { it.delete(flush: true) }
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
        def names = HibernateGormStaticApiEntity.executeQuery("select h.name from HibernateGormStaticApiEntity h where h.name like :name", [name: 'test%'],[:])

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
        def entity = new HibernateGormStaticApiEntity(name: "test").save(flush: true, failOnError: true)
        def entityId = entity.id

        when:
        def updatedCount = HibernateGormStaticApiEntity.executeUpdate("update HibernateGormStaticApiEntity set name = :newName where name = :oldName", [newName: 'updated', oldName: 'test'])
        session.clear()
        def instance = HibernateGormStaticApiEntity.get(entityId)

        then:
        updatedCount == 1
        instance.name == 'updated'

        cleanup:
        HibernateGormStaticApiEntity.withNewTransaction {
            HibernateGormStaticApiEntity.findAll().each { it.delete(flush: true) }
        }
    }

    void "Test executeUpdate with positional params"() {
        given:
        def entity = new HibernateGormStaticApiEntity(name: "test").save(flush: true, failOnError: true)
        def entityId = entity.id

        when:
        def updatedCount = HibernateGormStaticApiEntity.executeUpdate("update HibernateGormStaticApiEntity set name = ?1 where name = ?2", ['updated', 'test'])
        session.clear()
        def instance = HibernateGormStaticApiEntity.get(entityId)

        then:
        updatedCount == 1
        instance.name == 'updated'

        cleanup:
        HibernateGormStaticApiEntity.withNewTransaction {
            HibernateGormStaticApiEntity.findAll().each { it.delete(flush: true) }
        }
    }


    void "test simple sql query"() {

        given:
        setupTestData()

        when:"Some test data is saved"
        List<Club> results = Club.findAllWithSql("select * from club c order by c.name")

        then:"The results are correct"
        results.size() == 3
        results[0] instanceof Club
        results[0].name == 'Arsenal'
    }

    void "test sql query with gstring parameters"() {
        given:
        setupTestData()

        when:"Some test data is saved"
        String p = "%l%"
        List<Club> results = Club.findAllWithSql("select * from club c where c.name like $p order by c.name")

        then:"The results are correct"
        results.size() == 2
    }

    void "test escape HQL in findAll with gstring"() {
        given:
        setupTestData()

        when:"A query is used that embeds a GString with a value that should be encoded for the query to succeed"
        String p = "%l%"
        List<Club> results = Club.findAll("from Club c where c.name like $p order by c.name")

        then:"Exception is thrown"
        results.size() == 2

        when:"A query that passes arguments is used"
        results = Club.findAll("from Club c where c.name like $p and c.name like :test order by c.name", [test:'%e%'])

        then:"The results are correct"
        results.size() == 2
        results[0] instanceof Club
        results[0].name == 'Arsenal'
    }

    void "test escape HQL in executeQuery with gstring"() {
        given:
        setupTestData()

        when:"A query is used that embeds a GString with a value that should be encoded for the query to succeed"
        String p = "%l%"
        List<Club> results = Club.executeQuery("from Club c where c.name like $p order by c.name")

        then:"The results are correct"
        results.size() == 2


        when:"A query that passes arguments is used"
        results = Club.executeQuery("from Club c where c.name like $p and c.name like :test order by c.name", [test:'%e%'],[:])

        then:"The results are correct"
        results.size() == 2
    }

    void "test escape HQL in find with gstring"() {
        given:
        setupTestData()

        when:"A query is used that embeds a GString with a value that should be encoded for the query to succeed"
        String p = "%chester%"
        Club c = Club.find("from Club c where c.name like $p order by c.name")

        then:"The results are correct"
        thrown(GrailsQueryException)

        when:"A query that passes arguments is used"
        c = Club.find("from Club c where c.name like $p and c.name like :test order by c.name", [test:'%e%'])

        then:"The results are correct"
        c != null
        c.name == 'Manchester United'
    }

    protected void setupTestData() {
        new Club(name: "Barcelona").save()
        new Club(name: "Arsenal").save()
        new Club(name: "Manchester United").save(flush: true)
    }
}

@Entity
class HibernateGormStaticApiEntity {
    String name
}

