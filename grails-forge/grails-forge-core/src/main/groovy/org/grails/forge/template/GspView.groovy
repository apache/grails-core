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

package org.grails.forge.template

import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * A page of the generator and the model it is rendered with. The pages are GSPs compiled with the
 * Forge plugin, under {@code grails-app/views/forge}, and are named by their URI, such as
 * {@code /forge/feature/build/gradle/templates/buildGradle.gsp}.
 */
@CompileStatic
@EqualsAndHashCode
@ToString(includeNames = true)
final class GspView implements Writable {

    final String uri
    final Map<String, Object> model

    private GspView(String uri, Map<String, Object> model) {
        this.uri = uri
        this.model = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(model))
    }

    /**
     * @param uri the URI of the page, such as {@code /forge/feature/cli.gsp}
     * @param model the values of the page's model, by name
     */
    static GspView of(String uri, Map<String, ?> model) {
        new GspView(uri, (Map<String, Object>) model)
    }

    /**
     * @return the page rendered with the model
     */
    String render() {
        StringWriter out = new StringWriter()
        ForgePages.render(uri, model, out)
        out.toString()
    }

    @Override
    void write(OutputStream outputStream) throws IOException {
        Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
        ForgePages.render(uri, model, writer)
        writer.flush()
    }
}
