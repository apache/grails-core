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
package org.grails.plugins.sitemesh3

import org.grails.taglib.TagMethodInvoker
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Verifies that the compile-time capture tags shipped by
 * {@link Sitemesh3LayoutTagLib} are defined as method handlers rather than
 * closure fields, so the GSP dispatch produced by {@code GrailsLayoutPreprocessor}
 * uses the faster reflective method-invocation path instead of cloning a closure
 * on every page render.
 */
class Sitemesh3LayoutTagLibSpec extends Specification {

    static final List<String> TAGS = [
            'captureHead',
            'captureBody',
            'captureTitle',
            'wrapTitleTag',
            'captureMeta',
            'captureContent',
            'parameter',
    ]

    @Unroll
    def "tag [#tag] is defined as a method handler, not a closure field"() {
        given:
        Sitemesh3LayoutTagLib tagLib = new Sitemesh3LayoutTagLib()

        expect: 'the tag is discovered as an invokable method'
        TagMethodInvoker.hasInvokableTagMethod(tagLib, tag)

        and: 'no closure field of the same name shadows it'
        TagMethodInvoker.getClosureTagProperty(tagLib, tag) == null

        where:
        tag << TAGS
    }

    def "every shipped tag is reported as an invokable method name"() {
        expect:
        TagMethodInvoker.getInvokableTagMethodNames(Sitemesh3LayoutTagLib).containsAll(TAGS)
    }
}
