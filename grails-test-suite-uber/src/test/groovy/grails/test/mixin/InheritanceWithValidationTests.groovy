package grails.test.mixin

import grails.persistence.Entity
import grails.testing.gorm.DataTest
import grails.validation.Validateable
import spock.lang.Specification

/**
 * Test for GRAILS-9010
 */
class InheritanceWithValidationTests extends Specification implements DataTest {

    void setupSpec() {
        mockDomains(CustomProperty, StringPropertyValue)
    }

    void testNewStringValue () {

        given:
        def property = new CustomProperty()

        when:
        def propertyValue = property.newValue('testValue')

        then:
        propertyValue.valid
        propertyValue.validate()
    }
}

@Entity
@SuppressWarnings('unused')
class AbstractCustomPropertyValue implements Validateable {

    boolean valid = false

    static constraints = {
        valid (validator: validator)
    }

    static transients = ['valid']

    protected static validator = { boolean value, AbstractCustomPropertyValue instance ->
        if (!instance.valid) {
            return 'invalid.value.for.type'
        }

        return null // returning null means valid
    }
}

@Entity
@SuppressWarnings('GrMethodMayBeStatic')
class CustomProperty {
    AbstractCustomPropertyValue newValue(String value) {
        return new StringPropertyValue(value)
    }
}

@Entity
@SuppressWarnings('unused')
class StringPropertyValue extends AbstractCustomPropertyValue {

    String stringValue

    static constraints = {
        stringValue(nullable: true)
    }

    StringPropertyValue(String value) {
        this.stringValue = value
        this.valid = true
    }
}
