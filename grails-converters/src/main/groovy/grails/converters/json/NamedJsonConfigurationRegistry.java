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

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import org.grails.web.converters.jackson.GrailsJsonMapperCustomizer;

/**
 * Registry for request-safe named Jackson response configurations.
 *
 * @since 8.0
 */
public final class NamedJsonConfigurationRegistry {

    private final Supplier<JsonMapper> jsonMapper;
    private final ConcurrentMap<String, NamedJsonConfiguration> configurations = new ConcurrentHashMap<>();

    public NamedJsonConfigurationRegistry(JsonMapper jsonMapper) {
        this(() -> Objects.requireNonNull(jsonMapper, "jsonMapper"));
    }

    /**
     * @param jsonMapper supplies the mapper each configuration derives from, resolved when a writer
     * is first needed. Deferring it means the registry can be created before Jackson
     * auto-configuration has produced Spring Boot's mapper, and still derive from that mapper
     * rather than from a separately configured one.
     */
    public NamedJsonConfigurationRegistry(Supplier<JsonMapper> jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    public void register(String name, Consumer<NamedJsonConfiguration> customizer) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Named JSON configuration name must not be blank.");
        }
        Objects.requireNonNull(customizer, "customizer");
        NamedJsonConfiguration configuration = new NamedJsonConfiguration(name);
        customizer.accept(configuration);
        configurations.put(name, configuration);
    }

    public boolean contains(String name) {
        return configurations.containsKey(name);
    }

    public ObjectWriter writer(String name) {
        NamedJsonConfiguration configuration = configurations.get(name);
        if (configuration == null) {
            throw new IllegalArgumentException("Named JSON configuration [" + name + "] is not registered.");
        }
        JsonMapper mapper = this.jsonMapper.get();
        if (mapper == null) {
            throw new IllegalStateException("Named JSON configuration [" + name +
                    "] cannot be used: no JsonMapper is available. Spring Boot's Jackson " +
                    "auto-configuration normally provides one.");
        }
        return configuration.writer(mapper);
    }

    public String writeValueAsString(String name, Object value) {
        return writer(name).writeValueAsString(value);
    }

    public void writeValue(String name, Writer output, Object value) throws IOException {
        writer(name).writeValue(output, value);
    }

    /**
     * Writes with a per-response include/exclude projection applied on top of the named
     * configuration, so that selecting a configuration does not discard the projection.
     *
     * @param name the registered configuration
     * @param output the response writer
     * @param value the value to write
     * @param includes property names to include, or null for all
     * @param excludes property names to exclude, or null for none
     * @throws IOException if writing fails
     */
    public void writeValue(String name, Writer output, Object value,
            List<String> includes, List<String> excludes) throws IOException {
        ObjectWriter writer = writer(name);
        if (includes != null && !includes.isEmpty()) {
            writer = writer.withAttribute(GrailsJsonMapperCustomizer.INCLUDES_ATTRIBUTE, includes);
        }
        if (excludes != null && !excludes.isEmpty()) {
            writer = writer.withAttribute(GrailsJsonMapperCustomizer.EXCLUDES_ATTRIBUTE, excludes);
        }
        writer.writeValue(output, value);
    }
}
