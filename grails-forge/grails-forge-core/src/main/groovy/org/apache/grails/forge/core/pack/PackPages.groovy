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
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.grails.exceptions.ExceptionUtils
import org.grails.gsp.GroovyPageMetaInfo
import org.grails.gsp.GroovyPageTemplate
import org.grails.gsp.GroovyPagesException
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.gsp.io.DefaultGroovyPageLocator
import org.grails.taglib.AbstractTemplateVariableBinding
import org.grails.taglib.GrailsTagException
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
 * logic writes nothing, and what an expression gives is written as it is, not encoded for HTML.</p>
 *
 * <p>A template is compiled once for an instance, however often it is rendered: the compiled page is
 * kept, by a digest of whose template it is and of its source, for as long as the instance is. A
 * compiled page keeps the page each thread ran last, and with it what that run wrote, until memory is
 * needed, and its class keeps what its author has it keep. So the packs a generation is given, whose
 * templates are the code of another party, have an instance of their own for that generation, and
 * only the packs Forge has in itself, which are a fixed set, share one between generations. An
 * instance can be used by several threads at once: it compiles one page at a time and renders any
 * number.</p>
 *
 * @since 9.0
 * @see PackModel
 */
@CompileStatic
final class PackPages {

    /** What every template of a pack starts with, whether its source says so or not */
    public static final String DIRECTIVES = '<%@ page trimLogicLines="true" expressionCodec="none" %>'

    private static final int MAX_MESSAGE = 500
    private static final int MAX_FRAMES = 200
    private static final int MAX_CAUSES = 10

    // what the encoders of GSP look their state up with is one for the program: this one has a state
    // for each thread and none of a generation
    private static final DefaultOutputContextLookup ENCODING_STATE = new DefaultOutputContextLookup()

    private final GroovyPagesTemplateEngine engine
    private final Map<String, GroovyPageMetaInfo> pages = new ConcurrentHashMap<>()
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
     * @return the number of templates this has compiled
     */
    int getCompiled() {
        synchronized (pages) {
            return compiled
        }
    }

    /**
     * Renders a template, which is compiled the first time it is rendered.
     *
     * @param owner whose template it is, the name of its feature: a compiled page is of one owner
     * @param source the source of the template
     * @param name what to call the template in a message
     * @param templateApi the template API the model is of
     * @param model what the template can refer to, by name
     * @param limit the most bytes the template can write
     * @return what the template wrote, in UTF-8
     * @throws IllegalArgumentException when the template cannot be compiled or rendered, or writes more
     * than the limit
     */
    @SuppressWarnings('ParameterCount')
    byte[] render(String owner, String source, String name, int templateApi, Map<String, Object> model, int limit) {
        StringWriter out = new Bounded(limit)
        String text = source.startsWith('﻿') ? source.substring(1) : source
        GroovyPageMetaInfo page = null
        try {
            page = compiledPage(owner, text)
            new GroovyPageTemplate(page, new Names(model.keySet(), templateApi)).make(model).writeTo(out)
        } catch (TooMuch ignored) {
            throw new IllegalArgumentException("The template ${name} writes more than the ${limit} bytes a file of a feature pack can have")
        } catch (StackOverflowError e) {
            throw new IllegalArgumentException("The template ${name} cannot be rendered: ${where(e, page, text)}it calls itself without end", e)
        } catch (VirtualMachineError e) {
            // not for this to say anything about
            throw e
        } catch (Throwable e) {
            // an assertion and a missing class are errors of the template like any other. What went wrong
            // is in words of its author and of the compiler: a few of them, on one line, and with the
            // error itself for whoever asks for the whole of it
            throw new IllegalArgumentException("The template ${name} cannot be rendered: ${where(e, page, text)}${reason(e)}", e)
        }
        byte[] written = out.toString().getBytes(StandardCharsets.UTF_8)
        if (written.length > limit) {
            throw new IllegalArgumentException("The template ${name} writes more than the ${limit} bytes a file of a feature pack can have")
        }
        return written
    }

    /**
     * The compiled page of a template, which is compiled when there is none yet. A page that does not
     * compile is kept as well, and fails every time it is rendered.
     */
    private GroovyPageMetaInfo compiledPage(String owner, String text) {
        String key = key(owner, text)
        GroovyPageMetaInfo page = pages.get(key)
        if (page == null) {
            // one at a time: an engine is not said to compile two pages at once, and a page is compiled once
            synchronized (pages) {
                page = pages.get(key)
                if (page == null) {
                    // the directives on the first line of the source, so that the lines of a message are those
                    // of the template; a name of its own for the page, since the name of a file is not always
                    // one a page can have
                    String name = "template${++compiled}.gsp"
                    page = ((GroovyPageTemplate) engine.createTemplate(DIRECTIVES + text, name, false)).metaInfo
                    pages.put(key, page)
                }
            }
        }
        return page
    }

    private static String key(String owner, String text) {
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        digest.update(owner.getBytes(StandardCharsets.UTF_8))
        digest.update((byte) 0)
        digest.update(DIRECTIVES.getBytes(StandardCharsets.UTF_8))
        digest.update(text.getBytes(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(digest.digest())
    }

    /**
     * Where in the template an error is, to go in front of what it is: {@code line 3: }, or nothing when
     * the error does not say or says so itself. The compiler and the virtual machine name a line of the
     * class a page is compiled to, and the page has the line of the template each of those was written for.
     */
    private static String where(Throwable e, GroovyPageMetaInfo page, String text) {
        int line = 0
        if (e instanceof GrailsTagException) {
            // of a tag that is written out, it names the line the tag ends on: what failed in its body knows better
            line = ((GrailsTagException) e).getLineNumber()
            Throwable inBody = e.getCause()
            for (int i = 0; i < MAX_CAUSES && inBody != null && page?.getPageClass() != null; i++) {
                int inner = lineOfTemplate(page, lineOfPage(inBody, page.getPageClass().getName()))
                line = inner < 1 ? line : inner
                inBody = inBody.getCause()
            }
        } else if (e instanceof GroovyPagesException) {
            // any other is of an expression, and names the line itself
            if (e.getCause() instanceof CompilationFailedException) {
                line = lineOfTemplate(page, ExceptionUtils.extractLineNumber((CompilationFailedException) e.getCause()))
            }
        } else if (page?.getPageClass() != null) {
            line = lineOfTemplate(page, lineOfPage(e, page.getPageClass().getName()))
        }
        if (line < 1) {
            return ''
        }
        int lines = text.count('\n') + (text.endsWith('\n') ? 0 : 1)
        // what is found missing only where the template ends
        return line > lines ? 'at its end: ' : "line ${line}: "
    }

    private static int lineOfTemplate(GroovyPageMetaInfo page, int lineOfPage) {
        int[] lines = page?.getLineNumbers()
        return lines != null && lineOfPage >= 1 && lineOfPage <= lines.length ? lines[lineOfPage - 1] : 0
    }

    /**
     * The line of the class of the page, or of one of its closures, that was running when the error was thrown.
     */
    private static int lineOfPage(Throwable e, String pageClass) {
        StackTraceElement[] frames
        try {
            frames = e.getStackTrace()
        } catch (Throwable ignored) {
            return 0
        }
        for (int i = 0; i < frames.length && i < MAX_FRAMES; i++) {
            String name = frames[i].getClassName()
            if (frames[i].getLineNumber() > 0 && (name == pageClass || name.startsWith(pageClass + '$'))) {
                return frames[i].getLineNumber()
            }
        }
        return 0
    }

    private static String reason(Throwable e) {
        String message
        try {
            message = problem(e)
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
     * What an error says, without the name and the lines of the class of the page, which mean nothing to
     * the author of a template.
     */
    private static String problem(Throwable e) {
        if (e instanceof GroovyPagesException && e.getCause() instanceof MultipleCompilationErrorsException) {
            List<?> errors = ((MultipleCompilationErrorsException) e.getCause()).getErrorCollector().getErrors()
            if (errors && errors[0] instanceof SyntaxErrorMessage) {
                return 'it does not compile: ' + ((SyntaxErrorMessage) errors[0]).getCause().getOriginalMessage()
            }
        }
        String message = e.getMessage()
        if (e instanceof GrailsTagException && message != null) {
            // it has the name of the page and the line in front of it, and can have the name after it
            GrailsTagException tag = (GrailsTagException) e
            String prefix = "[${tag.getFileName()}:${tag.getLineNumber()}] "
            return (message.startsWith(prefix) ? message.substring(prefix.length()) : message).replace(" in GSP ${tag.getFileName()}", '')
        }
        return message
    }

    /**
     * Has a page fail for a name that is neither in its model nor one of its own (what it declares, and
     * the {@code out} and {@code it} every page has), which without it would be nothing: a template with a name misspelled, or written for a later
     * template API, would write a wrong file and not say so.
     */
    private static final class Names extends DefaultOutputContextLookup {

        private final OutputContext context

        Names(Set<String> model, int templateApi) {
            Set<String> known = new TreeSet<>(model)
            context = new DefaultOutputContext() {

                @Override
                AbstractTemplateVariableBinding createAndRegisterRootBinding() {
                    AbstractTemplateVariableBinding root = new Root(known, templateApi)
                    setBinding(root)
                    return root
                }
            }
        }

        @Override
        OutputContext lookupOutputContext() {
            // registers what the encoders of GSP look their state up with, when nothing has
            ENCODING_STATE.lookupOutputContext()
            return context
        }
    }

    /**
     * The binding every name a page does not have itself is looked up in.
     */
    private static final class Root extends TemplateVariableBinding {

        private final Set<String> known
        private final int templateApi

        Root(Set<String> known, int templateApi) {
            this.known = known
            this.templateApi = templateApi
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
            throw new MissingPropertyException("There is no '${name}' for a template: template API ${templateApi} has " +
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
