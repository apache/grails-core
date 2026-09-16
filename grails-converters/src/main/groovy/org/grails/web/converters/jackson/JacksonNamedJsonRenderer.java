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

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import grails.converters.json.NamedJsonConfigurationRegistry;
import grails.web.render.NamedJsonRenderer;

/**
 * Jackson implementation shared by {@code render} and {@code respond}.
 *
 * @since 8.0
 */
public final class JacksonNamedJsonRenderer implements NamedJsonRenderer {

    private final NamedJsonConfigurationRegistry registry;

    public JacksonNamedJsonRenderer(NamedJsonConfigurationRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean contains(String name) {
        return registry.contains(name);
    }

    @Override
    public void render(String name, Object value, Writer writer) throws IOException {
        registry.writeValue(name, writer, value);
    }

    @Override
    public void render(String name, Object value, Writer writer,
            List<String> includes, List<String> excludes) throws IOException {
        registry.writeValue(name, writer, value, includes, excludes);
    }
}
