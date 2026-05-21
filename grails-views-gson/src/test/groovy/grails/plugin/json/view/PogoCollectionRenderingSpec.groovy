/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
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