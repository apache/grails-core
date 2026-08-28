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
package org.grails.web.taglib

import grails.testing.web.taglib.TagLibUnitTest
import org.grails.plugins.web.taglib.ApplicationTagLib
import org.grails.taglib.CompiledTagInvocation
import org.grails.taglib.GrailsTagException
import org.grails.taglib.TagLibraryLookup
import spock.lang.Specification

/**
 * A tag whose namespace and name are already known is invoked as an ordinary method call rather than
 * through Groovy's dispatch, and must behave exactly as the dynamic route does.
 */
class CompiledTagInvocationSpec extends Specification implements TagLibUnitTest<ApplicationTagLib> {

    private TagLibraryLookup getLookup() {
        applicationContext.getBean(TagLibraryLookup)
    }

    void 'a tag that writes to the output returns what it wrote'() {
        when:
        Object output = CompiledTagInvocation.invoke(
                lookup, 'g', 'link', [controller: 'book', action: 'show'], null)

        then:
        output.toString() == applyTemplate('<g:link controller="book" action="show"/>')
    }

    void 'a tag called with a body receives it'() {
        given:
        Closure body = { 'inside' }

        when:
        Object output = CompiledTagInvocation.invoke(
                lookup, 'g', 'link', [controller: 'book'], body)

        then:
        output.toString().contains('inside')
    }

    void 'attributes may be omitted'() {
        expect: 'a null attribute map is treated as empty rather than failing'
        CompiledTagInvocation.invoke(lookup, 'g', 'link', null, { 'x' }) != null
    }

    void 'invoking without a tag library lookup is reported clearly'() {
        when:
        CompiledTagInvocation.invoke(null, 'g', 'link', [:], null)

        then:
        GrailsTagException e = thrown()
        e.message.contains('link')
    }

    void 'a tag the running application has not registered is reported as a missing method'() {
        when: 'the index described the namespace, so the call was resolved, but nothing registers this tag'
        CompiledTagInvocation.invoke(lookup, 'g', 'noSuchTagAnywhere', [:], null)

        then: 'which is what dispatching the call dynamically reported, and what callers catch'
        MissingMethodException e = thrown()
        e.method == 'noSuchTagAnywhere'
    }

    void 'a tag missing from a namespace nothing registers is reported the same way'() {
        when:
        CompiledTagInvocation.invokeArguments(lookup, 'noSuchNamespace', 'anyTag', [a: 1])

        then:
        thrown(MissingMethodException)
    }

    void 'arguments forwarded as written are read the same way dynamic dispatch reads them'() {
        given: 'the shapes TagLibraryMetaUtils.methodMissingForTagLib distinguishes'
        Map attrs = [controller: 'book', action: 'show']

        expect: 'a map alone is the attributes'
        CompiledTagInvocation.invokeArguments(lookup, 'g', 'link', attrs).toString() ==
                CompiledTagInvocation.invoke(lookup, 'g', 'link', attrs, null).toString()

        and: 'a map and a body are both taken'
        CompiledTagInvocation.invokeArguments(lookup, 'g', 'link', attrs, { 'inside' }).toString() ==
                CompiledTagInvocation.invoke(lookup, 'g', 'link', attrs, { 'inside' }).toString()

        and: 'a closure alone is the body'
        CompiledTagInvocation.invokeArguments(lookup, 'g', 'link', { 'inside' }).toString() ==
                CompiledTagInvocation.invoke(lookup, 'g', 'link', [:], { 'inside' }).toString()

        and: 'no arguments means no attributes and no body'
        CompiledTagInvocation.invokeArguments(lookup, 'g', 'link').toString() ==
                CompiledTagInvocation.invoke(lookup, 'g', 'link', [:], null).toString()
    }

    void 'an argument list no tag call takes is resolved as an ordinary method'() {
        when: 'two arguments whose first is not a map, which is not a shape a tag is called with'
        CompiledTagInvocation.invokeArguments(lookup, 'g', 'link', 'a', 'b')

        then: 'the dynamic path declines these rather than running the tag with nothing'
        thrown(MissingMethodException)
    }

    void 'more arguments than a tag call takes is resolved as an ordinary method'() {
        when:
        CompiledTagInvocation.invokeArguments(lookup, 'g', 'link', [controller: 'book'], 'body', 'extra')

        then:
        thrown(MissingMethodException)
    }

    void 'a single value that is neither a map nor a body is read under the tag name'() {
        when: 'a number cannot be a body, so it becomes an attribute named after the tag'
        String asAttribute = CompiledTagInvocation.invokeArguments(lookup, 'g', 'link', 5).toString()

        then: 'which is what dynamic dispatch does with such a call'
        asAttribute == CompiledTagInvocation.invoke(lookup, 'g', 'link', [link: 5], null).toString()

        and: 'and it is not rendered as the body would be'
        !asAttribute.contains('>5<')
    }

    void 'a single value that is text is the body'() {
        when:
        String asBody = CompiledTagInvocation.invokeArguments(lookup, 'g', 'link', 'inside').toString()

        then: 'a CharSequence is a body, as it is on the dynamic route'
        asBody == CompiledTagInvocation.invoke(lookup, 'g', 'link', [:], 'inside').toString()
        asBody.contains('inside')
    }
}
