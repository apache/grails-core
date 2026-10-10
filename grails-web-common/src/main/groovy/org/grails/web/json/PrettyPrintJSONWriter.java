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

import java.io.Writer;

/**
 * A JSONWriter dedicated to create indented/pretty printed output, indented as Grails 8 indented it.
 *
 * @author Siegfried Puchbauer
 * @since 1.1
 * @deprecated use {@link JSONWriter#JSONWriter(Writer, JsonMapperSupport, boolean)}, which indents with the mapper's
 *     default pretty printer; {@code grails.converters.json.legacy} uses it to indent JSON as Grails 8 did
 */
@Deprecated(since = "9.0")
public class PrettyPrintJSONWriter extends JSONWriter {

    public static final String DEFAULT_INDENT_STR = "  ";

    public static final String NEWLINE = System.lineSeparator();

    public PrettyPrintJSONWriter(Writer w) {
        this(w, DEFAULT_INDENT_STR);
    }

    public PrettyPrintJSONWriter(Writer w, String indentStr) {
        this(w, JsonMapperSupport.DEFAULT, indentStr);
    }

    /**
     * @param w the writer to write the JSON text to
     * @param jsonMapper the mapper to write the JSON text with
     * @param indentStr the text of one level of indentation
     * @since 9.0
     */
    public PrettyPrintJSONWriter(Writer w, JsonMapperSupport jsonMapper, String indentStr) {
        super(w, jsonMapper, new GrailsPrettyPrinter(indentStr, NEWLINE));
    }
}
