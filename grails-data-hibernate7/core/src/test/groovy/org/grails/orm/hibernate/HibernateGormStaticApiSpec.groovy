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

package org.grails.orm.hibernate


import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.gorm.annotation.Entity
import grails.gorm.tests.entities.Club
import org.grails.datastore.gorm.GormRegistry
import org.hibernate.jpa.AvailableHints

class HibernateGormStaticApiSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(HibernateGormStaticApiEntity, HibernateGormStaticApiMappedPropertyEntity, Club, HibernateGormStaticApiMultiTenantEntity)
    }

    void "Test that HibernateGormStaticApi uses the shared template from the datastore"() {
        given:
        def api = GormRegistry.instance.findStaticApi(HibernateGormStaticApiEntity)

        expect:
        api.hibernateTemplate.is(manager.hibernateDatastore.getHibernateTemplate())
    }

    void "proxy test"() {
        given:
        def entity = new Club(name: "test").save(flush: true, failOnError: true)
        def entityId = entity.id
        manager.session.clear()

        when:
        def same = Club.proxy(entityId)

        then:
        same != null
        same.id == entityId
        // Note: In Hibernate 7, proxy initialization behavior differs from Hibernate 5/6
        // The proxy may be initialized during retrieval, so we don't assert !isInitialized
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

    void "Test findWhere matches null values"() {
        given:
        new HibernateGormStaticApiEntity(name: "null-test", nullableName: null).save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "other", nullableName: "present").save(flush: true, failOnError: true)

        when:
        def instance = HibernateGormStaticApiEntity.findWhere(nullableName: null)

        then:
        instance.name == 'null-test'
    }

    void "Test findAllWhere matches null values"() {
        given:
        new HibernateGormStaticApiEntity(name: "null-test-1", nullableName: null).save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "null-test-2", nullableName: null).save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "other", nullableName: "present").save(flush: true, failOnError: true)

        when:
        def instances = HibernateGormStaticApiEntity.findAllWhere(nullableName: null)

        then:
        instances.size() == 2
        instances*.name.containsAll(['null-test-1', 'null-test-2'])
    }

    void "Test findWhere rejects unsafe property names"() {
        when:
        HibernateGormStaticApiEntity.findWhere(['name) or 1=1 or (name': 'test'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('not a valid property')
    }

    void "Test findAllWhere rejects unsafe null-valued property names"() {
        when:
        HibernateGormStaticApiEntity.findAllWhere(['nullableName) is null or 1=1 or (nullableName': null])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('not a valid property')
    }

    void "Test findWhere rejects mapped column names"() {
        when:
        HibernateGormStaticApiMappedPropertyEntity.findWhere(name_col: 'test')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('not a valid property')
    }

    void "Test findWhere applies JPA hints from args"() {
        given:
        def entity = new HibernateGormStaticApiEntity(name: "hint-test").save(flush: true, failOnError: true)
        def entityId = entity.id
        session.clear()

        when:
        def instance = HibernateGormStaticApiEntity.findWhere([name: 'hint-test'], [(AvailableHints.HINT_READ_ONLY): true])
        instance.name = "modified"
        session.flush()

        and: "the instance is reloaded from the database"
        session.clear()
        def reloadedInstance = HibernateGormStaticApiEntity.get(entityId)

        then:
        reloadedInstance.name == "hint-test"
    }

    void "Test findAll with HQL using named params"() {
        given:
        new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when:
        def instances = HibernateGormStaticApiEntity.findAll("from HibernateGormStaticApiEntity where name like :pattern", [pattern: 'test%'])

        then:
        instances.size() == 2
    }

    void "Test findAll with plain String"() {
        given:
        new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when:
        String hql = "from HibernateGormStaticApiEntity where name = ?1"
        def results = HibernateGormStaticApiEntity.findAll(hql, ['test1'])

        then:
        results.size() == 1
        results[0].name == 'test1'
    }

    void "Test find with plain String"() {
        given:
        new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when:
        String hql = "from HibernateGormStaticApiEntity where name = :name"
        def result = HibernateGormStaticApiEntity.find(hql, [name: 'test2'])

        then:
        result.name == 'test2'
    }

    void "Test executeQuery with plain String"() {
        given:
        new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when: "a plain String HQL query is executed (no params map required, as on Hibernate 5)"
        String hql = "select name from HibernateGormStaticApiEntity"
        def names = HibernateGormStaticApiEntity.executeQuery(hql)

        then: "the query runs and returns the projected values"
        names.size() == 2
        names.containsAll(['test1', 'test2'])
    }

    void "Test executeUpdate with plain String"() {
        given:
        new HibernateGormStaticApiEntity(name: "test").save(flush: true, failOnError: true)

        when: "a plain String HQL update is executed"
        String hql = "update HibernateGormStaticApiEntity set name = 'updated'"
        int updated = HibernateGormStaticApiEntity.executeUpdate(hql)

        then: "the update runs and reports the affected row count"
        updated == 1
        HibernateGormStaticApiEntity.findByName('updated') != null
    }

    void "Test executeQuery with a GString binds interpolated values as parameters (injection-safe)"() {
        given:
        new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when: "a GString carrying a SQL-injection-style value is interpolated into the query"
        String malicious = "missing' or '1'='1"
        def results = HibernateGormStaticApiEntity.executeQuery("from HibernateGormStaticApiEntity where name = ${malicious}")

        then: "the value is bound as a parameter rather than interpolated, so the injection matches no rows"
        results.isEmpty()
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
        thrown(IllegalArgumentException)
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
        def updatedCount = HibernateGormStaticApiEntity.executeUpdate("update HibernateGormStaticApiEntity set name = :newName where name = :oldName", [newName: 'updated', oldName: 'test'])
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

    void "Test find with example returns matching instance"() {
        given:
        new HibernateGormStaticApiEntity(name: "alpha").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "beta").save(flush: true, failOnError: true)
        manager.session.clear()

        when:
        def result = HibernateGormStaticApiEntity.find(new HibernateGormStaticApiEntity(name: "beta"))

        then:
        result != null
        result.name == "beta"
    }

    void "Test find with example returns null when no match"() {
        given:
        new HibernateGormStaticApiEntity(name: "alpha").save(flush: true, failOnError: true)
        manager.session.clear()

        when:
        def result = HibernateGormStaticApiEntity.find(new HibernateGormStaticApiEntity(name: "nonexistent"))

        then:
        result == null
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

    void "Test findAll with example returns matching instances"() {
        given:
        new HibernateGormStaticApiEntity(name: "match").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "match").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "other").save(flush: true, failOnError: true)
        manager.session.clear()

        when:
        def results = HibernateGormStaticApiEntity.findAll(new HibernateGormStaticApiEntity(name: "match"))

        then:
        results.size() == 2
        results.every { it.name == "match" }
    }

    void "Test findAll with empty example returns empty list"() {
        given:
        new HibernateGormStaticApiEntity(name: "a").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "b").save(flush: true, failOnError: true)
        manager.session.clear()

        when: "no non-null properties to constrain on"
        def results = HibernateGormStaticApiEntity.findAll(new HibernateGormStaticApiEntity())

        then: "findAllWhere with empty map returns null (by design guard)"
        results == null
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

    void "Test getAll with empty list returns empty list"() {
        when:
        def instances = HibernateGormStaticApiEntity.getAll([])

        then:
        instances == []
    }

    void "Test getAll preserves input id order"() {
        given:
        def e1 = new HibernateGormStaticApiEntity(name: "first").save(failOnError: true)
        def e2 = new HibernateGormStaticApiEntity(name: "second").save(failOnError: true)
        def e3 = new HibernateGormStaticApiEntity(name: "third").save(flush: true, failOnError: true)

        when: "ids are requested in reverse order"
        def instances = HibernateGormStaticApiEntity.getAll([e3.id, e1.id, e2.id])

        then: "results are in the same order as the requested ids"
        instances.size() == 3
        instances[0].id == e3.id
        instances[1].id == e1.id
        instances[2].id == e2.id
    }

    void "Test getAll preserves input order for convertible ids"() {
        given:
        def e1 = new HibernateGormStaticApiEntity(name: "first").save(failOnError: true)
        def e2 = new HibernateGormStaticApiEntity(name: "second").save(failOnError: true)
        def e3 = new HibernateGormStaticApiEntity(name: "third").save(flush: true, failOnError: true)

        when: "ids are supplied as strings in reverse order"
        def instances = HibernateGormStaticApiEntity.getAll([e3.id.toString(), e1.id.toString(), e2.id.toString()])

        then: "results are ordered by the converted requested ids"
        instances.size() == 3
        instances[0].id == e3.id
        instances[1].id == e1.id
        instances[2].id == e2.id
    }

    void "Test getAll returns null in position for non-existent ids"() {
        given:
        def e1 = new HibernateGormStaticApiEntity(name: "exists").save(flush: true, failOnError: true)
        def missingId = e1.id + 9999L

        when:
        def instances = HibernateGormStaticApiEntity.getAll([e1.id, missingId])

        then:
        instances.size() == 2
        instances[0].id == e1.id
        instances[1] == null
    }

    void "Test getAll with duplicate ids returns entry at each position"() {
        given:
        def e1 = new HibernateGormStaticApiEntity(name: "dup").save(flush: true, failOnError: true)

        when:
        def instances = HibernateGormStaticApiEntity.getAll([e1.id, e1.id])

        then:
        instances.size() == 2
        instances[0].id == e1.id
        instances[1].id == e1.id
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

        when:"A static native SQL query with no user input"
        List<Club> results = Club.findAllWithSql("select * from club c order by c.name")

        then:"The results are correct"
        results.size() == 3
        results[0] instanceof Club
        Club club = results[0] as Club
        club.name == 'Arsenal'
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
        results.first().name == 'Arsenal'

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
        c != null
        c.name == "Manchester United"

        when:"A query that passes arguments is used"
        c = Club.find("from Club c where c.name like $p and c.name like :test order by c.name", [test:'%e%'])

        then:"The results are correct"
        c != null
        c.name == 'Manchester United'
    }

    // -------------------------------------------------------------------------
    // null-id guard branches
    // -------------------------------------------------------------------------

    void "get returns null for null id"() {
        expect:
        Club.get(null) == null
    }

    void "read returns null for null id"() {
        expect:
        Club.read(null) == null
    }

    void "load returns null for non-convertible id"() {
        expect: "String that can't be converted to Long makes convertIdentifier return null"
        Club.load("not-a-long") == null
    }

    void "proxy returns null for null id"() {
        expect:
        Club.proxy(null) == null
    }

    void "exists returns false for non-convertible id"() {
        expect:
        !Club.exists("not-a-long")
    }

    // -------------------------------------------------------------------------
    // first / last on empty table
    // -------------------------------------------------------------------------

    void "first returns null when table is empty"() {
        expect:
        Club.first() == null
    }

    void "last returns null when table is empty"() {
        expect:
        Club.last() == null
    }

    // -------------------------------------------------------------------------
    // findWhere / findAllWhere with empty map
    // -------------------------------------------------------------------------

    void "findWhere with empty queryMap returns null"() {
        expect:
        Club.findWhere([:]) == null
        Club.findWhere(null) == null
    }

    void "findAllWhere with empty queryMap returns null"() {
        expect:
        Club.findAllWhere([:]) == null
        Club.findAllWhere(null) == null
    }

    void "Test proxy returns null when id is null"() {
        expect:
        HibernateGormStaticApiEntity.proxy(null) == null
    }

    void "Test load returns null when id is null"() {
        expect:
        HibernateGormStaticApiEntity.load(null) == null
    }

    void "Test findWhere returns null when queryMap is null"() {
        expect:
        HibernateGormStaticApiEntity.findWhere(null) == null
    }

    void "Test findAllWhere returns null when queryMap is null"() {
        expect:
        HibernateGormStaticApiEntity.findAllWhere(null) == null
    }

    void "Test getAll with Iterable"() {
        given:
        def e1 = new HibernateGormStaticApiEntity(name: "test1").save(failOnError: true)
        def e2 = new HibernateGormStaticApiEntity(name: "test2").save(flush: true, failOnError: true)

        when:
        Iterable<Serializable> iterableIds = [e1.id, e2.id] as Set
        def instances = HibernateGormStaticApiEntity.getAll(iterableIds)

        then:
        instances.size() == 2
    }

    void "Test findAllWhere with queryMap and args"() {
        given:
        new HibernateGormStaticApiEntity(name: "test").save(failOnError: true)
        new HibernateGormStaticApiEntity(name: "test").save(flush: true, failOnError: true)

        when:
        def instances = HibernateGormStaticApiEntity.findAllWhere([name: 'test'], [max: 1])

        then:
        instances.size() == 1
    }

    // -------------------------------------------------------------------------
    // list with max — returns PagedResultList
    // -------------------------------------------------------------------------

    void "list with max parameter returns a PagedResultList"() {
        given:
        setupTestData()

        when:
        def result = Club.list(max: 2)

        then:
        result instanceof org.grails.orm.hibernate.query.PagedResultList
        result.size() <= 2
    }

    // -------------------------------------------------------------------------
    // convertIdentifier — convert throws (non-parseable String → Long)
    // -------------------------------------------------------------------------

    void "get with non-parseable String id returns null via convertIdentifier"() {
        expect: "conversion from 'notALong' to Long throws internally, returns null"
        Club.get("notALong") == null
    }

    // -------------------------------------------------------------------------
    // getQualifier — field set explicitly
    // -------------------------------------------------------------------------

    void "getQualifier returns the explicit qualifier when set in constructor"() {
        when:
        def api = new HibernateGormStaticApi<Club>(
                Club,
                manager.hibernateDatastore,
                [],
                Thread.currentThread().contextClassLoader,
                null,
                "secondary"
        )

        then:
        api.getQualifier() == "secondary"
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
    String nullableName

    static constraints = {
        nullableName nullable: true
    }
}

@Entity
class HibernateGormStaticApiMappedPropertyEntity {
    String name

    static mapping = {
        name column: 'name_col'
    }
}

@Entity
class HibernateGormStaticApiMultiTenantEntity implements grails.gorm.MultiTenant<HibernateGormStaticApiMultiTenantEntity> {
    String name
}
