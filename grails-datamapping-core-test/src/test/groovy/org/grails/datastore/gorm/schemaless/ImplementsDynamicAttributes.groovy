package org.grails.datastore.gorm.schemaless

import spock.lang.Issue
import spock.lang.Specification


class DynamicDomainSpec extends Specification {

    @Issue("GDM-769")
    void "Test a domain with dynamic attributes doesn't try to set readonly properties"() {
        given:
        DynamicEntity entity = new DynamicEntity()

        when:
        entity.putAt("foo", 123)
        entity.putAt("name", "Sally")

        then:
        entity.foo == "foo"
        entity.name == "Sally"
        entity.getAt("foo") == "foo"
        !entity.attributes().containsKey("name")
        entity.attributes().foo == 123
    }

}

class DynamicEntity implements DynamicAttributes {

    String name

    String getFoo() {
        "foo"
    }

}