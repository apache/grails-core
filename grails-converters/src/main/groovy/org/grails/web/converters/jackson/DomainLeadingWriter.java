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

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.VirtualBeanPropertyWriter;
import tools.jackson.databind.util.Annotations;

import org.grails.datastore.mapping.model.PersistentEntity;

/**
 * Writes the class name or the version of a domain class instance, or its id where Jackson has no property for it. They
 * are written ahead of the other properties, as the JSON converter writes them: the class name and the version only when
 * the rendering includes them, and the id and version only when they have a value, since an unsaved instance has
 * neither. They are written in the views of their property, or else in those the class names for its properties.
 *
 * @since 9.0
 */
final class DomainLeadingWriter extends VirtualBeanPropertyWriter {

    /** What the writer writes. */
    enum Kind {
        CLASS("class"), ID(null), VERSION("version");

        private final String property;

        Kind(String property) {
            this.property = property;
        }
    }

    private static final JsonInclude.Value NON_NULL = JsonInclude.Value.construct(JsonInclude.Include.NON_NULL,
            JsonInclude.Include.NON_NULL);

    private final Kind kind;

    private final PersistentEntity entity;

    private final DomainRendering rendering;

    DomainLeadingWriter(BeanPropertyDefinition definition, Annotations contextAnnotations, JavaType type, Kind kind,
            PersistentEntity entity, DomainRendering rendering, Class<?>[] views) {
        super(definition, contextAnnotations, type, null, null, null, NON_NULL, views);
        this.kind = kind;
        this.entity = entity;
        this.rendering = rendering;
    }

    private DomainLeadingWriter(DomainLeadingWriter base, PropertyName name) {
        super(base, name);
        this.kind = base.kind;
        this.entity = base.entity;
        this.rendering = base.rendering;
    }

    @Override
    protected BeanPropertyWriter _new(PropertyName newName) {
        return new DomainLeadingWriter(this, newName);
    }

    @Override
    protected Object value(Object bean, JsonGenerator generator, SerializationContext context) {
        DomainClassRendering current = rendering.of(context, bean);
        return switch (kind) {
            case CLASS -> bean.getClass().getName();
            case ID -> current.propertyValue(bean, entity.getIdentity());
            case VERSION -> current.propertyValue(bean, entity.getVersion());
        };
    }

    @Override
    public VirtualBeanPropertyWriter withConfig(MapperConfig<?> config, AnnotatedClass declaringClass,
            BeanPropertyDefinition propDef, JavaType type) {
        return this;
    }

    @Override
    public void serializeAsProperty(Object bean, JsonGenerator generator, SerializationContext context) throws Exception {
        DomainClassRendering current = rendering.of(context, bean);
        if (written(current) && included(current, bean, context)) {
            super.serializeAsProperty(bean, generator, context);
        }
    }

    @Override
    public void serializeAsElement(Object bean, JsonGenerator generator, SerializationContext context) throws Exception {
        DomainClassRendering current = rendering.of(context, bean);
        if (!written(current)) {
            // A class name or a version the rendering does not write has no position, as an instance written by a mapper
            // whose rendering never writes it has none
            return;
        }
        if (included(current, bean, context)) {
            super.serializeAsElement(bean, generator, context);
        }
        else {
            serializeAsOmittedElement(bean, generator, context);
        }
    }

    private boolean written(DomainClassRendering current) {
        return switch (kind) {
            case CLASS -> current.isIncludeClass();
            case ID -> true;
            case VERSION -> current.isIncludeVersion();
        };
    }

    private boolean included(DomainClassRendering current, Object bean, SerializationContext context) {
        String property = kind == Kind.ID ? entity.getIdentity().getName() : kind.property;
        return current.includes(bean, property, getName()) && JsonProjection.projects(context, bean, property, getName());
    }
}
