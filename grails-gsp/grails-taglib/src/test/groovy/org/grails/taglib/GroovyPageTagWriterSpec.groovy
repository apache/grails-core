package org.grails.taglib


import spock.lang.Issue
import spock.lang.Specification

class GroovyPageTagWriterSpec extends Specification {

    @Issue("GRAILS-10666")
    def "toString returns string value"() {
        given:
            def groovyPageTagWriter = new GroovyPageTagWriter()
        when:
            groovyPageTagWriter.print("Hello world")
        then:
            groovyPageTagWriter.toString() == 'Hello world'
    }
}
