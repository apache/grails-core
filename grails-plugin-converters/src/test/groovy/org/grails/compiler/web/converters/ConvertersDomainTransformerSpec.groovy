package org.grails.compiler.web.converters

import grails.converters.XML
import grails.persistence.Entity
import spock.lang.Specification

class ConvertersDomainTransformerSpec extends Specification {

    void "Test domain type conversion methods added at compile time"() {
        when:
            def xml = new ConvertMe(name:"Bob") as XML

        then:
            xml instanceof XML
    }
}

@Entity
class ConvertMe {
    String name
}

