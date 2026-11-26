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

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX handler for parsing {@code META-INF/grails-plugin.xml} files.
 * Extracts {@code <type>} elements containing plugin class
 * fully-qualified names.
 */
class PluginXmlHandler extends DefaultHandler {

    private enum ParseState { IDLE, TYPE, RESOURCE }

    private ParseState state = ParseState.IDLE;
    private final List<String> pluginTypes = new ArrayList<>();
    private final List<String> pluginClasses = new ArrayList<>();
    private StringBuilder buffer = new StringBuilder();

    @Override
    public void startElement(String uri, String localName, String qName,
                             Attributes attributes) {
        if ("type".equals(localName) || "type".equals(qName)) {
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
                pluginTypes.add(buffer.toString().trim());
                break;
            case RESOURCE:
                pluginClasses.add(buffer.toString().trim());
                break;
            default:
                break;
        }
        state = ParseState.IDLE;
    }

    public List<String> getPluginClassNames() {
        return pluginTypes;
    }

    public List<String> getProvidedClasses() {
        return pluginClasses;
    }
}
