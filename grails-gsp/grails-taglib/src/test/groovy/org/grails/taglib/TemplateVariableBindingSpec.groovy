package org.grails.taglib

import spock.lang.Specification

class TemplateVariableBindingSpec extends Specification {

    def "should be able to read variables of parent binding"() {
        when:
        def vars = [foo: 'bar']
        def parentBinding = new TemplateVariableBinding(vars)
        def childBinding = new TemplateVariableBinding(parentBinding)

        then:
        childBinding.hasVariable('foo')
        childBinding.getVariables().containsKey('foo')
        childBinding.getVariableNames().contains('foo')
        // getVariable() has to be called last, because found variables of parent contexts are cached.
        // subsequent calls to hasVariable() would hit the cached variable.
        childBinding.getVariable('foo') == 'bar'
    }
}
