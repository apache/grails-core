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
package grails.plugin.json.builder;

import java.io.IOException;
import java.io.Writer;

/**
 * Temporary fork of {@link groovy.json.StreamingJsonBuilder} until Groovy 2.4.5 is out.
 *
 * @author Tim Yates
 * @author Andrey Bloschetsov
 * @author Graeme Rocher
 *
 * @since 1.8.1
 * @deprecated Use {@link groovy.json.StreamingJsonBuilder} instead.
 */
@Deprecated(since = "7.1", forRemoval = true)
public class StreamingJsonBuilder extends groovy.json.StreamingJsonBuilder {

    /**
     * Instantiates a JSON builder.
     *
     * @param writer A writer to which Json will be written
     */
    public StreamingJsonBuilder(Writer writer) {
        super(writer);
    }

    /**
     * Instantiates a JSON builder with the given generator.
     *
     * @param writer A writer to which Json will be written
     * @param generator used to generate the output
     * @since 2.5
     */
    @Deprecated(since = "7.1", forRemoval = true)
    public StreamingJsonBuilder(Writer writer, grails.plugin.json.builder.JsonGenerator generator) {
        super(writer, generator);
    }

    /**
     * Instantiates a JSON builder, possibly with some existing data structure.
     *
     * @param writer  A writer to which Json will be written
     * @param content a pre-existing data structure, default to null
     * @throws IOException
     *         If an I/O error occurs
     */
    public StreamingJsonBuilder(Writer writer, Object content) throws IOException {
        super(writer, content);
    }

    /**
     * Instantiates a JSON builder, possibly with some existing data structure and
     * the given generator.
     *
     * @param writer A writer to which Json will be written
     * @param content a pre-existing data structure, default to null
     * @param generator used to generate the output
     * @throws IOException
     *         If an I/O error occurs
     * @since 2.5
     */
    public StreamingJsonBuilder(Writer writer, Object content, grails.plugin.json.builder.JsonGenerator generator) throws IOException {
        super(writer, content, generator);
    }

    public StreamingJsonBuilder(Writer writer, groovy.json.JsonGenerator generator) {
        super(writer, generator);
    }

    public StreamingJsonBuilder(Writer writer, Object content, groovy.json.JsonGenerator generator) throws IOException {
        super(writer, content, generator);
    }

    @Deprecated(since = "7.1", forRemoval = true)
    public static class StreamingJsonDelegate extends groovy.json.StreamingJsonBuilder.StreamingJsonDelegate {

        public StreamingJsonDelegate(Writer w, boolean first) {
            super(w, first);
        }

        @Deprecated(since = "7.1", forRemoval = true)
        public StreamingJsonDelegate(Writer w, boolean first, grails.plugin.json.builder.JsonGenerator generator) {
            super(w, first, generator);
        }

        public StreamingJsonDelegate(Writer w, boolean first, groovy.json.JsonGenerator generator) {
            super(w, first, generator);
        }
    }
}
