package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.DatastoreConfigurationException
import org.hibernate.mapping.PersistentClass
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.util.OrderByClauseBuilder

class OrderByClauseBuilderSpec extends HibernateGormDatastoreSpec {

    @Subject
    OrderByClauseBuilder builder = new OrderByClauseBuilder()

    void setupSpec() {
        manager.addAllDomainClasses([OrderTest, SubOrderTest, OrderWithComponent])
    }

    void "test buildOrderByClause with empty string (default to id)"() {
        given:
        PersistentClass pc = datastore.metadata.getEntityBinding(OrderTest.name)

        when:
        String result = builder.buildOrderByClause("", pc, "role", "asc")

        then:
        result == "id asc"
    }

    void "test buildOrderByClause with single property"() {
        given:
        PersistentClass pc = datastore.metadata.getEntityBinding(OrderTest.name)

        when:
        String result = builder.buildOrderByClause("name", pc, "role", "asc")

        then:
        result == "name asc"
    }

    void "test buildOrderByClause with property and explicit order"() {
        given:
        PersistentClass pc = datastore.metadata.getEntityBinding(OrderTest.name)

        when:
        String result = builder.buildOrderByClause("name desc", pc, "role", "asc")

        then:
        result == "name desc"
    }

    void "test buildOrderByClause with multiple properties"() {
        given:
        PersistentClass pc = datastore.metadata.getEntityBinding(OrderTest.name)

        when:
        String result = builder.buildOrderByClause("name, age desc", pc, "role", "asc")

        then:
        result == "name asc, age desc"
    }

    void "test buildOrderByClause with mapped column name"() {
        given:
        PersistentClass pc = datastore.metadata.getEntityBinding(OrderTest.name)

        when:
        String result = builder.buildOrderByClause("other", pc, "role", "asc")

        then:
        result == "other_column asc"
    }

    void "test buildOrderByClause with null string"() {
        given:
        PersistentClass pc = datastore.metadata.getEntityBinding(OrderTest.name)

        when:
        String result = builder.buildOrderByClause(null, pc, "role", "asc")

        then:
        result == null
    }

    void "test buildOrderByClause with non-existent property"() {
        given:
        PersistentClass pc = datastore.metadata.getEntityBinding(OrderTest.name)

        when:
        builder.buildOrderByClause("foo", pc, "role", "asc")

        then:
        thrown(DatastoreConfigurationException)
    }

    void "test buildOrderByClause with invalid sort clause"() {
        given:
        PersistentClass pc = datastore.metadata.getEntityBinding(OrderTest.name)

        when:
        // Double ordering keyword should fail
        builder.buildOrderByClause("name asc desc", pc, "role", "asc")

        then:
        thrown(DatastoreConfigurationException)
    }

    void "test buildOrderByClause with different defaultOrder"() {
        given:
        PersistentClass pc = datastore.metadata.getEntityBinding(OrderTest.name)

        when:
        String result = builder.buildOrderByClause("name", pc, "role", "desc")

        then:
        result == "name desc"
    }

    void "test buildOrderByClause with multiple columns"() {
        given:
        PersistentClass pc = datastore.metadata.getEntityBinding(OrderWithComponent.name)

        when:
        String result = builder.buildOrderByClause("comp", pc, "role", "asc")

        then:
        result == "comp_c1 asc, comp_c2 asc"
    }

    void "test buildOrderByClause with table prefix for inherited property"() {
        given:
        PersistentClass pc = datastore.metadata.getEntityBinding(SubOrderTest.name)

        when:
        // 'name' is in the base table 'order_test', 'subProperty' is in 'sub_order_test'
        String result = builder.buildOrderByClause("name, subProperty", pc, "role", "asc")

        then:
        // In GORM TPH is default, so no table prefix
        result == "name asc, sub_property asc"
    }
}

@Entity
class OrderTest {
    String name
    Integer age
    String other

    static mapping = {
        other column: 'other_column'
    }
}

@Entity
class SubOrderTest extends OrderTest {
    String subProperty
}

@Entity
class OrderWithComponent {
    Long id
    TestComponent comp
    static embedded = ['comp']
}

class TestComponent {
    String c1
    String c2
}
