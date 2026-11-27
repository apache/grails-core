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
import java.util.Iterator;
import java.util.Map;
import java.util.stream.BaseStream;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.TokenStreamContext;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ObjectWriter;
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
     * form the converter's {@code PATH} behaviour writes, where that behaviour may write it, or else {@code null}.
     */
    record Frame(int depth, String path) {
    }

    /**
     * A writer whose writes share the objects being written across the serialization contexts of a write: that of the
     * generator, which writes a value a serializer hands to {@code writePOJO}, and that of the write, where the
     * generator was created apart from the write, as Spring's Jackson converter creates it. The map is the writer's,
     * which each context reads where it has no map of its own.
     *
     * @param writer a writer for one write
     * @return the writer, with a map of its own
     */
    static ObjectWriter sharingInstances(ObjectWriter writer) {
        return writer.withAttribute(INSTANCES, new IdentityHashMap<Object, Frame>());
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
     *
     * @param generator the generator
     * @param withPath whether the path is kept, which only the {@code PATH} behaviour writes
     */
    static Frame upcoming(JsonGenerator generator, boolean withPath) {
        TokenStreamContext current = generator.streamWriteContext();
        if (!withPath) {
            return new Frame(current.getNestingDepth() + 1, null);
        }
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
                // An element of a list can be left out; a property name has already been written, or its position counts
                if (!inElementArray(generator)) {
                    generator.writeNull();
                }
            }
            case PATH -> {
                // the keys in the order the JSON converter writes them, as Grails 8 did
                generator.writeStartObject();
                generator.writeStringProperty("ref", "root" + enclosing.path());
                generator.writeStringProperty("class", type);
                generator.writeEndObject();
            }
            default -> {
                // One step up from the object or array the reference is written in for each one between it and the value it
                // repeats, or none, written ".", where it repeats the object it is written in
                int levels = generator.streamWriteContext().getNestingDepth() - enclosing.depth();
                generator.writeStartObject();
                generator.writeStringProperty("_ref", levels > 0 ? String.join("/", Collections.nCopies(levels, "..")) : ".");
                generator.writeStringProperty("class", type);
                generator.writeEndObject();
            }
        }
    }

    /**
     * Whether the generator is about to write an element of an array that a collection or an array is written as, which
     * can be left out, rather than a property of a bean written as an array, whose position has to be kept: Jackson starts
     * the array of a bean with the bean as its current value, and that of a collection, an array, an iterator or a stream
     * with the value itself. An array a serializer starts without a current value is taken for a collection's, and the
     * array of a container of another type, such as a Scala collection, for a bean's.
     */
    private static boolean inElementArray(JsonGenerator generator) {
        TokenStreamContext context = generator.streamWriteContext();
        if (!context.inArray()) {
            return false;
        }
        Object current = context.currentValue();
        return current == null || current instanceof Iterable || current instanceof Iterator ||
                current instanceof BaseStream || current.getClass().isArray();
    }
}
