package org.grails.web.taglib

import grails.artefact.Artefact
import grails.testing.web.GrailsWebUnitTest
import spock.lang.Issue
import spock.lang.Specification

class ControllerTagLibMethodInheritanceSpec extends Specification implements GrailsWebUnitTest {

    void setupSpec() {
        mockTagLibs(FirstTagLib, SecondTagLib)
    }

    @Issue('GRAILS-10031')
    void 'Test calling an inherited tag which invokes a method overridden in the subclass'() {
        expect:
        'FirstTagLib.doSomething()' == applyTemplate('${first.myTag()}')
        'SecondTagLib.doSomething()' == applyTemplate('${second.myTag()}')
    }
}

@Artefact("TagLibrary")
class FirstTagLib {

    static namespace = 'first'
    
    protected String doSomething() {
        'FirstTagLib.doSomething()'
    }

    def myTag = { attrs ->
        out << doSomething()
    }
}

@Artefact("TagLibrary")
class SecondTagLib extends FirstTagLib {

    static namespace = 'second'
    
    protected String doSomething() {
        'SecondTagLib.doSomething()'
    }
}
