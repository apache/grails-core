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

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.PropertyWriter;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.databind.util.NameTransformer;

/**
 * A bean serializer that does not write an object again while it is being written, writing what its
 * {@link OnCycle} selects instead.
 *
 * @since 9.0
 */
final class CycleTracking extends ValueSerializer<Object> {

    /** Writes what stands for an object reached again while it is being written. */
    @FunctionalInterface
    interface OnCycle {

        void write(Object value, Cycles.Frame enclosing, JsonGenerator generator, SerializationContext context);
    }

    private final ValueSerializer<Object> delegate;

    private final OnCycle onCycle;

    private final Predicate<SerializationContext> paths;

    /**
     * Tracks the bean serializer a serializer wraps, if any.
     *
     * <p>Another module may have wrapped the bean serializer to take over values nested in it, as the JSON converter
     * does to render them itself. The tracker goes inside such a wrapper, since the wrapper hands a value to the converter
     * and the converter writes it again through the mapper: tracked outside, the second write would find the value already
     * being written.</p>
     *
     * <p>A bean serializer that writes object ids, for a class or a property annotated {@code @JsonIdentityInfo}, is not
     * tracked: it writes the id of an object it has written already, a reached again one included.</p>
     *
     * @param serializer a serializer, which may wrap a bean serializer
     * @param onCycle what to write for a value reached again
     * @param paths whether a value reached again may be written as its path, so that the path of each value is kept,
     *        asked once for each write, which it is the key of an attribute of, so the same one for every serializer
     * @return the serializer, with its bean serializer tracked, or {@code null} if it wraps none, or one that writes
     *         object ids
     */
    static ValueSerializer<?> around(ValueSerializer<?> serializer, OnCycle onCycle, Predicate<SerializationContext> paths) {
        if (serializer instanceof CycleTracking tracking) {
            return around(tracking.delegate, onCycle, paths);
        }
        if (serializer instanceof BeanSerializerBase) {
            return serializer.usesObjectId() ? null : new CycleTracking(serializer, onCycle, paths);
        }
        ValueSerializer<?> delegatee = serializer.getDelegatee();
        if (delegatee == null || delegatee == serializer) {
            return null;
        }
        ValueSerializer<?> tracked = around(delegatee, onCycle, paths);
        return tracked != null ? serializer.replaceDelegatee(tracked) : null;
    }

    @SuppressWarnings("unchecked")
    CycleTracking(ValueSerializer<?> delegate, OnCycle onCycle, Predicate<SerializationContext> paths) {
        this.delegate = (ValueSerializer<Object>) delegate;
        this.onCycle = onCycle;
        this.paths = paths;
    }

    /**
     * Whether the values of a write may be written as their paths, decided once for the write, as what decides it, such
     * as the configuration, does not change while it writes.
     */
    private boolean paths(SerializationContext context) {
        if (context.getAttribute(paths) instanceof Boolean decided) {
            return decided;
        }
        boolean decided = paths.test(context);
        context.setAttribute(paths, decided);
        return decided;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
        Map<Object, Cycles.Frame> instances = Cycles.instances(ctxt);
        Cycles.Frame enclosing = instances.get(value);
        if (enclosing != null) {
            onCycle.write(value, enclosing, gen, ctxt);
            return;
        }
        instances.put(value, Cycles.upcoming(gen, paths(ctxt)));
        try {
            delegate.serialize(value, gen, ctxt);
        }
        finally {
            instances.remove(value);
        }
    }

    @Override
    public void serializeWithType(Object value, JsonGenerator gen, SerializationContext ctxt, TypeSerializer typeSer) {
        Map<Object, Cycles.Frame> instances = Cycles.instances(ctxt);
        Cycles.Frame enclosing = instances.get(value);
        if (enclosing != null) {
            onCycle.write(value, enclosing, gen, ctxt);
            return;
        }
        instances.put(value, Cycles.upcoming(gen, paths(ctxt)));
        try {
            delegate.serializeWithType(value, gen, ctxt, typeSer);
        }
        finally {
            instances.remove(value);
        }
    }

    @Override
    public void resolve(SerializationContext ctxt) {
        delegate.resolve(ctxt);
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        ValueSerializer<?> contextual = delegate.createContextual(ctxt, property);
        if (contextual == delegate) {
            return this;
        }
        // a property annotated @JsonIdentityInfo makes the serializer write object ids
        return contextual.usesObjectId() ? contextual : new CycleTracking(contextual, onCycle, paths);
    }

    @Override
    public ValueSerializer<?> withIgnoredProperties(Set<String> ignoredProperties) {
        return tracking(delegate.withIgnoredProperties(ignoredProperties));
    }

    @Override
    public ValueSerializer<?> withFilterId(Object filterId) {
        return tracking(delegate.withFilterId(filterId));
    }

    @Override
    public ValueSerializer<?> withFormatOverrides(SerializationConfig config, JsonFormat.Value formatOverrides) {
        ValueSerializer<?> overridden = delegate.withFormatOverrides(config, formatOverrides);
        return overridden == null ? null : tracking(overridden);
    }

    @Override
    public ValueSerializer<Object> replaceDelegatee(ValueSerializer<?> delegatee) {
        return tracking(delegatee);
    }

    /** The bean serializer Jackson asks for, such as one that leaves out properties, tracked as this one is. */
    @SuppressWarnings("unchecked")
    private ValueSerializer<Object> tracking(ValueSerializer<?> serializer) {
        return serializer == delegate ? this : new CycleTracking(serializer, onCycle, paths);
    }

    @Override
    public ValueSerializer<Object> unwrappingSerializer(NameTransformer unwrapper) {
        // Properties written into an enclosing object have no object of their own to track
        return delegate.unwrappingSerializer(unwrapper);
    }

    @Override
    public boolean isUnwrappingSerializer() {
        return delegate.isUnwrappingSerializer();
    }

    @Override
    public boolean isEmpty(SerializationContext ctxt, Object value) {
        return delegate.isEmpty(ctxt, value);
    }

    @Override
    public boolean usesObjectId() {
        return delegate.usesObjectId();
    }

    @Override
    public Class<?> handledType() {
        return delegate.handledType();
    }

    @Override
    public ValueSerializer<?> getDelegatee() {
        return delegate;
    }

    @Override
    public Iterator<PropertyWriter> properties() {
        return delegate.properties();
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType type) {
        delegate.acceptJsonFormatVisitor(visitor, type);
    }
}
