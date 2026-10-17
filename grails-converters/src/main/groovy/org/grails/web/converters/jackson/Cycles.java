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
package org.grails.web.converters.jackson;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.TokenStreamContext;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;

import org.grails.web.converters.Converter.CircularReferenceBehaviour;

/**
 * Tracks the objects a write has started and not finished, so that one reached again is not written again, and writes
 * what the JSON converter's circular reference behaviour selects in its place.
 *
 * @since 9.0
 */
final class Cycles {

    private static final String INSTANCES = Cycles.class.getName() + ".instances";

    private Cycles() {
    }

    /**
     * Where an object being written started: the nesting depth of its JSON object, and its path from the root in the
     * form the converter's {@code PATH} behaviour writes.
     */
    record Frame(int depth, String path) {
    }

    @SuppressWarnings("unchecked")
    static Map<Object, Frame> instances(SerializationContext context) {
        Map<Object, Frame> instances = (Map<Object, Frame>) context.getAttribute(INSTANCES);
        if (instances == null) {
            instances = new IdentityHashMap<>();
            context.setAttribute(INSTANCES, instances);
        }
        return instances;
    }

    /**
     * Where the object about to be written will be: a level below the context it is written into, at the position
     * the context is about to move to.
     */
    static Frame upcoming(JsonGenerator generator) {
        TokenStreamContext current = generator.streamWriteContext();
        StringBuilder path = new StringBuilder();
        for (TokenStreamContext context = current; context != null && !context.inRoot(); context = context.getParent()) {
            int index = context == current ? context.getEntryCount() : context.getCurrentIndex();
            path.insert(0, context.inArray() ? "[" + index + "]" : "." + context.currentName());
        }
        return new Frame(current.getNestingDepth() + 1, path.toString());
    }

    /** Writes what the circular reference behaviour selects for an object reached again. */
    static void write(Object value, Frame enclosing, JsonGenerator generator, CircularReferenceBehaviour behaviour) {
        String type = value.getClass().getName();
        switch (behaviour) {
            case EXCEPTION -> throw DatabindException.from(generator, "Circular Reference detected: class " + type);
            case INSERT_NULL -> generator.writeNull();
            case IGNORE -> {
                // An array element can be left out; a property name has already been written.
                if (!generator.streamWriteContext().inArray()) {
                    generator.writeNull();
                }
            }
            case PATH -> {
                generator.writeStartObject();
                generator.writeStringProperty("class", type);
                generator.writeStringProperty("ref", "root" + enclosing.path());
                generator.writeEndObject();
            }
            default -> {
                // One step up for each object or array between this value and the one it repeats
                int levels = Math.max(1, generator.streamWriteContext().getNestingDepth() - enclosing.depth());
                generator.writeStartObject();
                generator.writeStringProperty("_ref", String.join("/", Collections.nCopies(levels, "..")));
                generator.writeStringProperty("class", type);
                generator.writeEndObject();
            }
        }
    }
}
