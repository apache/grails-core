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

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.util.NameTransformer;

/**
 * Writes a property of a bean as Jackson writes it, when every gate of the write includes it: the JSON converter's
 * includes and excludes, the projection of a response, and the rendering of a domain class instance. Each module that
 * gates a property adds its gate to the same writer.
 *
 * @since 9.0
 */
final class GatedPropertyWriter extends BeanPropertyWriter {

    /** Whether a property of a bean is written. */
    @FunctionalInterface
    interface Gate {

        /**
         * @param bean the bean being written
         * @param context the context of the write
         * @param writer the writer of the property, which names it as it is written
         * @return whether the property is written
         */
        boolean includes(Object bean, SerializationContext context, BeanPropertyWriter writer) throws Exception;

        /**
         * @param other another gate
         * @return a gate that includes a property when this gate and the other one do
         */
        default Gate and(Gate other) {
            return (bean, context, writer) -> includes(bean, context, writer) && other.includes(bean, context, writer);
        }
    }

    private final BeanPropertyWriter delegate;

    private final Gate gate;

    private GatedPropertyWriter(BeanPropertyWriter delegate, Gate gate) {
        super(delegate);
        this.delegate = delegate;
        this.gate = gate;
    }

    /**
     * @param writer a property writer, which may be gated already
     * @param gate a gate to add
     * @return a writer that writes the property when its gates, and this one, include it
     */
    static BeanPropertyWriter gated(BeanPropertyWriter writer, Gate gate) {
        if (writer instanceof GatedPropertyWriter gated) {
            return new GatedPropertyWriter(gated.delegate, gated.gate.and(gate));
        }
        return new GatedPropertyWriter(writer, gate);
    }

    @Override
    public BeanPropertyWriter rename(NameTransformer transformer) {
        BeanPropertyWriter renamed = delegate.rename(transformer);
        return renamed == delegate ? this : new GatedPropertyWriter(renamed, gate);
    }

    @Override
    public void assignSerializer(ValueSerializer<Object> serializer) {
        delegate.assignSerializer(serializer);
    }

    @Override
    public void assignNullSerializer(ValueSerializer<Object> serializer) {
        delegate.assignNullSerializer(serializer);
    }

    @Override
    public void assignTypeSerializer(TypeSerializer typeSerializer) {
        delegate.assignTypeSerializer(typeSerializer);
    }

    @Override
    public ValueSerializer<Object> getSerializer() {
        return delegate.getSerializer();
    }

    @Override
    public boolean hasSerializer() {
        return delegate.hasSerializer();
    }

    @Override
    public boolean hasNullSerializer() {
        return delegate.hasNullSerializer();
    }

    @Override
    public boolean isUnwrapping() {
        return delegate.isUnwrapping();
    }

    @Override
    public void serializeAsProperty(Object bean, JsonGenerator generator, SerializationContext context) throws Exception {
        if (gate.includes(bean, context, this)) {
            delegate.serializeAsProperty(bean, generator, context);
        }
    }

    @Override
    public void serializeAsElement(Object bean, JsonGenerator generator, SerializationContext context) throws Exception {
        if (gate.includes(bean, context, this)) {
            delegate.serializeAsElement(bean, generator, context);
        }
        else {
            delegate.serializeAsOmittedElement(bean, generator, context);
        }
    }

    @Override
    public void depositSchemaProperty(JsonObjectFormatVisitor visitor, SerializationContext context) {
        delegate.depositSchemaProperty(visitor, context);
    }
}
