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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

import org.springframework.beans.BeanUtils;

/**
 * A per-write {@code includes}/{@code excludes} projection, as the {@code render} and {@code respond}
 * arguments request it.
 *
 * <p>As with the legacy converter, it applies to the properties of objects of the written value's
 * type, or of its elements' types when the value is a collection or an array. It applies to domain
 * objects and to beans Jackson writes property by property. Values with no properties to project,
 * such as maps and strings, are rejected before anything is written.</p>
 *
 * @since 9.0
 */
public final class JsonProjection {

    /** Writer attribute holding the projection of one write. */
    public static final String ATTRIBUTE = JsonProjection.class.getName();

    private static final Logger LOG = LoggerFactory.getLogger(JsonProjection.class);

    private final List<String> includes;
    private final List<String> excludes;
    private final Set<Class<?>> types;
    private final Set<Class<?>> applied = ConcurrentHashMap.newKeySet();

    private JsonProjection(List<String> includes, List<String> excludes, Set<Class<?>> types) {
        this.includes = includes;
        this.excludes = excludes;
        this.types = types;
    }

    /**
     * @param value the value to be written
     * @param includes property names to include, or null for all
     * @param excludes property names to exclude, or null for none
     * @return the projection, or null if neither list names a property
     * @throws IllegalArgumentException if the value, or an element of it, has no properties to project
     */
    public static JsonProjection of(Object value, List<String> includes, List<String> excludes) {
        boolean including = includes != null && !includes.isEmpty();
        boolean excluding = excludes != null && !excludes.isEmpty();
        if (!including && !excluding) {
            return null;
        }
        Set<Class<?>> types = new LinkedHashSet<>();
        if (value instanceof Iterable<?> elements) {
            elements.forEach(element -> addType(element, types));
        }
        else if (value instanceof Object[] elements) {
            for (Object element : elements) {
                addType(element, types);
            }
        }
        else {
            addType(value, types);
        }
        return new JsonProjection(including ? includes : null, excluding ? excludes : null, types);
    }

    /**
     * @return a module that applies the projection of a write to the beans Jackson writes property
     * by property. Domain objects apply it in the Grails domain serializer.
     */
    public static JacksonModule module() {
        return new ProjectionModule();
    }

    /**
     * @param mapper a mapper projections are to be written with
     * @return the mapper itself if it applies projections, otherwise a copy that does
     */
    public static JsonMapper supporting(JsonMapper mapper) {
        for (JacksonModule registered : mapper.registeredModules()) {
            if (ProjectionModule.NAME.equals(registered.getModuleName())) {
                return mapper;
            }
        }
        return mapper.rebuild().addModule(module()).build();
    }

    private static void addType(Object value, Set<Class<?>> types) {
        if (value == null) {
            return;
        }
        Class<?> type = value.getClass();
        if (value instanceof Map || value instanceof Collection || type.isArray() || BeanUtils.isSimpleValueType(type)) {
            throw new IllegalArgumentException("JSON includes/excludes apply to the properties of domain objects " +
                    "and beans, not to a value of type [" + type.getName() + "].");
        }
        types.add(type);
    }

    /**
     * @param candidates the type of an object being written; a proxy and the type it stands for
     * @return the first candidate the projection applies to, or null
     */
    Class<?> appliesTo(Class<?>... candidates) {
        for (Class<?> candidate : candidates) {
            if (types.contains(candidate)) {
                applied.add(candidate);
                return candidate;
            }
        }
        return null;
    }

    /**
     * @param names the names of one property: its bean name and, where Jackson renames it, its JSON name
     * @return whether the projection keeps the property
     */
    boolean includes(String... names) {
        boolean listed = includes == null;
        for (String name : names) {
            if (excludes != null && excludes.contains(name)) {
                return false;
            }
            listed |= includes != null && includes.contains(name);
        }
        return listed;
    }

    private static final class ProjectionModule extends SimpleModule {

        private static final String NAME = "grails-json-projection";

        private ProjectionModule() {
            super(NAME);
        }

        @Override
        public void setupModule(SetupContext context) {
            super.setupModule(context);
            context.addSerializerModifier(new ValueSerializerModifier() {
                @Override
                public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                        BeanDescription.Supplier beanDescription, List<BeanPropertyWriter> properties) {
                    // A projection names bean properties, as the legacy converter's did
                    Map<String, String> beanNames = new HashMap<>();
                    for (BeanPropertyDefinition definition : beanDescription.get().findProperties()) {
                        beanNames.put(definition.getName(), definition.getInternalName());
                    }
                    List<BeanPropertyWriter> projected = new ArrayList<>(properties.size());
                    for (BeanPropertyWriter property : properties) {
                        projected.add(new ProjectedPropertyWriter(property,
                                beanNames.getOrDefault(property.getName(), property.getName())));
                    }
                    return projected;
                }
            });
        }
    }

    /**
     * Logs the types this projection was requested for but not applied to, after a write: a
     * serializer of their own wrote them without going through their properties.
     */
    public void reportUnapplied() {
        Set<Class<?>> unapplied = new LinkedHashSet<>(types);
        unapplied.removeAll(applied);
        if (!unapplied.isEmpty()) {
            LOG.warn("JSON includes/excludes were not applied to {}: a serializer of their own writes them. " +
                    "Apply the projection in that serializer, or write a DTO.", unapplied);
        }
    }
}
