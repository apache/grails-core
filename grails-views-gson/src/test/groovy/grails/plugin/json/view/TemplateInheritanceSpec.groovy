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
import grails.views.ViewException
import spock.lang.Specification

/**
 * Created by graemerocher on 24/05/16.
 */
class TemplateInheritanceSpec extends Specification implements JsonViewTest {

    void "test extending another template"() {
        when:

        def result = render(template:'child2', model:[player: new Player(name: "Cantona")])
        then:
        result.jsonText == '{"_links":{"self":{"href":"http://localhost:8080/player","hreflang":"en","type":"application/hal+json"}},"foo":"bar","name":"Cantona"}'
    }

    void "test extending another template that uses g.render(..)"() {

        when:
        def player = new Player(name: "Cantona")
        player.id = 1L
        def result = render(template:'child3', model:[player: player])
        then:
        result.jsonText == '{"id":1,"name":"Cantona"}'
    }

    void "test extending another template and rendering a JSON block"() {
        when:

        def result = render(template:'child4', model:[player: new Player(name: "Cantona")])
        then:
        result.jsonText == '{"_links":{"self":{"href":"http://localhost:8080/player","hreflang":"en","type":"application/hal+json"}},"foo":"bar","name":"Cantona"}'
    }

    void "test circular rendering is handled"() {
        when:
        def result = render(template:'circular/circular', model:[circular: new Circular(name: "Cantona")])

        then:
        notThrown(StackOverflowError)
        result.jsonText == '{"name":"Cantona"}'
    }

    void "test extending multiple templates"() {
        when:

        def result = render(template:'child2MultipleParents', model:[player: new Player(name: "Cantona")])
        then:
        result.jsonText == '{"_links":{"self":{"href":"http://localhost:8080/player","hreflang":"en","type":"application/hal+json"}},"foo":"bar","bar":"foo","name":"Cantona"}'
    }

    void "test extending multiple templates that uses g.render(..)"() {

        when:
        def player = new Player(name: "Cantona")
        player.id = 1L
        def result = render(template:'child3MultipleParents', model:[player: player])
        then:
        result.jsonText == '{"id":1,"bar":"foo","name":"Cantona"}'
    }

    void "test extending multiple templates and rendering a JSON block"() {
        when:

        def result = render(template:'child4MultipleParents', model:[player: new Player(name: "Cantona")])
        then:
        result.jsonText == '{"_links":{"self":{"href":"http://localhost:8080/player","hreflang":"en","type":"application/hal+json"}},"foo":"bar","bar":"foo","name":"Cantona"}'
    }


}
