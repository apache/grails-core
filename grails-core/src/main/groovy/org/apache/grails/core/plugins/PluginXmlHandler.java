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
package org.apache.grails.core.plugins;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Legacy SAX handler for reading deprecated {@code META-INF/grails-plugin.xml} descriptors
 * packaged by plugins built prior to Grails 9.
 *
 * @deprecated Legacy {@code META-INF/grails-plugin.xml} descriptors are deprecated in Grails 9.0
 *             and will be removed in Grails 10.0. Rebuild plugins against Grails 9 to generate
 *             {@code META-INF/grails/plugins.imports} and
 *             {@code META-INF/grails/plugins/<name>/plugin.properties}.
 */
@Deprecated(since = "9.0", forRemoval = true)
class PluginXmlHandler extends DefaultHandler {

    private enum ParseState { IDLE, TYPE, RESOURCE }

    private ParseState state = ParseState.IDLE;
    private @Nullable String pluginName;
    private @Nullable String pluginVersion;
    private final List<String> pluginTypes = new ArrayList<>();
    private final List<String> pluginClasses = new ArrayList<>();
    private StringBuilder buffer = new StringBuilder();

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        if ("plugin".equals(localName) || "plugin".equals(qName)) {
            pluginName = trimToNull(attributes.getValue("name"));
            pluginVersion = trimToNull(attributes.getValue("version"));
        } else if ("type".equals(localName) || "type".equals(qName)) {
            state = ParseState.TYPE;
            buffer = new StringBuilder();
        } else if ("resource".equals(localName) || "resource".equals(qName)) {
            state = ParseState.RESOURCE;
            buffer = new StringBuilder();
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (state == ParseState.TYPE || state == ParseState.RESOURCE) {
            buffer.append(ch, start, length);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        switch (state) {
            case TYPE:
                String typeName = buffer.toString().trim();
                if (!typeName.isEmpty()) {
                    pluginTypes.add(typeName);
                }
                break;
            case RESOURCE:
                String resourceName = buffer.toString().trim();
                if (!resourceName.isEmpty()) {
                    pluginClasses.add(resourceName);
                }
                break;
            default:
                break;
        }
        state = ParseState.IDLE;
    }

    public @Nullable String getPluginName() {
        return pluginName;
    }

    public @Nullable String getPluginVersion() {
        return pluginVersion;
    }

    public List<String> getPluginClassNames() {
        return pluginTypes;
    }

    public List<String> getProvidedClasses() {
        return pluginClasses;
    }

    private static @Nullable String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
