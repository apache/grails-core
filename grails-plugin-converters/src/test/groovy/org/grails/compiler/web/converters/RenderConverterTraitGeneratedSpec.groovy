package org.grails.compiler.web.converters

import grails.web.Action
import groovy.transform.Generated
import spock.lang.Specification

import java.lang.reflect.Method

class ControllerTraitGeneratedSpec extends Specification {

    void "test that all RenderConverter trait methods are marked as Generated"() {
        expect: "all RenderConverter methods are marked as Generated on implementation class"
        RenderConverterTrait.getMethods().each { Method traitMethod ->
            assert TestConverter.class.getMethod(traitMethod.name, traitMethod.parameterTypes).isAnnotationPresent(Generated)
        }
    }
}

class TestConverter implements RenderConverterTrait {
    
}

