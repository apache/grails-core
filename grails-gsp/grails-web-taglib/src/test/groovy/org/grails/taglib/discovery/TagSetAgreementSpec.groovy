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

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The set of tags a build records and the set an application registers have to be the same set.
 *
 * <p>{@link TagDiscoveryRulesSpec} pins whether a given method is a tag. This pins the other half:
 * which members are asked about at all, and how far up the hierarchy. That half used to be written
 * once per side, and the two sides disagreed - a {@code Closure} tag inherited from a base class was
 * registered at runtime and missing from the index, so a namespace could be reported complete while
 * a working tag was unknown, which under strict checking fails a build over correct code.
 */
class TagSetAgreementSpec extends Specification {

    @Unroll
    void 'both views find the same tags when #description'() {
        when:
        Set<String> fromTree = fromTree(source, subject)

        and:
        Set<String> fromClass = fromClass(source, subject)

        then: 'neither side may know a tag the other does not'
        fromTree == fromClass

        and:
        fromTree == expected as Set

        where:
        description                          | subject      | expected                    | source
        'a method tag is declared'           | 'Subject'    | ['plain']                   | 'class Subject { def plain(Map attrs) { } }'
        'a closure tag is declared'          | 'Subject'    | ['legacy']                  | 'class Subject { Closure legacy = { Map attrs -> } }'
        'both kinds are declared'            | 'Subject'    | ['plain', 'legacy']         | 'class Subject { def plain(Map attrs) { }\n Closure legacy = { Map attrs -> } }'
        'a closure tag is inherited'         | 'Subject'    | ['common']                  | 'class BaseOne { Closure common = { Map attrs -> } }\nclass Subject extends BaseOne { }'
        'a closure tag is inherited twice'   | 'Subject'    | ['common']                  | 'class TopTwo { Closure common = { Map attrs -> } }\nclass MidTwo extends TopTwo { }\nclass Subject extends MidTwo { }'
        'a subclass redeclares a closure'    | 'Subject'    | ['common']                  | 'class BaseThree { Closure common = { Map attrs -> } }\nclass Subject extends BaseThree { Closure common = { Map attrs -> } }'
        'a method tag is inherited'          | 'Subject'    | []                          | 'class BaseFour { def plain(Map attrs) { } }\nclass Subject extends BaseFour { }'
        'a closure field is not a tag shape' | 'Subject'    | ['odd']                     | 'class Subject { Closure odd = { String a, int b -> } }'
        'a static closure is not a tag'      | 'Subject'    | []                          | 'class Subject { static Closure notATag = { Map attrs -> } }'
    }

    void 'an inherited closure tag is kept alongside the class own tags, not lost'() {
        when:
        Set<String> tags = fromTree('''
            class BaseFive { Closure common = { Map attrs -> } }
            class Subject extends BaseFive { def own(Map attrs) { } }
        ''', 'Subject')

        then: 'which is what the runtime registers, so the index may not omit it'
        tags == ['own', 'common'] as Set
    }

    private Set<String> fromTree(String source, String subject) {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.parameters = true
        CompilationUnit unit = new CompilationUnit(configuration)
        unit.addSource(SourceUnit.create('Subject.groovy', source))
        unit.compile(Phases.CANONICALIZATION)
        ClassNode classNode = unit.AST.classes.find { it.nameWithoutPackage == subject }
        assert classNode != null, "no class [${subject}] on the tree"
        TagDiscoveryRules.findTags(new AstTagLibraryView(classNode, configuration.parameters))
    }

    private Set<String> fromClass(String source, String subject) {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.parameters = true
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader, configuration)
        Class<?> compiled = null
        loader.parseClass(source, 'Subject.groovy')
        compiled = loader.loadClass(subject)
        TagDiscoveryRules.findTags(new ReflectedTagLibraryView(compiled))
    }
}
