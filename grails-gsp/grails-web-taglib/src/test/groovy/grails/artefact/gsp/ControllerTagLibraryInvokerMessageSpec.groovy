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
package grails.artefact.gsp

import groovy.transform.CompileStatic

import org.grails.taglib.GroovyPageAttributes
import org.grails.taglib.TagLibraryLookup

import spock.lang.Specification

class ControllerTagLibraryInvokerMessageSpec extends Specification {

    void 'message(Map) dispatches to the message tag of the default namespace'() {
        given:
        RecordingTagLib tagLib = new RecordingTagLib()
        DefaultNamespaceInvoker invoker = new DefaultNamespaceInvoker()
        invoker.tagLibraryLookup = new StubTagLibraryLookup(tagLibsByNamespace: [g: tagLib])

        when:
        def result = invoker.message(code: 'default.created.message', args: ['Book', '1'])

        then:
        result == 'resolved'
        tagLib.invokedName == 'message'
        tagLib.invokedAttrs.code == 'default.created.message'
        tagLib.invokedAttrs.args == ['Book', '1']

        and: 'the tag sees function-call semantics, exactly as dynamic controller dispatch produced'
        tagLib.invokedAttrs instanceof GroovyPageAttributes
        !((GroovyPageAttributes) tagLib.invokedAttrs).gspTagSyntaxCall
    }

    void 'a no-arg message() call routes through the same dispatch with empty attributes'() {
        given:
        RecordingTagLib tagLib = new RecordingTagLib()
        DefaultNamespaceInvoker invoker = new DefaultNamespaceInvoker()
        invoker.tagLibraryLookup = new StubTagLibraryLookup(tagLibsByNamespace: [g: tagLib])

        when: 'the single-Map-parameter method is invoked without arguments'
        def result = invoker.message()

        then: 'dispatch succeeds with empty attributes, matching the previous methodMissing behavior'
        result == 'resolved'
        tagLib.invokedAttrs.isEmpty()
    }

    void 'message(Map) prefers the declared taglib namespace and falls back to the default'() {
        given: 'a tag library registered only under the default namespace'
        RecordingTagLib tagLib = new RecordingTagLib()
        CustomNamespaceInvoker invoker = new CustomNamespaceInvoker()
        invoker.tagLibraryLookup = new StubTagLibraryLookup(tagLibsByNamespace: [g: tagLib])

        when:
        def result = invoker.message(code: 'x')

        then: 'the default-namespace tag library handles the call'
        result == 'resolved'
        tagLib.invokedName == 'message'

        when: 'the custom namespace also provides the tag'
        RecordingTagLib customTagLib = new RecordingTagLib()
        invoker.tagLibraryLookup = new StubTagLibraryLookup(tagLibsByNamespace: [custom: customTagLib, g: tagLib])
        invoker.message(code: 'y')

        then: 'the declared namespace wins, mirroring methodMissing dispatch order'
        customTagLib.invokedName == 'message'
        customTagLib.invokedAttrs.code == 'y'
    }

    void 'message(Map) throws MissingMethodException when no tag library provides the tag'() {
        given:
        DefaultNamespaceInvoker invoker = new DefaultNamespaceInvoker()
        invoker.tagLibraryLookup = new StubTagLibraryLookup(tagLibsByNamespace: [:])

        when:
        invoker.message(code: 'x')

        then: 'the failure mode matches dynamic dispatch via methodMissing'
        thrown(MissingMethodException)
    }

    void 'message(Map) resolves under static compilation'() {
        given:
        RecordingTagLib tagLib = new RecordingTagLib()
        StaticController controller = new StaticController()
        controller.tagLibraryLookup = new StubTagLibraryLookup(tagLibsByNamespace: [g: tagLib])

        when: 'a statically compiled controller-style class invokes the canonical idiom'
        def result = controller.created()

        then:
        result == 'resolved'
        tagLib.invokedAttrs.code == 'default.created.message'
    }
}

class DefaultNamespaceInvoker implements ControllerTagLibraryInvoker {
}

class CustomNamespaceInvoker implements ControllerTagLibraryInvoker {
    @Override
    String getTaglibNamespace() { 'custom' }
}

/**
 * Mirrors how a {@code @GrailsCompileStatic} controller calls {@code message(...)} — this class
 * only compiles when the trait declares a real {@code message(Map)} method, guarding the
 * static-resolution contract.
 */
@CompileStatic
class StaticController implements ControllerTagLibraryInvoker {
    Object created() {
        message(code: 'default.created.message', args: ['Book', '1'])
    }
}

class StubTagLibraryLookup extends TagLibraryLookup {
    Map<String, GroovyObject> tagLibsByNamespace = [:]

    @Override
    GroovyObject lookupTagLibrary(String namespace, String tagName) {
        tagName == 'message' ? tagLibsByNamespace[namespace] : null
    }

    @Override
    boolean doesTagReturnObject(String namespace, String tagName) {
        // mirrors ValidationTagLib declaring message in returnObjectForTags
        tagName == 'message'
    }

    @Override
    Map<String, Object> getEncodeAsForTag(String namespace, String tagName) {
        null
    }
}

class RecordingTagLib {
    String invokedName
    Map invokedAttrs

    Closure message = { Map attrs ->
        invokedName = 'message'
        invokedAttrs = attrs
        'resolved'
    }
}
