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
package org.grails.web.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.PrettyPrinter;
import tools.jackson.core.TokenStreamContext;
import tools.jackson.core.util.Instantiatable;

/**
 * Indents JSON as Grails 8 did: every key and array element on its own line, a key followed by {@code ": "}, and an
 * array that is not an array element on a line of its own.
 */
final class GrailsPrettyPrinter implements PrettyPrinter, Instantiatable<GrailsPrettyPrinter> {

    private final String indent;

    private final String newline;

    private int level;

    GrailsPrettyPrinter(String indent, String newline) {
        this.indent = indent;
        this.newline = newline;
    }

    @Override
    public GrailsPrettyPrinter createInstance() {
        return new GrailsPrettyPrinter(indent, newline);
    }

    @Override
    public void writeRootValueSeparator(JsonGenerator g) {
    }

    @Override
    public void writeStartObject(JsonGenerator g) {
        g.writeRaw('{');
        level++;
    }

    @Override
    public void beforeObjectEntries(JsonGenerator g) {
        newline(g);
    }

    @Override
    public void writeObjectEntrySeparator(JsonGenerator g) {
        g.writeRaw(',');
        newline(g);
    }

    @Override
    public void writeObjectNameValueSeparator(JsonGenerator g) {
        g.writeRaw(": ");
    }

    @Override
    public void writeEndObject(JsonGenerator g, int nrOfEntries) {
        level--;
        newline(g);
        g.writeRaw('}');
    }

    @Override
    public void writeStartArray(JsonGenerator g) {
        // the generator is already in the array, whose parent is where the array is a value
        TokenStreamContext parent = g.streamWriteContext().getParent();
        if (parent == null || !parent.inArray()) {
            newline(g);
        }
        g.writeRaw('[');
        level++;
    }

    @Override
    public void beforeArrayValues(JsonGenerator g) {
        newline(g);
    }

    @Override
    public void writeArrayValueSeparator(JsonGenerator g) {
        g.writeRaw(',');
        newline(g);
    }

    @Override
    public void writeEndArray(JsonGenerator g, int nrOfValues) {
        level--;
        newline(g);
        g.writeRaw(']');
    }

    private void newline(JsonGenerator g) {
        g.writeRaw(newline);
        for (int i = 0; i < level; i++) {
            g.writeRaw(indent);
        }
    }
}
