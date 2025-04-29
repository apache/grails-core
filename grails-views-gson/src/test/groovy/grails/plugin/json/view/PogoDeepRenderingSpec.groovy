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
import groovy.json.JsonSlurper
import spock.lang.Issue
import spock.lang.Specification

/**
 * Created by graemerocher on 13/04/16.
 */
class PogoDeepRenderingSpec  extends Specification implements JsonViewTest {

    @Issue('https://github.com/grails/grails-views/issues/18')
    void "Test deep rendering a POGO produces the correct json"() {
        given:"A deep graph of POGOs"
        def child = new Child2(name: "child")
        def parent = new Parent2(name: "parent", children: [child])
        child.parent = parent
        def grandParent = new GrandParent2(name: "grandParent", children: [parent])

        when:"The template is rendered"
        def result = render('''
import grails.plugin.json.view.GrandParent2

model {
    GrandParent2 grandParent
}

json g.render(grandParent, [deep: true])
''',[grandParent: grandParent])



        def json = result.json
        then:"The JSON is correct"
        json.name == 'grandParent'
        json.children[0].name == 'parent'
        json.children[0].children[0].name == 'child'
    }
}


class GrandParent2 {

    String name

    List<Parent2> children
}
class Parent2 {

    String name

    List<Child2> children
}

class Child2 {

    Parent2 parent
    String name

}
