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

import java.util.function.BiConsumer;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.impl.PropertySerializerMap;
import tools.jackson.databind.util.NameTransformer;

/**
 * Writes a proxy of a domain class instance as the instance it proxies, in its own serializer, so that a proxy narrowed
 * to a subclass is written as the subclass. A proxy of an instance that no longer exists, which has no target, is
 * written as {@code null}, and left out of the object a {@code @JsonUnwrapped} property would write it into and by a
 * property that leaves out empty values.
 *
 * @since 9.0
 */
final class DomainProxySerializer extends ValueSerializer<Object> {

    private final DomainRendering rendering;

    private final BeanProperty property;

    /** The names of the properties of the instance, when it is written into the object that encloses it. */
    private final NameTransformer unwrapper;

    /** The serializers of the classes of the instances proxied, found once each, as Jackson's property writers keep them. */
    private volatile PropertySerializerMap serializers = PropertySerializerMap.emptyForProperties();

    DomainProxySerializer(DomainRendering rendering) {
        this(rendering, null, null);
    }

    private DomainProxySerializer(DomainRendering rendering, BeanProperty property, NameTransformer unwrapper) {
        this.rendering = rendering;
        this.property = property;
        this.unwrapper = unwrapper;
    }

    /** The instance is written with the property the proxy is written for, so that what it is annotated with applies. */
    @Override
    public ValueSerializer<?> createContextual(SerializationContext context, BeanProperty property) {
        return property == this.property ? this : new DomainProxySerializer(rendering, property, unwrapper);
    }

    /** The instance is written into the object that encloses it, as the serializer of the instance unwraps it. */
    @Override
    public ValueSerializer<Object> unwrappingSerializer(NameTransformer unwrapper) {
        return new DomainProxySerializer(rendering, property, unwrapper);
    }

    @Override
    public boolean isUnwrappingSerializer() {
        return unwrapper != null;
    }

    /**
     * A proxy of an instance that no longer exists is empty, as {@code null} is, and any other is as empty as the instance
     * it proxies, so that a property that leaves out empty values leaves it out.
     */
    @Override
    public boolean isEmpty(SerializationContext context, Object value) {
        Object unwrapped = rendering.of(context, value).unwrap(value);
        if (unwrapped == null) {
            return true;
        }
        return unwrapped != value && context.findValueSerializer(unwrapped.getClass()).isEmpty(context, unwrapped);
    }

    @Override
    public void serialize(Object value, JsonGenerator generator, SerializationContext context) {
        Object unwrapped = unwrapped(value, context);
        if (unwrapped == null) {
            writeNull(generator);
            return;
        }
        serializer(unwrapped, generator, context).serialize(unwrapped, generator, context);
    }

    @Override
    public void serializeWithType(Object value, JsonGenerator generator, SerializationContext context,
            TypeSerializer typeSerializer) {
        Object unwrapped = unwrapped(value, context);
        if (unwrapped == null) {
            writeNull(generator);
            return;
        }
        serializer(unwrapped, generator, context).serializeWithType(unwrapped, generator, context, typeSerializer);
    }

    private void writeNull(JsonGenerator generator) {
        if (unwrapper == null) {
            generator.writeNull();
        }
    }

    private ValueSerializer<Object> serializer(Object instance, JsonGenerator generator, SerializationContext context) {
        Class<?> type = instance.getClass();
        PropertySerializerMap found = serializers;
        ValueSerializer<Object> serializer = found.serializerFor(type);
        if (serializer == null) {
            serializer = context.findPrimaryPropertySerializer(type, property);
            if (unwrapper != null) {
                serializer = serializer.unwrappingSerializer(unwrapper);
            }
            serializers = found.newWith(type, serializer);
        }
        if (unwrapper != null && !serializer.isUnwrappingSerializer()) {
            // a serializer that cannot unwrap the instance writes it under the name of the property, as Jackson does
            generator.writeName(property.getName());
        }
        return serializer;
    }

    @SuppressWarnings("unchecked")
    private Object unwrapped(Object value, SerializationContext context) {
        Object unwrapped = rendering.of(context, value).unwrap(value);
        if (unwrapped == value) {
            throw new IllegalStateException("The proxy handler could not unwrap the proxy [" + value.getClass().getName() + "]");
        }
        if (context.getAttribute(JsonProjection.ATTRIBUTE) instanceof JsonProjection projection) {
            projection.narrowed(value.getClass(), unwrapped == null ? null : unwrapped.getClass());
        }
        if (unwrapped != null && context.getAttribute(DomainClassJacksonModule.STAND_INS) instanceof BiConsumer<?, ?> standIns) {
            ((BiConsumer<Object, Object>) standIns).accept(value, unwrapped);
        }
        return unwrapped;
    }
}
