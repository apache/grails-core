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

import java.util.Objects;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.impl.PropertySerializerMap;
import tools.jackson.databind.util.NameTransformer;

import org.springframework.util.ClassUtils;

import org.grails.datastore.mapping.model.PersistentProperty;

/**
 * Writes the id of a domain class instance with the value the rendering of the instance gives it, as the version and the
 * ids of associated instances are: the JSON converter's domain class marshaller reads them with {@code extractValue}. An
 * id the rendering gives as {@code null}, such as that of an unsaved instance, is not written, as the converter does not
 * write it.
 *
 * <p>The writer of the property writes the id itself, so that what Jackson, or another module, applies to the property
 * applies to it. A value the rendering gives in its place is written with the serializer, type id and inclusion of the
 * property.</p>
 *
 * @since 9.0
 */
final class DomainIdWriter extends BeanPropertyWriter {

    private final BeanPropertyWriter delegate;

    private final PersistentProperty identity;

    private final DomainRendering rendering;

    DomainIdWriter(BeanPropertyWriter delegate, PersistentProperty identity, DomainRendering rendering) {
        super(delegate);
        this.delegate = delegate;
        this.identity = identity;
        this.rendering = rendering;
    }

    @Override
    public BeanPropertyWriter rename(NameTransformer transformer) {
        BeanPropertyWriter renamed = delegate.rename(transformer);
        return renamed == delegate ? this : new DomainIdWriter(renamed, identity, rendering);
    }

    @Override
    public void assignSerializer(ValueSerializer<Object> serializer) {
        super.assignSerializer(serializer);
        delegate.assignSerializer(serializer);
    }

    @Override
    public void assignNullSerializer(ValueSerializer<Object> serializer) {
        super.assignNullSerializer(serializer);
        delegate.assignNullSerializer(serializer);
    }

    @Override
    public void assignTypeSerializer(TypeSerializer typeSerializer) {
        super.assignTypeSerializer(typeSerializer);
        delegate.assignTypeSerializer(typeSerializer);
    }

    @Override
    public void serializeAsProperty(Object bean, JsonGenerator generator, SerializationContext context) throws Exception {
        Object rendered = rendering.of(context, bean).propertyValue(bean, identity);
        if (rendered == null) {
            return;
        }
        if (Objects.equals(rendered, get(bean))) {
            delegate.serializeAsProperty(bean, generator, context);
            return;
        }
        ValueSerializer<Object> serializer = serializer(rendered, context);
        if (!suppressed(rendered, serializer, context)) {
            generator.writeName(_name);
            write(rendered, serializer, generator, context);
        }
    }

    @Override
    public void serializeAsElement(Object bean, JsonGenerator generator, SerializationContext context) throws Exception {
        Object rendered = rendering.of(context, bean).propertyValue(bean, identity);
        if (rendered == null) {
            serializeAsOmittedElement(bean, generator, context);
            return;
        }
        if (Objects.equals(rendered, get(bean))) {
            delegate.serializeAsElement(bean, generator, context);
            return;
        }
        ValueSerializer<Object> serializer = serializer(rendered, context);
        if (suppressed(rendered, serializer, context)) {
            serializeAsOmittedElement(bean, generator, context);
        }
        else {
            write(rendered, serializer, generator, context);
        }
    }

    /** The serializer of the property, for a value of its type, and otherwise the one Jackson finds for the value. */
    private ValueSerializer<Object> serializer(Object value, SerializationContext context) {
        if (_serializer != null && ClassUtils.resolvePrimitiveIfNecessary(_declaredType.getRawClass()).isInstance(value)) {
            return _serializer;
        }
        PropertySerializerMap serializers = _dynamicSerializers;
        ValueSerializer<Object> serializer = serializers.serializerFor(value.getClass());
        return serializer != null ? serializer : _findAndAddDynamic(serializers, value.getClass(), context);
    }

    /** Whether the inclusion of the property, such as {@code @JsonInclude(NON_EMPTY)}, leaves the value out. */
    private boolean suppressed(Object value, ValueSerializer<Object> serializer, SerializationContext context) {
        if (_suppressableValue == null) {
            return false;
        }
        return MARKER_FOR_EMPTY == _suppressableValue ? serializer.isEmpty(context, value) : _suppressableValue.equals(value);
    }

    private void write(Object value, ValueSerializer<Object> serializer, JsonGenerator generator, SerializationContext context) {
        if (_typeSerializer == null) {
            serializer.serialize(value, generator, context);
        }
        else {
            serializer.serializeWithType(value, generator, context, _typeSerializer);
        }
    }
}
