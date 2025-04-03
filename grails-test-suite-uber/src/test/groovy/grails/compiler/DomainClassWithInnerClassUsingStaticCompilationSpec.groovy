package grails.compiler

import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import grails.validation.Validateable
import spock.lang.Issue
import spock.lang.Specification

class DomainClassWithInnerClassUsingStaticCompilationSpec extends Specification implements DomainUnitTest<SomeClass> {

    @Issue('https://github.com/apache/grails-core/issues/12461')
    void 'a domain class marked with @GrailsCompileStatic containing an inner class and a "constraints" block'() {
        expect: 'the configuration from the "constraints" closure is available'
            SomeClass.constraints instanceof Closure
            SomeClass.constraintsClosureCalled
    }

    @Issue('https://github.com/apache/grails-core/issues/12461')
    void 'a domain class marked with @GrailsCompileStatic containing an inner class and a "mapping" block'() {
        expect: 'the configuration from the "mapping" closure is available'
            SomeClass.mapping instanceof Closure
            SomeClass.mappingClosureCalled
    }

    @Issue('https://github.com/apache/grails-core/issues/12461')
    void 'a domain class marked with @GrailsCompileStatic containing an inner class and a "namedQueries" block'() {
        setup:
            SomeClass.getNamedQuery('test')

        expect: 'the configuration from the "namedQueries" closure is available'
            SomeClass.namedQueries instanceof Closure
            SomeClass.namedQueriesClosureCalled
    }
}

@GrailsCompileStatic
@Entity
class SomeClass implements Validateable {

    class SomeInnerClass {}

    SomeInnerClass foo

    static boolean constraintsClosureCalled = false
    static boolean mappingClosureCalled = false
    static boolean namedQueriesClosureCalled = false

    static constraints = {
        constraintsClosureCalled = true
    }

    static mapping = {
        mappingClosureCalled = true
    }

    static namedQueries = {
        namedQueriesClosureCalled = true
    }
}
