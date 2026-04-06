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

import groovy.lang.Closure;

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

    private final Writer grailsWriter;
    private final groovy.json.JsonGenerator grailsGenerator;

    public StreamingJsonBuilder(Writer writer) {
        super(writer);
        this.grailsWriter = writer;
        this.grailsGenerator = new groovy.json.JsonGenerator.Options().build();
    }

    @Deprecated(since = "7.1", forRemoval = true)
    public StreamingJsonBuilder(Writer writer, grails.plugin.json.builder.JsonGenerator generator) {
        super(writer, generator);
        this.grailsWriter = writer;
        this.grailsGenerator = generator;
    }

    public StreamingJsonBuilder(Writer writer, Object content) throws IOException {
        super(writer, content);
        this.grailsWriter = writer;
        this.grailsGenerator = new groovy.json.JsonGenerator.Options().build();
    }

    public StreamingJsonBuilder(Writer writer, Object content, grails.plugin.json.builder.JsonGenerator generator) throws IOException {
        super(writer, content, generator);
        this.grailsWriter = writer;
        this.grailsGenerator = generator;
    }

    public StreamingJsonBuilder(Writer writer, groovy.json.JsonGenerator generator) {
        super(writer, generator);
        this.grailsWriter = writer;
        this.grailsGenerator = generator;
    }

    public StreamingJsonBuilder(Writer writer, Object content, groovy.json.JsonGenerator generator) throws IOException {
        super(writer, content, generator);
        this.grailsWriter = writer;
        this.grailsGenerator = generator;
    }

    @Override
    public Object call(@groovy.lang.DelegatesTo(value = StreamingJsonDelegate.class, strategy = Closure.DELEGATE_FIRST) Closure c) throws IOException {
        grailsWriter.write(grails.plugin.json.builder.JsonOutput.OPEN_BRACE);
        StreamingJsonDelegate.cloneDelegateAndGetContent(grailsWriter, c, true, grailsGenerator);
        grailsWriter.write(grails.plugin.json.builder.JsonOutput.CLOSE_BRACE);
        return null;
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

        public static void cloneDelegateAndGetContent(Writer w, Closure c, boolean first, groovy.json.JsonGenerator generator) {
            StreamingJsonDelegate delegate = new StreamingJsonDelegate(w, first, generator);
            Closure cloned = (Closure) c.clone();
            cloned.setDelegate(delegate);
            cloned.setResolveStrategy(Closure.DELEGATE_FIRST);
            cloned.call();
        }
    }
}
