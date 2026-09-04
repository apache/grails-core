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
package org.grails.gsp

import groovy.transform.CompileStatic
import spock.lang.Specification

/**
 * A page with no tag library lookup has nothing to resolve an unqualified name against, and has to
 * say so as a missing method.
 *
 * <p>Resolving unqualified names moved off the metaclass and onto a real {@code methodMissing}.
 * Installing it onto the metaclass used to be skipped altogether when there was no lookup, so the
 * page simply had no {@code methodMissing} and an unresolved call reported a missing method. A real
 * method is always there, so the same condition has to be handled rather than reached.
 */
class GroovyPageMethodMissingSpec extends Specification {

    void 'the page under test really has no tag library lookup'() {
        expect: 'otherwise every case below would be exercising the resolved path'
        lookupOf(new LookupLessPage()) == null
    }

    void 'an unresolvable call on a page with no lookup reports a missing method'() {
        given: 'a page that was never given a tag library lookup'
        GroovyPage page = new LookupLessPage()

        when:
        callMethodMissing(page,'noSuchTag', [[code: 'x']] as Object[])

        then: 'not a null pointer from reaching through the absent lookup'
        MissingMethodException e = thrown()
        e.method == 'noSuchTag'
    }

    void 'the name and arguments are carried on the exception'() {
        given:
        GroovyPage page = new LookupLessPage()

        when:
        callMethodMissing(page,'anotherTag', ['sole'] as Object[])

        then:
        MissingMethodException e = thrown()
        e.method == 'anotherTag'
        e.arguments == ['sole'] as Object[]
    }

    void 'a call made with no arguments is reported the same way'() {
        given: 'the shape a page produces for ${bareTag()}'
        GroovyPage page = new LookupLessPage()

        when:
        callMethodMissing(page,'bareTag', [] as Object[])

        then:
        MissingMethodException e = thrown()
        e.method == 'bareTag'
        e.arguments.length == 0
    }

    void 'a null argument list is still a missing method rather than a null pointer'() {
        given:
        GroovyPage page = new LookupLessPage()

        when:
        callMethodMissing(page,'nullArgsTag', null)

        then: 'what it carries matters less than that it is not an NPE'
        MissingMethodException e = thrown()
        e.method == 'nullArgsTag'
    }

    /**
     * Calls the method rather than letting the metaclass route an explicit {@code methodMissing} call
     * somewhere else, so what is exercised is the method a page's own unresolved call reaches.
     */
    @CompileStatic
    private static Object callMethodMissing(GroovyPage page, String name, Object args) {
        page.methodMissing(name, args)
    }

    /**
     * Reads the lookup through the getter rather than as a property, since a page routes property
     * access through its own resolution.
     */
    @CompileStatic
    private static Object lookupOf(GroovyPage page) {
        page.getTagLibraryLookup()
    }

    private static class LookupLessPage extends GroovyPage {

        @Override
        String getGroovyPageFileName() {
            'lookupless.gsp'
        }

        @Override
        Object run() {
            null
        }
    }
}
