/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.converters.jackson;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.TokenStreamContext;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import grails.core.support.proxy.EntityProxyHandler;
import grails.core.support.proxy.ProxyHandler;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.ManyToOne;
import org.grails.datastore.mapping.model.types.OneToOne;
import org.grails.web.converters.Converter.CircularReferenceBehaviour;

/** Serializes a mapped Grails domain type using its persistent metadata. */
final class GrailsDomainJsonSerializer extends ValueSerializer<Object> {

    /** Per-write attribute holding the domain objects being written, outermost first. */
    private static final Object IN_PROGRESS = GrailsDomainJsonSerializer.class.getName() + ".inProgress";

    private final PersistentEntity entity;
    private final ProxyHandler proxyHandler;
    private final boolean includeVersion;
    private final boolean includeClass;
    private final CircularReferenceBehaviour circularReferenceBehaviour;

    GrailsDomainJsonSerializer(PersistentEntity entity, ProxyHandler proxyHandler,
            boolean includeVersion, boolean includeClass, CircularReferenceBehaviour circularReferenceBehaviour) {
        this.entity = entity;
        this.proxyHandler = proxyHandler;
        this.includeVersion = includeVersion;
        this.includeClass = includeClass;
        this.circularReferenceBehaviour = circularReferenceBehaviour;
    }

    /**
     * Where an object being written started: the nesting depth of its JSON object, and its path
     * from the root in the form the legacy {@code PATH} behaviour writes.
     */
    private record Frame(int depth, String path) {
    }

    @Override
    public void serialize(Object value, JsonGenerator generator, SerializationContext context) throws JacksonException {
        Object unwrapped = proxyHandler.unwrapIfProxy(value);
        if (unwrapped != value && unwrapped.getClass() != entity.getJavaClass()) {
            context.writeValue(generator, unwrapped);
            return;
        }
        Map<Object, Frame> inProgress = inProgress(context);
        Frame enclosing = inProgress.get(unwrapped);
        if (enclosing != null) {
            // Reached again through a value written inside it, such as an embedded value that
            // points back at its owner. Associations are written as references and never get here.
            writeCircularReference(unwrapped, enclosing, generator);
            return;
        }
        BeanWrapper bean = new BeanWrapperImpl(unwrapped);
        JsonProjection projection = projection(context, unwrapped.getClass(), value.getClass());

        generator.writeStartObject();
        inProgress.put(unwrapped, new Frame(generator.streamWriteContext().getNestingDepth(),
                path(generator.streamWriteContext())));
        try {
            writeProperties(bean, generator, context, projection);
        }
        finally {
            inProgress.remove(unwrapped);
        }
        generator.writeEndObject();
    }

    private void writeProperties(BeanWrapper bean, JsonGenerator generator, SerializationContext context,
            JsonProjection projection) throws JacksonException {
        if (includeClass && shouldInclude(projection, "class")) {
            generator.writeStringProperty("class", entity.getName());
        }
        // An unsaved instance has neither yet; the legacy marshaller leaves them out rather than writing null
        writePropertyIfSet(entity.getIdentity(), bean, generator, context, projection);
        if (includeVersion) {
            writePropertyIfSet(entity.getVersion(), bean, generator, context, projection);
        }
        for (PersistentProperty property : entity.getPersistentProperties()) {
            if (!property.equals(entity.getVersion())) {
                writeProperty(property, bean, generator, context, projection);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Frame> inProgress(SerializationContext context) {
        Map<Object, Frame> inProgress = (Map<Object, Frame>) context.getAttribute(IN_PROGRESS);
        if (inProgress == null) {
            inProgress = new IdentityHashMap<>();
            context.setAttribute(IN_PROGRESS, inProgress);
        }
        return inProgress;
    }

    /** Writes what the legacy converter writes for the configured circular reference behaviour. */
    private void writeCircularReference(Object value, Frame enclosing, JsonGenerator generator) throws JacksonException {
        String type = value.getClass().getName();
        switch (circularReferenceBehaviour) {
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

    /** The path from the root to the object or array a context belongs to, as {@code .name[0]}. */
    private static String path(TokenStreamContext context) {
        StringBuilder path = new StringBuilder();
        for (TokenStreamContext parent = context.getParent(); parent != null && !parent.inRoot();
                parent = parent.getParent()) {
            path.insert(0, parent.inArray() ? "[" + parent.getCurrentIndex() + "]" : "." + parent.currentName());
        }
        return path.toString();
    }

    private void writePropertyIfSet(PersistentProperty property, BeanWrapper bean, JsonGenerator generator,
            SerializationContext context, JsonProjection projection) throws JacksonException {
        if (property != null && bean.getPropertyValue(property.getName()) != null) {
            writeProperty(property, bean, generator, context, projection);
        }
    }

    private void writeProperty(PersistentProperty property, BeanWrapper bean, JsonGenerator generator,
            SerializationContext context, JsonProjection projection) throws JacksonException {
        if (property == null || !shouldInclude(projection, property.getName())) {
            return;
        }
        Object propertyValue = bean.getPropertyValue(property.getName());
        generator.writeName(property.getName());
        if (property instanceof Association association && !association.isEmbedded() &&
                (property instanceof OneToOne || property instanceof ManyToOne)) {
            writeAssociationReference(propertyValue, association.getAssociatedEntity(), generator, context);
        }
        else if (property instanceof Association association && !association.isEmbedded() &&
                propertyValue instanceof Collection<?> collection) {
            generator.writeStartArray();
            for (Object associated : collection) {
                writeAssociationReference(associated, association.getAssociatedEntity(), generator, context);
            }
            generator.writeEndArray();
        }
        else if (property instanceof Association association && !association.isEmbedded() &&
                propertyValue instanceof Map<?, ?> map) {
            generator.writeStartObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                generator.writeName(String.valueOf(entry.getKey()));
                writeAssociationReference(entry.getValue(), association.getAssociatedEntity(), generator, context);
            }
            generator.writeEndObject();
        }
        else {
            context.writeValue(generator, propertyValue);
        }
    }

    private void writeAssociationReference(Object value, PersistentEntity associatedEntity, JsonGenerator generator,
            SerializationContext context) throws JacksonException {
        if (value == null || associatedEntity == null) {
            context.writeValue(generator, value);
            return;
        }
        PersistentProperty identity = associatedEntity.getIdentity();
        Object identifier = null;
        if (proxyHandler instanceof EntityProxyHandler entityProxyHandler) {
            identifier = entityProxyHandler.getProxyIdentifier(value);
        }
        Object unwrapped = proxyHandler.unwrapIfProxy(value);
        if (identifier == null && identity != null) {
            identifier = new BeanWrapperImpl(unwrapped).getPropertyValue(identity.getName());
        }
        generator.writeStartObject();
        if (includeClass) {
            generator.writeStringProperty("class", associatedEntity.getName());
        }
        if (identifier != null) {
            generator.writeName(identity == null ? "id" : identity.getName());
            context.writeValue(generator, identifier);
        }
        generator.writeEndObject();
    }

    /** The projection of this write, if it names the written object's type or the proxy's. */
    private static JsonProjection projection(SerializationContext context, Class<?> type, Class<?> proxyType) {
        Object projection = context.getAttribute(JsonProjection.ATTRIBUTE);
        return projection instanceof JsonProjection projected && projected.appliesTo(type, proxyType) != null ?
                projected : null;
    }

    private static boolean shouldInclude(JsonProjection projection, String property) {
        return projection == null || projection.includes(property);
    }
}
