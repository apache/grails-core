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
package grails.core

import spock.lang.Specification

class DefaultArtefactInfoSpec extends Specification {

    void 'a class added and completed is reflected in classes, grailsClasses and lookup maps'() {
        given:
        DefaultArtefactInfo info = new DefaultArtefactInfo()
        GrailsClass grailsClass = new DefaultGrailsClass(Foo)

        when:
        info.addGrailsClass(grailsClass)
        info.updateComplete()

        then:
        info.getClasses() == [Foo] as Class[]
        info.getGrailsClasses() == [grailsClass] as GrailsClass[]
        info.getGrailsClassesByName().get(Foo.name) == grailsClass
        info.getClassesByName().get(Foo.name) == Foo
        info.getGrailsClass(Foo.name) == grailsClass
        info.getGrailsClassByLogicalPropertyName(grailsClass.logicalPropertyName) == grailsClass
    }

    void 'adding the same class name twice replaces the earlier grails class but keeps a single entry'() {
        given:
        DefaultArtefactInfo info = new DefaultArtefactInfo()
        GrailsClass first = new DefaultGrailsClass(Foo)
        GrailsClass second = new DefaultGrailsClass(Foo)

        when:
        info.addGrailsClass(first)
        info.addGrailsClass(second)
        info.updateComplete()

        then:
        info.getGrailsClasses().length == 1
        info.getGrailsClasses()[0] == second
        info.getGrailsClassesByName().get(Foo.name) == second
    }

    void 'an overridable class is added to the front of the grails classes list'() {
        given:
        DefaultArtefactInfo info = new DefaultArtefactInfo()
        GrailsClass foo = new DefaultGrailsClass(Foo)
        GrailsClass bar = new DefaultGrailsClass(Bar)

        when:
        info.addGrailsClass(foo)
        info.addOverridableGrailsClass(bar)
        info.updateComplete()

        then:
        info.getGrailsClasses() == [bar, foo] as GrailsClass[]
    }

    void 'lookups for an unknown name return null'() {
        given:
        DefaultArtefactInfo info = new DefaultArtefactInfo()
        info.updateComplete()

        expect:
        info.getGrailsClass('unknown') == null
        info.getGrailsClassByLogicalPropertyName('unknown') == null
    }

    void 'handlerData is a plain mutable map available for handler-specific state'() {
        given:
        DefaultArtefactInfo info = new DefaultArtefactInfo()

        when:
        info.handlerData.put('key', 'value')

        then:
        info.handlerData.get('key') == 'value'
    }

    static class Foo {

    }

    static class Bar {

    }

}
