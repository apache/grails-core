package grails.gorm.tests

import org.codehaus.groovy.ast.ClassNode
import spock.lang.Specification

/**
 * Created by graemerocher on 25/08/2016.
 */
class SingleCharPropertySpec extends Specification {
    void "Test parse GORM entity with single char properties"() {
        when:"A gorm entity is parsed"
        def cls = new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Person {
    String firstName
    String lastName
}

@Entity
class PersonLink {
    Person a
    Person b

    String toString() {
        "$a -> $b"
    }
}
''')
        then:"It is a valid class"
        new ClassNode(cls).methods
    }
}
