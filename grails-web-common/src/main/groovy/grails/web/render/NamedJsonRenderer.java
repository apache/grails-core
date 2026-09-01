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
package grails.web.render;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Renders a value with a registered named JSON configuration.
 *
 * @since 8.0
 */
public interface NamedJsonRenderer {

    boolean contains(String name);

    void render(String name, Object value, Writer writer) throws IOException;

    /**
     * Renders with a per-response projection applied.
     *
     * <p>Deliberately not a default method: an implementation that quietly ignored the projection
     * would drop it from the response with nothing to indicate it had been requested.</p>
     *
     * @param name the registered configuration
     * @param value the value to write
     * @param writer the response writer
     * @param includes property names to include, or null for all
     * @param excludes property names to exclude, or null for none
     * @throws IOException if writing fails
     */
    void render(String name, Object value, Writer writer,
            List<String> includes, List<String> excludes) throws IOException;
}
