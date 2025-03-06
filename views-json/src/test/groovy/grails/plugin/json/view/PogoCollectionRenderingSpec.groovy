package grails.plugin.json.view

import grails.plugin.json.view.test.JsonViewTest
import spock.lang.Specification

/**
 * Created by graemerocher on 22/04/16.
 */
class PogoCollectionRenderingSpec extends Specification implements JsonViewTest {

    static String OBJECT_VIEW = '''
model {
    Object object
}
json g.render(object)
'''

    void "Test render POGO that defines a set"() {
        when:"A pogo that defines a set is rendered"
        def result = render(OBJECT_VIEW, [object: new SetClass(name: "Bob", stuff: ['one', 'two'] as Set)])

        then:"The JSON is correct"
        result.json.name == "Bob"
        result.json.stuff.contains('one')
        result.json.stuff.contains('two')
    }

    void "Test render POGO that defines a generic set"() {
        when:"A pogo that defines a set is rendered"
        def result = render(OBJECT_VIEW, [object: new GenericSetClass(name: "Bob", stuff: ['one', 'two'] as Set)])

        then:"The JSON is correct"
        result.json.name == "Bob"
        result.json.stuff.contains('one')
        result.json.stuff.contains('two')
    }

    void "Test render a pogo with list of maps"() {
        when:
        def pogo = new GenericListClass(list: [[foo:'bar', bar: ['A','B']], [x:'y']])
        def renderResult = render(OBJECT_VIEW, [object: pogo])

        then:"The result is correct"
        renderResult.jsonText == '{"list":[{"foo":"bar","bar":["A","B"]},{"x":"y"}]}'
    }
    void "Test render a pogo with list of simple types"() {
        when:
        def pogo = new GenericListClass(list: [[foo:'bar', bar: new GenericListClass(list: ['A','B'])], [x:'y']])
        def renderResult = render(OBJECT_VIEW, [object: pogo])

        then:"The result is correct"
        renderResult.jsonText == '{"list":[{"foo":"bar","bar":{"list":["A","B"]}},{"x":"y"}]}'
    }

    void "Test render a pogo with list of pogos"() {
        when:
        def pogo = new GenericListClass(list: [new GenericPogoClass(name: 'A'),new GenericPogoClass(name: 'B')])
        def renderResult = render(OBJECT_VIEW, [object: pogo])

        then:"The result is correct"
        renderResult.jsonText == '{"list":[{"name":"A"},{"name":"B"}]}'
    }

    void "Test render a pogo with a map"() {
        when:
        def pogo = new GenericMapClass(map: [foo:'bar', bar: ['A','B']])
        def renderResult = render(OBJECT_VIEW, [object: pogo])

        then:"The result is correct"
        renderResult.jsonText == '{"map":{"foo":"bar","bar":["A","B"]}}'
    }

    void "Test render a pogo with a map that has a pogo"() {
        when:
        def pogo = new GenericMapClass(map: [foo:'bar', bar: new GenericListClass(list: ['A','B'])])
        def renderResult = render(OBJECT_VIEW, [object: pogo])

        then:"The result is correct"
        renderResult.jsonText == '{"map":{"foo":"bar","bar":{"list":["A","B"]}}}'
    }

}

class GenericPogoClass {
    String name
}

class SetClass {
    String name
    Set<String> stuff = []
}

class GenericSetClass {
    String name
    Set stuff = []
}

class GenericListClass {
    List list
}

class GenericMapClass {
    Map map
}