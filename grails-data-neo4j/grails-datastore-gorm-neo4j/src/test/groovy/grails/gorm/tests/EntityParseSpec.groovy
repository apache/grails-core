package grails.gorm.tests

import grails.neo4j.Neo4jEntity
import org.codehaus.groovy.ast.ClassNode
import spock.lang.Specification

/**
 * Created by graemerocher on 05/07/16.
 */
class EntityParseSpec extends Specification {

    void "test parse entity at runtime"() {
        when:"An entity is parsed"
        def gcl = new GroovyClassLoader()
        Class cls = gcl.parseClass('''
@grails.gorm.annotation.Entity
class Foo {
    String title
}
''')

        then:"The class is correct"
        Neo4jEntity.isAssignableFrom(cls)
        new ClassNode(cls).methods
    }
}
