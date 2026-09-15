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
import java.util.List;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import grails.core.support.proxy.EntityProxyHandler;
import grails.core.support.proxy.ProxyHandler;
import org.grails.core.util.IncludeExcludeSupport;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.ManyToOne;
import org.grails.datastore.mapping.model.types.OneToOne;

/** Serializes a mapped Grails domain type using its persistent metadata. */
final class GrailsDomainJsonSerializer extends ValueSerializer<Object> {

    // Stateless, and consulted once per property of every serialized object.
    private static final IncludeExcludeSupport<String> INCLUDE_EXCLUDE_SUPPORT = new IncludeExcludeSupport<>();

    private final PersistentEntity entity;
    private final ProxyHandler proxyHandler;
    private final boolean includeVersion;
    private final boolean includeClass;

    GrailsDomainJsonSerializer(PersistentEntity entity, ProxyHandler proxyHandler,
            boolean includeVersion, boolean includeClass) {
        this.entity = entity;
        this.proxyHandler = proxyHandler;
        this.includeVersion = includeVersion;
        this.includeClass = includeClass;
    }

    @Override
    public void serialize(Object value, JsonGenerator generator, SerializationContext context) throws JacksonException {
        Object unwrapped = proxyHandler.unwrapIfProxy(value);
        BeanWrapper bean = new BeanWrapperImpl(unwrapped);
        List<String> includes = properties(context, GrailsJsonMapperCustomizer.INCLUDES_ATTRIBUTE, unwrapped.getClass());
        List<String> excludes = properties(context, GrailsJsonMapperCustomizer.EXCLUDES_ATTRIBUTE, unwrapped.getClass());

        generator.writeStartObject();
        if (includeClass && shouldInclude(includes, excludes, "class")) {
            generator.writeStringProperty("class", entity.getName());
        }
        writeProperty(entity.getIdentity(), bean, generator, context, includes, excludes);
        if (includeVersion) {
            writeProperty(entity.getVersion(), bean, generator, context, includes, excludes);
        }
        for (PersistentProperty property : entity.getPersistentProperties()) {
            if (!property.equals(entity.getVersion())) {
                writeProperty(property, bean, generator, context, includes, excludes);
            }
        }
        generator.writeEndObject();
    }

    private void writeProperty(PersistentProperty property, BeanWrapper bean, JsonGenerator generator,
            SerializationContext context, List<String> includes, List<String> excludes) throws JacksonException {
        if (property == null || !shouldInclude(includes, excludes, property.getName())) {
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

    @SuppressWarnings("unchecked")
    private List<String> properties(SerializationContext context, String attribute, Class<?> type) {
        Object configured = context.getAttribute(attribute);
        if (configured instanceof Map<?, ?> configuredByType) {
            return (List<String>) configuredByType.get(type);
        }
        return configured instanceof List<?> list ? (List<String>) list : null;
    }

    private boolean shouldInclude(List<String> includes, List<String> excludes, String property) {
        return INCLUDE_EXCLUDE_SUPPORT.shouldInclude(includes, excludes, property);
    }
}
