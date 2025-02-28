package org.grails.plugins.converters.api

import spock.lang.Specification

class ConvertersApiSpec extends Specification {
    void 'Test converting an array to an interface type'() {
        given:
            def converter = new ConvertersApi()

        when:
            def someArray = ['One', 'Two', 'One', 'Three'] as String[]
            def someSet = converter.asType(someArray, Set)

        then:
            someSet instanceof Set
    }
}
