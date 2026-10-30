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
package grails.converters.json;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.core.Version;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import org.grails.web.converters.jackson.DomainClassJacksonModule;

/**
 * Defines serializers, a view, and writer attributes for one named Jackson response configuration.
 *
 * @since 9.0
 */
public final class NamedJsonConfiguration {

    private final SimpleModule module;
    private final Map<Object, Object> attributes = new LinkedHashMap<>();
    private Class<?> serializationView;
    private volatile ObjectWriter writer;
    private boolean registered;

    NamedJsonConfiguration(String name) {
        // Its own registration id: a module named as the configuration would replace a module of the same name
        this.module = new SimpleModule("grails-json-" + name, Version.unknownVersion(), new Object());
    }

    /**
     * Writes the instances of a type with a serializer, ahead of the serializers of the application's mapper.
     *
     * @param type the type
     * @param serializer its serializer
     * @param <T> the type
     * @return this configuration
     */
    public <T> NamedJsonConfiguration serializer(Class<T> type, ValueSerializer<? super T> serializer) {
        assertMutable();
        module.addSerializer(type, serializer);
        return this;
    }

    /**
     * Writes with a Jackson view: a property annotated {@code @JsonView} is written only in the views it names, and one
     * that names none as the mapper's {@code MapperFeature.DEFAULT_VIEW_INCLUSION} decides.
     *
     * @param view the view
     * @return this configuration
     */
    public NamedJsonConfiguration view(Class<?> view) {
        assertMutable();
        this.serializationView = view;
        return this;
    }

    /**
     * Writes the associated instances of the domain class instances written in full, rather than as references of their
     * ids, as {@code JSON.use('deep')} rendered them. The registry has a configuration named {@code deep} that does.
     *
     * @return this configuration
     */
    public NamedJsonConfiguration deep() {
        return attribute(DomainClassJacksonModule.DEEP, Boolean.TRUE);
    }

    /**
     * Adds an attribute to the writes of this configuration, which serializers read from their context.
     *
     * @param name the name of the attribute
     * @param value its value
     * @return this configuration
     * @throws IllegalArgumentException for {@link DomainClassJacksonModule#RENDERINGS}, which only the JSON converter's
     *         writes can supply: the mapper of a named configuration decides whether it writes the class name and the
     *         version of a domain class before any write supplies a rendering
     */
    public NamedJsonConfiguration attribute(Object name, Object value) {
        assertMutable();
        if (DomainClassJacksonModule.RENDERINGS.equals(name)) {
            throw new IllegalArgumentException("[" + DomainClassJacksonModule.RENDERINGS + "] is the attribute of the " +
                    "JSON converter's writes; a named JSON configuration renders domain class instances with the " +
                    "grails.converters.json.domain settings, and a serializer or a projection of its own.");
        }
        attributes.put(name, value);
        return this;
    }

    void freeze() {
        registered = true;
    }

    private void assertMutable() {
        if (registered) {
            throw new IllegalStateException("Named JSON configurations cannot be changed after registration; register a replacement.");
        }
    }

    ObjectWriter createWriter(JsonMapper mapper) {
        JsonMapper configuredMapper = mapper.rebuild().addModule(module).build();
        ObjectWriter writer = serializationView == null ? configuredMapper.writer() :
                configuredMapper.writerWithView(serializationView);
        // added to the attributes the mapper gives every write, which they leave in place
        for (Map.Entry<Object, Object> attribute : attributes.entrySet()) {
            writer = writer.withAttribute(attribute.getKey(), attribute.getValue());
        }
        return writer;
    }

    /**
     * Returns the writer for this configuration, deriving it from the given mapper once.
     * Rebuilding a mapper is expensive, and this configuration is immutable after registration,
     * so the derived writer is cached rather than rebuilt for every response.
     */
    ObjectWriter writer(JsonMapper mapper) {
        ObjectWriter current = this.writer;
        if (current == null) {
            synchronized (this) {
                current = this.writer;
                if (current == null) {
                    current = createWriter(mapper);
                    this.writer = current;
                }
            }
        }
        return current;
    }
}
