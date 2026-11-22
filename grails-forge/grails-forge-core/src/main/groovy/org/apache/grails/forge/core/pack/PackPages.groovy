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
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.codehaus.groovy.control.BytecodeProcessor
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.grails.exceptions.ExceptionUtils
import org.grails.forge.util.VersionInfo
import org.grails.gsp.GroovyPageClassLoader
import org.grails.gsp.GroovyPageMetaInfo
import org.grails.gsp.GroovyPageTemplate
import org.grails.gsp.GroovyPagesException
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.gsp.compiler.GroovyPageParser
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
 * logic writes nothing, what an expression gives is written as it is, not encoded for HTML, and the
 * text is that of a file and not of a page of a web application. The only tags a template has are the
 * instructions to the generator; every other tag is text, see {@link PackTemplateSource}.</p>
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
 * <p>The instance of the packs of Forge is given the pages the build of Forge compiled, see
 * {@link PrecompiledPackPages}: a template whose page is among them, for the very source it would be
 * compiled from, is not compiled. The build compiles those pages with {@link #precompile}, which is
 * what compiles a template here, so that a page of the build is the page a run would have compiled.</p>
 *
 * @since 9.0
 * @see PackModel
 */
@CompileStatic
final class PackPages {

    /**
     * What every template of a pack starts with, whether its source says so or not. A page is by default
     * one of a web application: its {@code head}, {@code title}, {@code meta} and {@code body} are
     * rewritten for the layout it is shown in, which a file that is generated must not be, and it is
     * compiled statically where a system property asks for that, which a template with a model that has
     * no types cannot be.
     */
    public static final String DIRECTIVES = '<%@ page trimLogicLines="true" expressionCodec="none" grailsLayoutPreprocess="false" compileStatic="false" %>'

    private static final int MAX_MESSAGE = 500
    private static final int MAX_FRAMES = 200
    private static final int MAX_CAUSES = 10
    private static final int MAX_NAME = 60
    private static final String TOO_DEEP = 'it is nested too deeply to be compiled: an expression has too many parts, or instructions are ' +
            'inside each other too many times'
    private static final String TOO_LARGE = 'it has more expressions, scriptlets and instructions than the page of one template can hold: ' +
            'divide it into several files'
    private static final Pattern MISSING_NAME = Pattern.compile(/There is no '(\w+)' for a template/)

    // what the encoders of GSP look their state up with is one for the program: this one has a state
    // for each thread and none of a generation
    private static final DefaultOutputContextLookup ENCODING_STATE = new DefaultOutputContextLookup()

    private final GroovyPagesTemplateEngine engine
    private final Map<String, Compiled> pages = new ConcurrentHashMap<>()
    private final PrecompiledPackPages precompiled
    private final Loader loader
    private int compiled
    private int taken

    /**
     * @param classLoader the class loader the pages are compiled against
     */
    PackPages(ClassLoader classLoader) {
        this(classLoader, null)
    }

    /**
     * @param classLoader the class loader the pages are compiled against
     * @param precompiled the pages the build compiled, which are taken for the templates they are of
     * and not compiled again, or {@code null} for an instance that compiles every template: the one of
     * packs that are not of Forge
     */
    PackPages(ClassLoader classLoader, PrecompiledPackPages precompiled) {
        this(classLoader, precompiled, false)
    }

    private PackPages(ClassLoader classLoader, PrecompiledPackPages precompiled, boolean ofBuild) {
        this.precompiled = precompiled
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
        if (precompiled != null || ofBuild) {
            // what the engine compiles with when it is left to make its class loader, which is told the
            // source of each page and so can give the class of the build for it, or keep the classes it
            // compiles for the build. Given to the engine after it has set itself up: before, it would
            // put a class loader of its own in front of this one.
            CompilerConfiguration configuration = new CompilerConfiguration()
            configuration.setSourceEncoding(GroovyPageParser.GROOVY_SOURCE_CHAR_ENCODING)
            loader = new Loader(classLoader, configuration, precompiled)
            engine.classLoader = loader
        } else {
            loader = null
        }
    }

    /**
     * @return the number of templates this has compiled; a template whose page is one of the build was
     * not compiled, see {@link #getPrecompiled}
     */
    int getCompiled() {
        synchronized (pages) {
            return compiled
        }
    }

    /**
     * @return the number of templates this has taken the page of the build for
     */
    int getPrecompiled() {
        synchronized (pages) {
            return taken
        }
    }

    /**
     * Compiles the template of a pack of Forge for the build, as rendering it would compile it.
     *
     * @param classLoader the class loader the page is compiled against
     * @param owner whose template it is, the name of its feature
     * @param source the source of the template
     * @return the page: its key, the classes it is compiled to and what they were compiled from
     * @throws RuntimeException when the template cannot be compiled
     */
    static Precompiled precompile(ClassLoader classLoader, String owner, String source) {
        PackPages pages = new PackPages(classLoader, null, true)
        String text = withoutMark(source)
        String key = key(owner, text)
        Map<String, byte[]> classes = new TreeMap<>()
        GroovyPageMetaInfo page
        pages.loader.keep(classes)
        try {
            page = pages.create(DIRECTIVES + PackTemplateSource.toPage(text), PrecompiledPackPages.pageName(key))
        } finally {
            pages.loader.keep(null)
        }
        if (page.getCompilationException() != null) {
            throw page.getCompilationException()
        }
        String name = page.getPageClass().getName()
        if (name != PrecompiledPackPages.className(key) || !classes.containsKey(name)) {
            throw new IllegalStateException("The page of a template was compiled to the class ${name}, and not to " +
                    PrecompiledPackPages.className(key))
        }
        return new Precompiled(key, pages.loader.source, classes)
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
        String text = withoutMark(source)
        GroovyPageMetaInfo page = null
        try {
            Compiled compiled = compiledPage(owner, text)
            if (compiled.refused != null) {
                throw compiled.refused
            }
            page = compiled.page
            new GroovyPageTemplate(page, new Names(model.keySet(), templateApi)).make(model).writeTo(out)
        } catch (Throwable e) {
            // a page hands on what its expressions and the bodies of its tags throw inside an exception of
            // its own, so what an error is, is asked of all it was caused by
            if (causedBy(e, TooMuch) != null) {
                throw new IllegalArgumentException("The template ${name} writes more than the ${limit} bytes a file of a feature pack can have")
            }
            if (causedBy(e, StackOverflowError) != null) {
                // of the compiler, which goes as deep as what it compiles, or of the template when it runs
                boolean compiling = e instanceof GroovyPagesException && e.getCause() instanceof CompilationFailedException
                throw new IllegalArgumentException("The template ${name} cannot be rendered: " +
                        (compiling ? TOO_DEEP : "${where(e, page)}it calls itself without end"), e)
            }
            Throwable ofMachine = causedBy(e, VirtualMachineError)
            if (ofMachine != null) {
                // not for this to say anything about
                throw ofMachine
            }
            if (causedBy(e, InterruptedException) != null) {
                // whoever interrupted the thread still has to find it interrupted
                Thread.currentThread().interrupt()
            }
            // an assertion and a missing class are errors of the template like any other. What went wrong
            // is in words of its author and of the compiler: a few of them, on one line, and with the
            // error itself for whoever asks for the whole of it
            throw new IllegalArgumentException("The template ${name} cannot be rendered: ${where(e, page)}${reason(e)}${hint(e, text)}", e)
        }
        byte[] written = out.toString().getBytes(StandardCharsets.UTF_8)
        if (written.length > limit) {
            throw new IllegalArgumentException("The template ${name} writes more than the ${limit} bytes a file of a feature pack can have")
        }
        return written
    }

    private static Throwable causedBy(Throwable e, Class<? extends Throwable> type) {
        Throwable each = e
        for (int i = 0; i < MAX_CAUSES && each != null; i++) {
            if (type.isInstance(each)) {
                return each
            }
            each = each.getCause()
        }
        return null
    }

    /**
     * The compiled page of a template, which is compiled when there is none yet. A page the parser
     * refuses and one that does not compile are kept as well, and fail every time they are rendered.
     */
    private Compiled compiledPage(String owner, String text) {
        String key = key(owner, text)
        Compiled page = pages.get(key)
        if (page == null) {
            // one at a time: an engine is not said to compile two pages at once, and a page is compiled once
            synchronized (pages) {
                page = pages.get(key)
                GroovyPageMetaInfo ofBuild = page == null ? ofBuild(key, text) : null
                if (ofBuild != null) {
                    taken++
                    page = new Compiled(ofBuild, null)
                    pages.put(key, page)
                }
                if (page == null) {
                    // the directives on the first line of the source, so that the lines of a message are those
                    // of the template; a name of its own for the page, since the name of a file is not always
                    // one a page can have
                    String name = "template${++compiled}.gsp"
                    try {
                        String source = DIRECTIVES + PackTemplateSource.toPage(text)
                        page = new Compiled(create(source, name), null)
                    } catch (GrailsTagException | PackTemplateSource.Refused e) {
                        // what the parser refuses, it refuses each time
                        page = new Compiled(null, e)
                    } catch (StackOverflowError ignored) {
                        // of the compiler, which goes as deep as what it compiles does; without the error,
                        // which is no error of what the template does when it is run
                        page = new Compiled(null, new PackTemplateSource.Refused(0, TOO_DEEP))
                    }
                    pages.put(key, page)
                }
            }
        }
        return page
    }

    private GroovyPageMetaInfo create(String source, String name) {
        return ((GroovyPageTemplate) engine.createTemplate(source, name, false)).metaInfo
    }

    /**
     * The page of a template with the class the build compiled, or {@code null} when the template is to
     * be compiled: the build compiled none for its key, or compiled it from another source than the one
     * it has now. Whatever is wrong with the template is said where it is compiled.
     */
    private GroovyPageMetaInfo ofBuild(String key, String text) {
        if (precompiled == null || !precompiled.has(key)) {
            return null
        }
        loader.expect(key)
        try {
            GroovyPageMetaInfo page = create(DIRECTIVES + PackTemplateSource.toPage(text), PrecompiledPackPages.pageName(key))
            return page.getCompilationException() == null && loader.gave(page.getPageClass()) ? page : null
        } catch (VirtualMachineError e) {
            throw e
        } catch (Throwable ignored) {
            return null
        } finally {
            loader.expect(null)
        }
    }

    private static String withoutMark(String source) {
        return source.startsWith('﻿') ? source.substring(1) : source
    }

    /**
     * The key a page is kept by, which is the key the build names its class by: each part with its
     * length in front of it, so that no two owners and sources are the same bytes.
     *
     * @param owner whose template it is, the name of its feature
     * @param text the source of the template, without a byte order mark
     * @return the SHA-256 of the owner, the directives and the text
     */
    static String key(String owner, String text) {
        return digest([owner, DIRECTIVES, text])
    }

    private static String digest(List<String> parts) {
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        for (String part : parts) {
            byte[] bytes = part.getBytes(StandardCharsets.UTF_8)
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array())
            digest.update(bytes)
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    /**
     * Where in the template an error is, to go in front of what it is: {@code line 3: }, or nothing when
     * the error does not say or says so itself. The compiler and the virtual machine name a line of the
     * class a page is compiled to, and the page has the line of the template each of those was written for.
     */
    private static String where(Throwable e, GroovyPageMetaInfo page) {
        int line = 0
        if (e instanceof PackTemplateSource.Refused) {
            line = ((PackTemplateSource.Refused) e).line
        } else if (e instanceof GrailsTagException) {
            // thrown for what failed inside a tag, it names the line the tag ends on: what failed knows better
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
        // what is found missing only where the template ends is on its last line
        return line < 1 ? '' : "line ${line}: "
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
     * What to add for a name a template has not, when the template has a {@code g:set} that would have
     * set it in a view: there it is a tag of a tag library, and here it is text.
     */
    private static String hint(Throwable e, String text) {
        for (Throwable each = e; each != null && each != each.getCause(); each = each.getCause()) {
            String message
            try {
                message = each.getMessage()
            } catch (Throwable ignored) {
                continue
            }
            Matcher missing = message == null ? null : MISSING_NAME.matcher(message)
            if (missing != null && missing.find()) {
                String name = missing.group(1)
                boolean set = name.length() <= MAX_NAME && text =~ ('<g:set\\b[^>]*\\bvar\\s*=\\s*["' + "'" + ']' + Pattern.quote(name) + '["' + "'" + ']')
                return set ? ". The <g:set var=\"${name}\"> of the template is text, which is written and sets nothing: use <g:def>" : ''
            }
        }
        return ''
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
            if (e.getMessage()?.contains('Method too large')) {
                // the method of a class has a size it cannot be over, and a page is one method
                return TOO_LARGE
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

    /**
     * A template as the build compiled it.
     */
    static final class Precompiled {

        /** The key of the template, see {@link PackPages#key}. */
        final String key
        /** The SHA-256 of what the page was compiled from: the Groovy source, and the compiler and how it was set. */
        final String source
        /** The bytes of each class the page is compiled to, by the name of the class, in the order of the names. */
        final Map<String, byte[]> classes

        Precompiled(String key, String source, Map<String, byte[]> classes) {
            this.key = key
            this.source = source
            this.classes = Collections.unmodifiableMap(classes)
        }
    }

    /**
     * Compiles the pages as the class loader the engine makes for itself does, and is told the Groovy
     * source of each page as the engine is about to compile it. For a page that is expected of the
     * build it gives the class of the build when that was compiled from this very source, and refuses
     * the page when it was not: such a page is then compiled under a name of its own. For the build it
     * keeps the bytes of the classes it compiles.
     */
    private static final class Loader extends GroovyPageClassLoader {

        private final PrecompiledPackPages precompiled
        private final String compiler
        private String expected
        private Class<?> given
        private Map<String, byte[]> kept
        private String source

        Loader(ClassLoader parent, CompilerConfiguration configuration, PrecompiledPackPages precompiled) {
            super(parent, configuration)
            this.precompiled = precompiled
            this.compiler = compiler(configuration)
            configuration.setBytecodePostprocessor({ String name, byte[] original ->
                if (kept != null) {
                    kept.put(name, original.clone())
                }
                return original
            } as BytecodeProcessor)
        }

        // the key of the page that is created next, which is one of the build; null for a page that is compiled
        void expect(String key) {
            expected = key
            given = null
        }

        boolean gave(Class<?> page) {
            return given != null && given.is(page)
        }

        void keep(Map<String, byte[]> classes) {
            kept = classes
        }

        String getSource() {
            return source
        }

        /**
         * What, beside its source, the class of a page comes of: the compiler and how it is set. The
         * build and a run say it with this, and a page of the build is taken only where it is the
         * same. The Groovy source itself has in it what the parser of the pages made of the template
         * for the environment, the directives and the system properties of the run. The version of
         * Java the classes are written for is left out: a class of the build runs on every Java this
         * runs on, and is what the compiler would write for the Java of the build.
         */
        private static String compiler(CompilerConfiguration configuration) {
            return digest([
                    "groovy ${GroovySystem.getVersion()}".toString(),
                    "grails ${VersionInfo.getGrailsVersion()}".toString(),
                    "encoding ${configuration.getSourceEncoding()}".toString(),
                    "optimization ${new TreeMap<String, Boolean>(configuration.getOptimizationOptions())}".toString(),
                    "parameters ${configuration.getParameters()} preview ${configuration.isPreviewFeatures()}".toString(),
                    "base ${configuration.getScriptBaseClass()}".toString(),
                    "customizers ${configuration.getCompilationCustomizers()*.getClass()*.getName()}".toString(),
                    "disabled ${new TreeSet<String>(configuration.getDisabledGlobalASTTransformations() ?: [] as Set<String>)}".toString(),
            ])
        }

        @Override
        Class parseClass(String text, String fileName) {
            if (expected == null && kept == null) {
                // a page that is compiled in this run and is in no jar: as the engine writes it
                source = digest([compiler, text])
                return super.parseClass(text, fileName)
            }
            // A page of the build is of a package that only this module has: the engine writes the
            // source of a page without one, and a jar that is a module can have no class without. The
            // package is said in front of the first line, so that the lines stay the ones the page
            // has for the lines of its template. The build compiles this source and a run takes the
            // digest of this source: what is taken is what the run would have compiled from it.
            String ofPackage = "package ${PrecompiledPackPages.PACKAGE}; ${text}"
            source = digest([compiler, ofPackage])
            if (expected == null) {
                return super.parseClass(ofPackage, fileName)
            }
            given = precompiled.load(expected, source)
            if (given == null) {
                throw new NotOfBuild()
            }
            return given
        }
    }

    private static final class NotOfBuild extends RuntimeException {

        NotOfBuild() {
            super('not the source the build compiled', null, false, false)
        }
    }

    /**
     * What compiling a template came to: its page, which can itself hold that it does not compile, or
     * what the parser refused it with.
     */
    private static final class Compiled {

        final GroovyPageMetaInfo page
        final RuntimeException refused

        Compiled(GroovyPageMetaInfo page, RuntimeException refused) {
            this.page = page
            this.refused = refused
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
