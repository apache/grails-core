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
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;

/**
 * Writes a proxy of a domain class instance as the instance it proxies, in its own serializer, so that a proxy narrowed
 * to a subclass is written as the subclass. A proxy of an instance that no longer exists, which has no target, is
 * written as {@code null}.
 *
 * @since 9.0
 */
final class DomainProxySerializer extends ValueSerializer<Object> {

    private final DomainRendering rendering;

    private final BeanProperty property;

    DomainProxySerializer(DomainRendering rendering) {
        this(rendering, null);
    }

    private DomainProxySerializer(DomainRendering rendering, BeanProperty property) {
        this.rendering = rendering;
        this.property = property;
    }

    /** The instance is written with the property the proxy is written for, so that what it is annotated with applies. */
    @Override
    public ValueSerializer<?> createContextual(SerializationContext context, BeanProperty property) {
        return property == this.property ? this : new DomainProxySerializer(rendering, property);
    }

    @Override
    public void serialize(Object value, JsonGenerator generator, SerializationContext context) {
        Object unwrapped = unwrapped(value, context);
        if (unwrapped == null) {
            generator.writeNull();
            return;
        }
        context.findPrimaryPropertySerializer(unwrapped.getClass(), property).serialize(unwrapped, generator, context);
    }

    @Override
    public void serializeWithType(Object value, JsonGenerator generator, SerializationContext context,
            TypeSerializer typeSerializer) {
        Object unwrapped = unwrapped(value, context);
        if (unwrapped == null) {
            generator.writeNull();
            return;
        }
        context.findPrimaryPropertySerializer(unwrapped.getClass(), property).serializeWithType(unwrapped, generator, context,
                typeSerializer);
    }

    private Object unwrapped(Object value, SerializationContext context) {
        Object unwrapped = rendering.of(context, value).unwrap(value);
        if (unwrapped == value) {
            throw new IllegalStateException("The proxy handler could not unwrap the proxy [" + value.getClass().getName() + "]");
        }
        if (context.getAttribute(JsonProjection.ATTRIBUTE) instanceof JsonProjection projection) {
            projection.narrowed(value.getClass(), unwrapped == null ? null : unwrapped.getClass());
        }
        return unwrapped;
    }
}
