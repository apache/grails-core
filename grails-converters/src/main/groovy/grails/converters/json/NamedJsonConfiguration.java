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

import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * Defines serializers, a view, and writer attributes for one named Jackson response configuration.
 *
 * @since 8.0
 */
public final class NamedJsonConfiguration {

    private final SimpleModule module;
    private final Map<Object, Object> attributes = new LinkedHashMap<>();
    private Class<?> serializationView;
    private volatile ObjectWriter writer;

    NamedJsonConfiguration(String name) {
        this.module = new SimpleModule("grails-json-" + name);
    }

    public <T> NamedJsonConfiguration serializer(Class<T> type, ValueSerializer<? super T> serializer) {
        module.addSerializer(type, serializer);
        return this;
    }

    public NamedJsonConfiguration view(Class<?> view) {
        this.serializationView = view;
        return this;
    }

    public NamedJsonConfiguration attribute(Object name, Object value) {
        attributes.put(name, value);
        return this;
    }

    ObjectWriter createWriter(JsonMapper mapper) {
        JsonMapper configuredMapper = mapper.rebuild().addModule(module).build();
        ObjectWriter writer = serializationView == null ? configuredMapper.writer() :
                configuredMapper.writerWithView(serializationView);
        return attributes.isEmpty() ? writer : writer.withAttributes(attributes);
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
