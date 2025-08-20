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
package grails.rest

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

/**
 * Represents a Link in a RESTful resource. See https://tools.ietf.org/html/draft-kelly-json-hal-05#section-5
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
@EqualsAndHashCode(includes = ['rel', 'href'])
class Link implements Serializable {

    Link(String rel, String href) {
        if (!rel) {
            throw new IllegalArgumentException("The 'rel' argument - for defining the relationship of the link - is required.")
        }
        if (!href) {
            throw new IllegalArgumentException("The 'href' argument - for defining the links resource - is required.")
        }
        this.rel = rel
        this.href = href
    }

    /**
     * The link relationship
     */
    final String rel
    /**
     * The link's href
     */
    final String href
    /**
     * The language of the linked resource
     */
    Locale hreflang
    /**
     * The content type of the linked resource
     */
    String contentType
    /**
     * The Human readable title of the resource
     */
    String title

    /**
     * Whether the link is deprecated
     */
    boolean deprecated = false

    /**
     * Whether the link is a URI template
     */
    boolean templated = false

    /**
     * Creates a link for the given arguments
     *
     * @param arguments The arguments
     * @return The link
     */
    static Link createLink(Map<String, Object> arguments) {

        final rel = arguments.rel ? arguments.rel.toString() : null
        final href = arguments.href ? arguments.href.toString() : null
        def link = (Link) Link.newInstance(rel, href)

        final remaining = arguments.subMap(['hreflang', 'contentType', 'title', 'deprecated', 'templated'])
        for (entry in remaining.entrySet()) {
            final value = entry.value
            if (value) {
                ((GroovyObject) link).setProperty(entry.key, value)
            }
        }
        return link
    }
}
