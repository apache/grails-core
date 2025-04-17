package grails.plugin.json.view

import grails.gorm.annotation.Entity
import grails.plugin.json.view.test.JsonViewTest
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import spock.lang.Specification

/**
 * Created by jameskleeh on 2/9/17.
 */
class CompositeIdSpec extends Specification implements JsonViewTest {

    void setup() {
        mappingContext = new HibernateMappingContext()
        templateEngine.mappingContext = mappingContext
    }

    void "Test render domain object with a simple composite id"() {
        given:
        mappingContext.addPersistentEntities(CompositeSimple)
        CompositeSimple simple = new CompositeSimple(first: "x", second: "y", age: 99)

        when:
        def result = render('''
import grails.plugin.json.view.*

model {
    CompositeSimple simple
}
json g.render(simple)
''', [simple: simple])

        then:"The result is correct"
        result.jsonText == '{"first":"x","second":"y","age":99}'
    }

    void "Test render domain object with a complex composite id"() {
        given:
        mappingContext.addPersistentEntities(CompositeDomain, FirstId, SecondId)
        FirstId first = new FirstId(firstName: 'x')
        first.id = 1
        SecondId second = new SecondId(secondName: 'y')
        second.id = 2
        CompositeDomain domain = new CompositeDomain(first: first, second: second, age: 99)

        when:
        def result = render('''
import grails.plugin.json.view.*

model {
    CompositeDomain domain
}
json g.render(domain)
''', [domain: domain])

        then:"The result is correct"
        result.jsonText == '{"first":{"id":1},"second":{"id":2},"age":99}'

        when: "The template expands on an id property"
        result = render('''
import grails.plugin.json.view.*

model {
    CompositeDomain domain
}
json g.render(domain, [expand: ['first']])
''', [domain: domain])

        then:"The result is correct"
        result.jsonText == '{"first":{"id":1,"firstName":"x"},"second":{"id":2},"age":99}'

        when: "The template expands on an id property and excludes"
        result = render('''
import grails.plugin.json.view.*

model {
    CompositeDomain domain
}
json g.render(domain, [expand: ['first'], excludes: ['first.id']])
''', [domain: domain])

        then:"The result is correct"
        result.jsonText == '{"first":{"firstName":"x"},"second":{"id":2},"age":99}'

        when: "The template sets deep to true"
        result = render('''
import grails.plugin.json.view.*

model {
    CompositeDomain domain
}
json g.render(domain, [deep: true])
''', [domain: domain])

        then:"The result is correct"
        result.jsonText == '{"first":{"id":1,"firstName":"x"},"second":{"id":2,"secondName":"y"},"age":99}'
    }


    void "Test render domain object with a mixed composite id"() {
        given:
        mappingContext.addPersistentEntities(CompositeMixed, FirstId)
        FirstId first = new FirstId(firstName: 'x')
        first.id = 1
        CompositeMixed domain = new CompositeMixed(first: first, second: 'y', age: 99)

        when:
        def result = render('''
import grails.plugin.json.view.*

model {
    CompositeMixed domain
}
json g.render(domain)
''', [domain: domain])

        then:"The result is correct"
        result.jsonText == '{"first":{"id":1},"second":"y","age":99}'

        when: "The template expands on an id property"
        result = render('''
import grails.plugin.json.view.*

model {
    CompositeMixed domain
}
json g.render(domain, [expand: ['first']])
''', [domain: domain])

        then:"The result is correct"
        result.jsonText == '{"first":{"id":1,"firstName":"x"},"second":"y","age":99}'

        when: "The template expands on an id property and excludes"
        result = render('''
import grails.plugin.json.view.*

model {
    CompositeMixed domain
}
json g.render(domain, [expand: ['first'], excludes: ['second']])
''', [domain: domain])

        then:"The result is correct"
        result.jsonText == '{"first":{"id":1,"firstName":"x"},"age":99}'

        when: "The template sets deep to true"
        result = render('''
import grails.plugin.json.view.*

model {
    CompositeMixed domain
}
json g.render(domain, [deep: true])
''', [domain: domain])

        then:"The result is correct"
        result.jsonText == '{"first":{"id":1,"firstName":"x"},"second":"y","age":99}'
    }

}


@Entity
class CompositeSimple {

    String first
    String second

    Integer age

    static mapping = {
        id(composite: ['first', 'second'])
    }
}

@Entity
class FirstId {
    String firstName
}

@Entity
class SecondId {
    String secondName
}

@Entity
class CompositeDomain {
    FirstId first
    SecondId second

    Integer age

    static mapping = {
        id(composite: ['first', 'second'])
    }
}

@Entity
class CompositeMixed {
    FirstId first
    String second

    Integer age

    static mapping = {
        id(composite: ['first', 'second'])
    }
}