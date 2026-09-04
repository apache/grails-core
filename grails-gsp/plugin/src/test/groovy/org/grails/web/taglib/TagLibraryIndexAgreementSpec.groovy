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

import org.grails.plugins.web.taglib.ApplicationTagLib
import org.grails.plugins.web.taglib.CountryTagLib
import org.grails.plugins.web.taglib.FormTagLib
import org.grails.plugins.web.taglib.FormatTagLib
import org.grails.plugins.web.taglib.JavascriptTagLib
import org.grails.plugins.web.taglib.PluginTagLib
import org.grails.plugins.web.taglib.UrlMappingTagLib
import org.grails.plugins.web.taglib.ValidationTagLib
import org.grails.taglib.TagMethodInvoker
import org.grails.taglib.index.TagLibraryIndex
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The compile-time index and the runtime tag resolution must describe the same set of tags.
 *
 * A tag present in the index but not resolvable at runtime would let a GSP compile against it and
 * then fail when the page renders, which is the failure mode that makes a static index dangerous.
 */
class TagLibraryIndexAgreementSpec extends Specification {

    static final List<Class<?>> FRAMEWORK_TAG_LIBRARIES = [
            ApplicationTagLib, ValidationTagLib, FormTagLib, FormatTagLib,
            JavascriptTagLib, PluginTagLib, UrlMappingTagLib, CountryTagLib
    ]

    TagLibraryIndex index = TagLibraryIndex.load(getClass().classLoader)

    void 'the index is populated from the compiled framework tag libraries'() {
        expect:
        !index.isEmpty()
        'g' in index.namespaces
    }

    @Unroll
    void 'index and runtime agree on the tags declared by #tagLibClass.simpleName'() {
        given: 'the tags the runtime will dispatch for this tag library'
        Set<String> runtimeTags = TagMethodInvoker.getInvokableTagMethodNames(tagLibClass) as Set

        and: 'the tags the compile-time index records for it'
        Set<String> indexedTags = index.getTagNames(namespaceOf(tagLibClass)).findAll { String tag ->
            index.lookup(namespaceOf(tagLibClass), tag).tagLibraryClassName() == tagLibClass.name
        } as Set

        expect: 'no tag is claimed statically that the runtime would refuse to dispatch'
        (indexedTags - runtimeTags).isEmpty()

        and: 'no runtime tag is missing from the index, which would silently fall back to dynamic'
        (runtimeTags - indexedTags).isEmpty()

        where:
        tagLibClass << FRAMEWORK_TAG_LIBRARIES
    }

    void 'well known tags resolve through the index to their declaring tag library'() {
        expect:
        index.lookup('g', tag)?.tagLibraryClassName() == declaringClass.name

        where:
        tag           | declaringClass
        'message'     | ValidationTagLib
        'fieldValue'  | ValidationTagLib
        'link'        | ApplicationTagLib
        'set'         | ApplicationTagLib
        'formatDate'  | FormatTagLib
    }

    void 'index and runtime agree on tags decided by annotation or parameter type'() {
        given:
        Set<String> runtimeTags = TagMethodInvoker.getInvokableTagMethodNames(IndexEdgeCaseTagLib) as Set
        Set<String> indexedTags = index.getTagNames('edge')

        expect: 'the two views are identical, including the awkward cases'
        indexedTags == runtimeTags

        and: 'specifically'
        'plain' in indexedTags
        'withBody' in indexedTags
        'annotated' in indexedTags       // @Tag overrides the signature rule
        !('excluded' in indexedTags)     // @NotATag overrides it the other way
        !('untyped' in indexedTags)      // untyped attrs is not Map-assignable, so not dispatchable
        !('helper' in indexedTags)
    }

    void 'an unknown tag is not resolved'() {
        expect:
        index.lookup('g', 'noSuchTagAnywhere') == null
        index.lookup('nosuchnamespace', 'message') == null
    }

    private static String namespaceOf(Class<?> tagLibClass) {
        def namespace = tagLibClass.declaredFields.find { it.name == 'namespace' && java.lang.reflect.Modifier.isStatic(it.modifiers) }
        if (!namespace) {
            return 'g'
        }
        namespace.accessible = true
        (namespace.get(null) ?: 'g').toString()
    }
}
