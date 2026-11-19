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
import org.grails.taglib.AbstractTemplateVariableBinding
import org.grails.taglib.TemplateVariableBinding
import org.grails.taglib.encoder.DefaultOutputContextLookup
import org.grails.taglib.encoder.OutputContext

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
    private int compiled

    /**
     * @param classLoader the class loader the pages are compiled against
     */
    PackPages(ClassLoader classLoader) {
        engine = new GroovyPagesTemplateEngine() {

            @Override
            String getGspEncoding() {
                // the source is handed over in UTF-8, whatever the platform reads its files in
                return StandardCharsets.UTF_8.name()
            }
        }
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
            // on the first line of the source, so that the lines of a message are those of the template; a
            // name of its own for the page, since the name of a file is not always one a page can have
            String page = "template${++compiled}.gsp"
            String text = source.startsWith('\uFEFF') ? source.substring(1) : source
            GroovyPageTemplate template = (GroovyPageTemplate) engine.createTemplate(DIRECTIVES + text, page, false)
            new GroovyPageTemplate(template.metaInfo, new Names(model.keySet())).make(model).writeTo(out)
        } catch (TooMuch ignored) {
            throw new IllegalArgumentException("The template ${name} writes more than the ${limit} bytes a file of a feature pack can have")
        } catch (StackOverflowError e) {
            throw new IllegalArgumentException("The template ${name} cannot be rendered: it calls itself without end", e)
        } catch (VirtualMachineError e) {
            // not for this to say anything about
            throw e
        } catch (Throwable e) {
            // an assertion and a missing class are errors of the template like any other. What went wrong
            // is in words of its author and of the compiler: a few of them, on one line, and with the
            // error itself for whoever asks for the whole of it
            throw new IllegalArgumentException("The template ${name} cannot be rendered: ${reason(e)}", e)
        }
        byte[] written = out.toString().getBytes(StandardCharsets.UTF_8)
        if (written.length > limit) {
            throw new IllegalArgumentException("The template ${name} writes more than the ${limit} bytes a file of a feature pack can have")
        }
        return written
    }

    private static String reason(Throwable e) {
        String message
        try {
            message = e.getMessage()
        } catch (Throwable ignored) {
            message = null
        }
        String reason = (message ?: '').readLines().join(' ')
                .replaceAll(/[\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}]/, ' ').replace('@|', '@ |').trim()
        if (!reason) {
            // an anonymous class has no simple name
            reason = e.class.name
        }
        return reason.length() > MAX_MESSAGE ? reason.substring(0, reason.offsetByCodePoints(0, reason.codePointCount(0, MAX_MESSAGE))) + '...' : reason
    }

    /**
     * Has a page fail for a name that is neither in its model nor one of its own (what it declares, and
     * the {@code out} and {@code it} every page has), which without it would be nothing: a template with a name misspelled, or written for a later
     * template API, would write a wrong file and not say so.
     */
    private static final class Names extends DefaultOutputContextLookup {

        private final OutputContext context

        Names(Set<String> model) {
            Set<String> known = new TreeSet<>(model)
            context = new DefaultOutputContext() {

                @Override
                AbstractTemplateVariableBinding createAndRegisterRootBinding() {
                    AbstractTemplateVariableBinding root = new Root(known)
                    setBinding(root)
                    return root
                }
            }
        }

        @Override
        OutputContext lookupOutputContext() {
            // registers what the encoders of GSP look their state up with
            super.lookupOutputContext()
            return context
        }
    }

    /**
     * The binding every name a page does not have itself is looked up in.
     */
    private static final class Root extends TemplateVariableBinding {

        private final Set<String> known

        Root(Set<String> known) {
            this.known = known
        }

        @Override
        Binding findBindingForVariable(String name) {
            return this
        }

        @Override
        Object getVariable(String name) {
            if (getVariablesMap().containsKey(name) || known.contains(name)) {
                return getVariablesMap().get(name)
            }
            throw new MissingPropertyException("There is no '${name}' for a template: template API ${PackDescriptor.TEMPLATE_API} has " +
                    "${known}")
        }
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
