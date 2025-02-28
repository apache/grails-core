package org.grails.plugins.databinding

import grails.databinding.converters.ValueConverter
import grails.web.databinding.WebDataBinding
import org.grails.testing.GrailsUnitTest
import org.springframework.core.annotation.Order
import spock.lang.PendingFeature
import spock.lang.Specification

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Month
import java.time.ZoneId

@SuppressWarnings("GrMethodMayBeStatic")
class DataBindingConfigurationSpec extends Specification implements GrailsUnitTest, WebDataBinding {


    void setup() {
        defineBeans {
            myValueConverter1(MyValueConverter)
            myValueConverter2(MyValueConverter2)
            myDateValueConverter(MyDateValueConverter)
        }
    }

    void "test that grailsWebDataBinder exists"() {

        expect:
        grailsApplication.mainContext.containsBean("grailsWebDataBinder")
    }

    void "test custom ValueConverter are ordered if defined with @Order"() {

        when:
        Map source = ["name": "John Doe", "prop": "test"]
        Person person = new Person()
        person.setProperties(source)

        then:
        person.prop.value == "test2"

    }

    void "test customize data binding for the types which have standard ValueConverters using @Order"() {
        when:
        Map source = ["name": "John Doe", "prop": "test", "dob": "12031990"]
        Person person = new Person()
        person.setProperties(source)

        then:
        person.prop.value == "test2"
        person.dob
        Month.MARCH == getMonth(person.dob)
    }

    private Month getMonth(Date date) {
        date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().getMonth()
    }

    @Order(value = 1)
    static class MyDateValueConverter implements ValueConverter {

        @Override
        boolean canConvert(Object value) {
            return value instanceof String
        }

        @Override
        Object convert(Object value) {
            if (value) {
                DateFormat formatter = new SimpleDateFormat("ddMMyyyy")
                return formatter.parse((String) value)
            }
            return value
        }

        @Override
        Class<?> getTargetType() {
            Date.class
        }
    }

    @Order(value = 2)
    static class MyValueConverter implements ValueConverter {

        @Override
        boolean canConvert(Object value) {
            return value instanceof String
        }

        @Override
        Object convert(Object value) {
            new MyCustomProp((String) value + "1")
        }

        @Override
        Class<?> getTargetType() {
            MyCustomProp.class
        }
    }

    @Order(value = 1)
    static class MyValueConverter2 implements ValueConverter {

        @Override
        boolean canConvert(Object value) {
            return value instanceof String
        }

        @Override
        Object convert(Object value) {
            new MyCustomProp((String) value + "2")
        }

        @Override
        Class<?> getTargetType() {
            MyCustomProp.class
        }
    }

    static class MyCustomProp {
        String value

        MyCustomProp(String value) {
            this.value = value
        }
    }

    static class Person implements WebDataBinding {
        String name
        Date dob
        MyCustomProp prop

        Person() {}
    }
}
