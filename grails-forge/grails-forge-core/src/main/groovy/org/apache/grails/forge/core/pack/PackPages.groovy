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
package org.apache.grails.forge.core.pack

import groovy.transform.CompileStatic
import java.nio.charset.StandardCharsets
import org.grails.gsp.GroovyPageTemplate
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.gsp.io.DefaultGroovyPageLocator
import org.grails.taglib.encoder.DefaultOutputContextLookup

/**
 * Renders the templates of feature packs, which are GSP pages a pack brings as source.
 *
 * <p>A template is code of the pack: compiling and rendering it runs what its author wrote, with all
 * that this program can do. Nothing here confines it. A pack is used when the person generating the
 * application trusts where it comes from.</p>
 *
 * <p>Every template is compiled as if it started with {@link #DIRECTIVES}: a line that holds only
 * logic writes nothing, and what an expression gives is written as it is, not encoded for HTML.
 * The pages compiled by one instance are kept for as long as it is, so one is made for a generation.</p>
 *
 * @since 9.0
 * @see PackModel
 */
@CompileStatic
final class PackPages {

    /** What every template of a pack starts with, whether its source says so or not */
    public static final String DIRECTIVES = '<%@ page trimLogicLines="true" expressionCodec="none" %>'

    private static final int MAX_MESSAGE = 500

    private final GroovyPagesTemplateEngine engine

    /**
     * @param classLoader the class loader the pages are compiled against
     */
    PackPages(ClassLoader classLoader) {
        engine = new GroovyPagesTemplateEngine()
        engine.groovyPageLocator = new DefaultGroovyPageLocator()
        engine.classLoader = classLoader
        engine.afterPropertiesSet()
    }

    /**
     * Compiles a template and renders it.
     *
     * @param source the source of the template
     * @param name what to call the template in a message
     * @param model what the template can refer to, by name
     * @param limit the most bytes the template can write
     * @return what the template wrote, in UTF-8
     * @throws IllegalArgumentException when the template cannot be compiled or rendered, or writes more
     * than the limit
     */
    byte[] render(String source, String name, Map<String, Object> model, int limit) {
        StringWriter out = new Bounded(limit)
        try {
            GroovyPageTemplate template = (GroovyPageTemplate) engine.createTemplate(DIRECTIVES + '\n' + source, name, false)
            new GroovyPageTemplate(template.metaInfo, new DefaultOutputContextLookup()).make(model).writeTo(out)
        } catch (TooMuch ignored) {
            throw new IllegalArgumentException("The template ${name} writes more than the ${limit} bytes a file of a feature pack can have")
        } catch (Exception e) {
            // what went wrong is in words of the template's author and of the compiler: a few of them, on one line
            String reason = (e.message ?: e.class.simpleName).readLines().join(' ').replaceAll(/[\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}]/, ' ').replace('@|', '@ |')
            throw new IllegalArgumentException("The template ${name} cannot be rendered: " +
                    (reason.length() > MAX_MESSAGE ? reason.substring(0, MAX_MESSAGE) + '...' : reason))
        }
        byte[] written = out.toString().getBytes(StandardCharsets.UTF_8)
        if (written.length > limit) {
            throw new IllegalArgumentException("The template ${name} writes more than the ${limit} bytes a file of a feature pack can have")
        }
        return written
    }

    private static final class TooMuch extends RuntimeException {

        TooMuch() {
            super('too much', null, false, false)
        }
    }

    /**
     * Stops a template that writes without end before it has taken the memory there is.
     */
    private static final class Bounded extends StringWriter {

        private final int limit

        Bounded(int limit) {
            this.limit = limit
        }

        @Override
        void write(int c) {
            check(1)
            super.write(c)
        }

        @Override
        void write(char[] cbuf, int off, int len) {
            check(len)
            super.write(cbuf, off, len)
        }

        @Override
        void write(String str) {
            check(str.length())
            super.write(str)
        }

        @Override
        void write(String str, int off, int len) {
            check(len)
            super.write(str, off, len)
        }

        private void check(int more) {
            // a character is at least one byte
            if (getBuffer().length() + (long) more > limit) {
                throw new TooMuch()
            }
        }
    }
}
