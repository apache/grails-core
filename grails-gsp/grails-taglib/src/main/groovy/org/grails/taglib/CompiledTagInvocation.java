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
package org.grails.taglib;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import groovy.lang.Closure;
import groovy.lang.MissingMethodException;

import org.grails.taglib.encoder.OutputContext;
import org.grails.taglib.encoder.OutputContextLookupHelper;

/**
 * Invokes a tag whose namespace and name are known without going through Groovy's method dispatch.
 *
 * <p>Calling a tag as {@code g.message(code: 'x')} reaches the tag library through {@code
 * invokeMethod}, which means a dynamic call site in the caller's bytecode even when that caller is
 * statically compiled. The tag being called is fixed in the source, so once it has been resolved
 * against the tag library index there is nothing left to decide at runtime beyond which bean holds it.
 *
 * <p>This is the entry point such a call is expressed as: an ordinary method call taking the
 * namespace and name as arguments. It applies the same attribute and body handling, output capture,
 * encoding and return-object behaviour as the dynamic path, because both end at
 * {@link TagOutput#captureTagOutput}.
 *
 * @since 8.0.0
 */
public final class CompiledTagInvocation {

    private static final Object[] EMPTY_ARGUMENTS = new Object[0];

    private CompiledTagInvocation() {
    }

    /**
     * Invokes a tag with attributes and a body.
     *
     * @param lookup the tag libraries available to the caller
     * @param namespace the tag library namespace
     * @param tagName the tag name within that namespace
     * @param attrs the tag attributes, treated as empty when {@code null}
     * @param body the tag body as a closure or as text, or {@code null} when there is none
     * @return whatever the tag produces, which for a tag that writes to the output is its output
     */
    public static Object invoke(TagLibraryLookup lookup, String namespace, String tagName,
            Map<?, ?> attrs, Object body) {
        return invoke(lookup, namespace, tagName, attrs, body,
                OutputContextLookupHelper.lookupOutputContext());
    }

    /**
     * Invokes a tag against a known output context, for a caller that already has one to hand.
     *
     * @param lookup the tag libraries available to the caller
     * @param namespace the tag library namespace
     * @param tagName the tag name within that namespace
     * @param attrs the tag attributes, treated as empty when {@code null}
     * @param body the tag body as a closure or as text, or {@code null} when there is none
     * @param outputContext where the tag writes
     * @return whatever the tag produces
     */
    public static Object invoke(TagLibraryLookup lookup, String namespace, String tagName,
            Map<?, ?> attrs, Object body, OutputContext outputContext) {
        if (lookup == null) {
            throw new GrailsTagException("Tag [" + tagName + "] cannot be invoked without a tag library lookup");
        }
        Map<?, ?> attributes = attrs != null ? attrs : Collections.emptyMap();
        // A body may be a closure or the text a caller wrote directly, which the dynamic path accepted
        // through overloads that wrapped the text. Narrowing this to Closure would turn a string body
        // into a cast failure.
        Object tagBody = body instanceof CharSequence ? new TagOutput.ConstantClosure((CharSequence) body) : body;
        if (lookup.lookupTagLibrary(namespace, tagName) == null) {
            return dispatchUnregistered(lookup, namespace, tagName, attributes, tagBody);
        }
        return TagOutput.captureTagOutput(lookup, namespace, tagName, attributes, tagBody, outputContext);
    }

    /**
     * Hands a tag the index knows but the running application has not registered back to the dispatch
     * that would have run had the call never been resolved.
     *
     * <p>The index describes what a tag library declares when it is compiled, which is not the same
     * question as what a running application registers: a plugin can be excluded, a tag library can be
     * named in {@code nonEnhancedTagLibClasses}, and a unit test can mock some tag libraries and not
     * others. The dynamic path reported that as a {@link MissingMethodException}, and code written
     * around a tag call catches it or probes with {@code respondsTo}, so resolving the call must not
     * turn it into something else. Dispatching through the namespace rather than raising the exception
     * here also keeps the type it names the one that path named.
     */
    private static Object dispatchUnregistered(TagLibraryLookup lookup, String namespace, String tagName,
            Map<?, ?> attrs, Object body) {
        Object[] arguments;
        if (body != null) {
            arguments = new Object[] {attrs, body};
        }
        else if (!attrs.isEmpty()) {
            arguments = new Object[] {attrs};
        }
        else {
            arguments = EMPTY_ARGUMENTS;
        }
        return dispatchDynamically(lookup, namespace, tagName, arguments);
    }

    /**
     * Resolves a call the way it would have resolved had it never been rewritten.
     */
    private static Object dispatchDynamically(TagLibraryLookup lookup, String namespace, String tagName,
            Object[] arguments) {
        NamespacedTagDispatcher dispatcher = lookup.lookupNamespaceDispatcher(namespace);
        if (dispatcher == null) {
            throw new MissingMethodException(tagName, CompiledTagInvocation.class, arguments);
        }
        return dispatcher.invokeMethod(tagName, arguments);
    }

    /**
     * @return true when the arguments are a shape a tag call takes, which is the same question
     *         {@code TagLibraryMetaUtils.matchesTagShape} asks of a call it is about to dispatch
     */
    private static boolean matchesTagShape(Object[] arguments) {
        return switch (arguments.length) {
            case 0, 1 -> true;
            case 2 -> arguments[0] instanceof Map<?, ?>;
            default -> false;
        };
    }

    /**
     * Invokes a tag with whatever arguments the call was written with.
     *
     * <p>A tag call is written in more shapes than attributes and a body: with nothing, with a body
     * alone, or with a single value that the tag reads under its own name. Where the shape is not
     * evident in the source - a map held in a variable, say - the arguments are only known once they
     * have been evaluated, which is what this takes.
     *
     * @param lookup the tag libraries available to the caller
     * @param namespace the tag library namespace
     * @param tagName the tag name within that namespace
     * @param args the evaluated arguments, in the order they were written
     * @return whatever the tag produces
     */
    public static Object invokeArguments(TagLibraryLookup lookup, String namespace, String tagName,
            Object... args) {
        return invokeArgumentsInContext(lookup, namespace, tagName,
                OutputContextLookupHelper.lookupOutputContext(), args);
    }

    /**
     * Invokes a tag with whatever arguments the call was written with, against a known output context.
     *
     * @param lookup the tag libraries available to the caller
     * @param namespace the tag library namespace
     * @param tagName the tag name within that namespace
     * @param outputContext where the tag writes
     * @param args the evaluated arguments, in the order they were written
     * @return whatever the tag produces
     */
    public static Object invokeArgumentsInContext(TagLibraryLookup lookup, String namespace,
            String tagName, OutputContext outputContext, Object... args) {
        Object[] arguments = args != null ? args : EMPTY_ARGUMENTS;
        if (!matchesTagShape(arguments)) {
            // Not a shape a tag call takes. The dynamic path declines these and resolves the name as
            // an ordinary method -- an overload of the same name, or a missing method -- so reaching
            // the tag with no attributes and no body would run it where that would not have.
            return dispatchDynamically(lookup, namespace, tagName, arguments);
        }
        Map<?, ?> attrs = Collections.emptyMap();
        Object body = null;
        // The same shapes, in the same order, as the dynamic dispatch in
        // TagLibraryMetaUtils.methodMissingForTagLib reads them.
        switch (arguments.length) {
            case 0:
                break;
            case 1:
                if (arguments[0] instanceof Map<?, ?> map) {
                    attrs = map;
                }
                else if (arguments[0] instanceof Closure || arguments[0] instanceof CharSequence) {
                    body = arguments[0];
                }
                else {
                    Map<String, Object> named = new LinkedHashMap<>(1);
                    named.put(tagName, arguments[0]);
                    attrs = named;
                }
                break;
            case 2:
                attrs = (Map<?, ?>) arguments[0];
                body = arguments[1];
                break;
            default:
                break;
        }
        return invoke(lookup, namespace, tagName, attrs, body, outputContext);
    }
}
