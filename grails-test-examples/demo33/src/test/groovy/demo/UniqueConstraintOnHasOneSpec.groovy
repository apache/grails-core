package demo

import grails.persistence.Entity
import grails.testing.gorm.DataTest
import groovy.test.NotYetImplemented
import spock.lang.Specification

class UniqueConstraintOnHasOneSpec extends Specification implements DataTest {

    void setupSpec() {
        mockDomains Foo, Bar
    }

    void "Foo's name should be unique"() {
        given:
        def foo1 = new Foo(name: "FOO1")
        def bar = new Bar(name: "BAR1", foo: foo1)
        foo1.bar = bar
        foo1.save()

        expect:
        Foo.count() == 1

        when:
        def foo2 = new Foo(name: "FOO1")
        foo2.bar = new Bar(name: "BAR2", foo: foo2)
        foo2.save()

        then:
        foo2.hasErrors()
        foo2.errors['name']?.code == 'unique'
    }

    @NotYetImplemented
    void "Foo's bar should be unique, but..."() {
        given:
        def foo1 = new Foo(name: "FOO1")
        def bar = new Bar(name: "BAR", foo: foo1)
        foo1.bar = bar
        foo1.save()

        expect:
        Foo.count() == 1

        when:
        def foo2 = new Foo(name: "FOO2")
        foo2.bar = bar // using same Bar instance
        foo2.save()

        then:
        foo2.hasErrors()
        foo2.errors['bar']?.code == 'unique'
    }
}

@Entity
class Bar {

    String name

    Foo foo

    static constraints = {
    }
}

@Entity
class Foo {

    String name

    static hasOne = [bar: Bar]

    static constraints = {
        name unique: true
        bar unique: true
    }
}