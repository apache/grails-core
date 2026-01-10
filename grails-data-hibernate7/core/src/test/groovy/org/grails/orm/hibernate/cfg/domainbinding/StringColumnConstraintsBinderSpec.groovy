package org.grails.orm.hibernate.cfg.domainbinding

import org.hibernate.mapping.Column
import org.grails.datastore.mapping.config.Property
import spock.lang.Specification

class StringColumnConstraintsBinderSpec extends Specification {

    StringColumnConstraintsBinder binder
    Column column
    Property mappedForm

    def setup() {
        binder = new StringColumnConstraintsBinder()
        column = new Column("test")
        mappedForm = Mock(Property)
    }

    def "should not set column length when neither is provided"() {
        given:
        mappedForm.getMaxSize() >> null
        mappedForm.getInList() >> null
        def originalLength = column.length

        when:
        binder.bindStringColumnConstraints(column, mappedForm)

        then:
        column.length == originalLength
    }

    def "should not set column length when empty list"() {
        given:
        mappedForm.getMaxSize() >> null
        mappedForm.getInList() >> []
        def originalLength = column.length

        when:
        binder.bindStringColumnConstraints(column, mappedForm)

        then:
        column.length == originalLength
    }

    def "should set column length when maxSize is provided"() {
        given:
        mappedForm.getMaxSize() >> 255
        mappedForm.getInList() >> null

        when:
        binder.bindStringColumnConstraints(column, mappedForm)

        then:
        column.length == 255
    }

    def "should set column length to longest inList value when maxSize is null"() {
        given:
        mappedForm.getMaxSize() >> null
        mappedForm.getInList() >> ["1","2","3","4"]

        when:
        binder.bindStringColumnConstraints(column, mappedForm)

        then:
        column.length == 4 // length of "very long string" - preserving original expectation
    }

    def "should set column length to longest valid int inList value when maxSize is null"() {
        given:
        mappedForm.getMaxSize() >> null
        mappedForm.getInList() >> ["4","string",Long.MAX_VALUE.toString(), null]

        when:
        binder.bindStringColumnConstraints(column, mappedForm)

        then:
        column.length == 4 // length of "very long string" - preserving original expectation
    }


    def "should prioritize maxSize over inList when both are present"() {
        given:
        mappedForm.getMaxSize() >> 1
        mappedForm.getInList() >> ["3"]

        when:
        binder.bindStringColumnConstraints(column, mappedForm)

        then:
        column.length == 1
    }

    def "should handle zero maxSize"() {
        given:
        mappedForm.getMaxSize() >> 0
        mappedForm.getInList() >> null
        def originalLength = column.length

        when:
        binder.bindStringColumnConstraints(column, mappedForm)

        then:
        column.length == originalLength
    }


    def "should handle Number subclasses for maxSize"() {
        given:
        mappedForm.getMaxSize() >> 50L // Long instead of Integer
        mappedForm.getInList() >> null

        when:
        binder.bindStringColumnConstraints(column, mappedForm)

        then:
        column.length == 50
    }
}