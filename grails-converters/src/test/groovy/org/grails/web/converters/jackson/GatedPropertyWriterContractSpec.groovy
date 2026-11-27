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
package org.grails.web.converters.jackson

import java.lang.reflect.Method
import java.lang.reflect.Modifier

import tools.jackson.databind.ser.AnyGetterWriter
import tools.jackson.databind.ser.bean.UnwrappingBeanPropertyWriter

import spock.lang.Specification

/**
 * The gated writers wrap Jackson's writer of the entries of a {@code @JsonAnyGetter} and its writer of an unwrapped
 * property, whose state, such as the accessor and the serializer of the entries, a wrapper cannot read. They forward
 * every method those classes declare, but those only the methods they forward call, to the writer they wrap: a method
 * a later release of Jackson adds to those classes fails this test, rather than a response, until the wrapper forwards
 * it, or it is found to need no forwarding.
 */
class GatedPropertyWriterContractSpec extends Specification {

    void 'the gated writer of the entries of a @JsonAnyGetter forwards every method AnyGetterWriter declares'() {
        expect: 'depositSchemaProperty, which writes no schema for the entries, reads no state, and the methods of an ObjectNode of entries only getAndSerialize and getAndFilter call'
        unforwarded(AnyGetterWriter, gated('AnyGetter'),
                ['depositSchemaProperty', '_verifyObjectNode', '_serializeObjectNodeEntries']) == []
    }

    void 'the gated writer of an unwrapped property forwards every method UnwrappingBeanPropertyWriter declares'() {
        expect: 'the methods only rename, serializeAsProperty and depositSchemaProperty call, and isUnwrapping, which is constant'
        unforwarded(UnwrappingBeanPropertyWriter, gated('Unwrapping'),
                ['_new', '_findAndAddDynamic', '_depositSchemaProperty', 'isUnwrapping']) == []
    }

    private static Class<?> gated(String name) {
        Class.forName("${GatedPropertyWriter.name}\$${name}")
    }

    /** The methods a Jackson class declares that the wrapper of its writers neither overrides nor needs to. */
    private static List<String> unforwarded(Class<?> jackson, Class<?> wrapper, List<String> forwardedThroughOthers) {
        jackson.declaredMethods.findAll { Method method ->
            !Modifier.isPrivate(method.modifiers) && !Modifier.isStatic(method.modifiers) && !method.synthetic &&
                    !method.bridge && !(method.name in forwardedThroughOthers) && !overrides(wrapper, method)
        }.collect { Method method -> method.toString() }.sort()
    }

    private static boolean overrides(Class<?> wrapper, Method method) {
        wrapper.declaredMethods.any { Method declared ->
            declared.name == method.name && declared.parameterTypes == method.parameterTypes
        }
    }
}
