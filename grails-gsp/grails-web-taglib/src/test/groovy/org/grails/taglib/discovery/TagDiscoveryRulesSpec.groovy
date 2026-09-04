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
package org.grails.taglib.discovery

import java.lang.reflect.Method

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Runs one matrix of method shapes through both views of the discovery rules.
 *
 * <p>Each case is compiled once and classified twice: from the syntax tree, as a build does while a
 * tag library is compiled, and by reflection over the resulting class, as an application does at
 * startup. Both must reach the stated answer. A case where they differ is a tag that either compiles
 * and then fails to dispatch, or is reported as unknown while being perfectly callable.
 */
class TagDiscoveryRulesSpec extends Specification {

    @Unroll
    void 'the tree and the compiled class agree that #description'() {
        given: 'a tag library declaring the method under test'
        String source = """
            import grails.gsp.Tag
            import grails.gsp.NotATag
            class Subject {
                ${declaration}
            }
        """

        when: 'it is classified from the syntax tree'
        boolean fromTree = classifyFromTree(source, methodName)

        and: 'and by reflection over the compiled class'
        boolean fromClass = classifyFromClass(source, methodName)

        then: 'both views agree'
        fromTree == fromClass

        and: 'on the expected answer'
        fromTree == isTag

        where:
        description                                     | methodName    | isTag | declaration
        'a Map attrs parameter is a tag'                | 'plain'       | true  | 'def plain(Map attrs) { }'
        'attrs plus a Closure body is a tag'            | 'withBody'    | true  | 'def withBody(Map attrs, Closure body) { }'
        'a Closure body alone is a tag'                 | 'bodyOnly'    | true  | 'def bodyOnly(Closure body) { }'
        'a Map named something else is not a tag'       | 'renamed'     | false | 'def renamed(Map options) { }'
        'a Closure named something else is not a tag'   | 'renamedBody' | false | 'def renamedBody(Map attrs, Closure block) { }'
        'an untyped attrs parameter is not a tag'       | 'untyped'     | false | 'def untyped(attrs) { }'
        'a no argument method is not a tag'             | 'nullary'     | false | 'def nullary() { }'
        'an unrelated helper is not a tag'              | 'helper'      | false | 'String helper(String a, int b) { a }'
        'a static method is not a tag'                  | 'statik'      | false | 'static def statik(Map attrs) { }'
        'a private method is not a tag'                 | 'hidden'      | false | 'private def hidden(Map attrs) { }'
        'a getter is not a tag'                         | 'getThing'    | false | 'def getThing() { }'
        'a setter is not a tag'                         | 'setThing'    | false | 'void setThing(Map attrs) { }'
        'NotATag excludes a conventional shape'         | 'excluded'    | false | '@NotATag def excluded(Map attrs) { }'
        'Tag includes an unconventional shape'          | 'annotated'   | true  | '@Tag def annotated(Map attrs, String code) { code }'
        'a framework trait name is not a tag'           | 'withCodec'   | false | 'def withCodec(Map attrs) { }'
        'a defaulted trailing parameter is a tag'       | 'defaulted'   | true  | 'def defaulted(Map attrs, String extra = null) { }'
        // These three pin the differences between these rules and the discovery they replaced, which
        // excluded Object and GroovyObject members by signature rather than by name, checked that an
        // is* accessor returned boolean, and said nothing about $ in a name. None of them is reachable
        // by a tag that would otherwise have been discovered, and the shape check rejects them anyway -
        // but discovery is what a running application registers tag libraries from, so the answers are
        // stated here rather than left to be derived.
        'an Object member name is not a tag'            | 'equals'      | false | 'def equals(Map attrs) { }'
        'a non boolean is accessor is not a tag'        | 'isThing'     | false | 'String isThing() { null }'
        'a synthetic name is not a tag'                 | 'a$b'         | false | "def 'a\$b'(Map attrs) { }"
    }

    /**
     * Classifies straight from the tree, which is what the tag library index generation sees.
     */
    private boolean classifyFromTree(String source, String methodName) {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.parameters = true
        CompilationUnit unit = new CompilationUnit(configuration)
        unit.addSource(SourceUnit.create('Subject.groovy', source))
        unit.compile(Phases.CANONICALIZATION)
        ClassNode classNode = unit.firstClassNode
        MethodNode method = classNode.methods.find { it.name == methodName }
        assert method != null, "no method [${methodName}] on the tree"
        TagDiscoveryRules.isTagMethod(new AstTagMethodView(method, configuration.parameters))
    }

    /**
     * Classifies the compiled class, which is what an application does when it registers tag libraries.
     */
    private boolean classifyFromClass(String source, String methodName) {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.parameters = true
        def loader = new GroovyClassLoader(getClass().classLoader, configuration)
        Class<?> compiled = loader.parseClass(source, 'Subject.groovy')
        // Groovy expands a parameter default into overloads, so the shortest form is the callable one.
        List<Method> candidates = compiled.declaredMethods.findAll { it.name == methodName }
        assert candidates, "no method [${methodName}] on the compiled class"
        candidates.any { TagDiscoveryRules.isTagMethod(new ReflectedTagMethodView(it)) }
    }
}
