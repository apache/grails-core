/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.taglib

import groovy.transform.CompileStatic
import org.grails.taglib.encoder.OutputContext

/**
 * Dispatches invokeMethod calls to taglib method calls
 */
@CompileStatic
class TagLibNamespaceMethodDispatcher {
    private final String namespace
    private final TagLibraryLookup lookup
    private final OutputContext outputContext

    TagLibNamespaceMethodDispatcher(String ns, TagLibraryLookup lookup, OutputContext outputContext) {
        this.namespace = ns
        this.lookup = lookup
        this.outputContext = outputContext
    }

    @Override
    Object invokeMethod(String name, Object args) {
        invokeTagMethodCall(namespace, name, makeObjectArray(args))
    }

    private static Object[] makeObjectArray(Object args) {
        args instanceof Object[] ? (Object[]) args : [args] as Object[]
    }

    private Object invokeTagMethodCall(String namespace, String name, Object[] args) {
        Map attrs = null
        Object body = null
        for (Object arg : args) {
            if (arg instanceof Map) {
                attrs = Map.cast(arg)
            } else {
                body = arg
            }
        }
        if (attrs == null) {
            attrs = [:]
        }
        invokeTagMethodCall(namespace, name, attrs, body)
    }

    private Object invokeTagMethodCall(String namespace, String name, Map attrs, Object body) {
        TagOutput.captureTagOutput(lookup, namespace, name, attrs, body, outputContext)
    }
}
