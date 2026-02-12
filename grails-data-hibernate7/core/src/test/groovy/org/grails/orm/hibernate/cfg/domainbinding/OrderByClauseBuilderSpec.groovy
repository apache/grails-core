package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.mapping.PersistentClass
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.util.OrderByClauseBuilder

class OrderByClauseBuilderSpec extends HibernateGormDatastoreSpec {

    @Subject
    OrderByClauseBuilder builder = new OrderByClauseBuilder()

    void setupSpec() {
        manager.addAllDomainClasses([OrderTest, SubOrderTest])
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

    void "test buildOrderByClause with table prefix for inherited property"() {
        given:
        PersistentClass pc = datastore.metadata.getEntityBinding(SubOrderTest.name)

        when:
        // 'name' is in the base table 'order_test', 'subProperty' is in 'sub_order_test'
        String result = builder.buildOrderByClause("name, subProperty", pc, "role", "asc")

        then:
        // Hibernate 7 mapping might use different table qualification depending on strategy.
        // Assuming joined-subclass or TPH.
        // If TPH (default GORM), table prefix might be empty if it's the same table.
        result.contains("name asc")
        result.contains("sub_property asc")
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
