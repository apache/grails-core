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
package org.apache.grails.core.cli.compiler

import java.nio.charset.StandardCharsets
import java.util.regex.Matcher
import java.util.regex.Pattern

import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.StandardJavaFileManager
import javax.tools.ToolProvider

import groovy.json.JsonSlurper
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.messages.Message
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.control.messages.WarningMessage
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import org.apache.grails.gradle.model.cli.ToolDescriptor

/**
 * Holds the page of the guide on writing tools to the compiler of tools: every example tool of
 * {@code creatingTools.adoc} compiles as the build of a project compiles it, without an error and
 * without a warning, and its descriptor lacks nothing, so the page never teaches a tool the compiler
 * warns about. The Java example compiles without a warning, and the descriptors the page shows written
 * by hand are ones the build reads.
 *
 * <p>An example is a listing whose title is the path of the file it is, under {@code src/cli} or
 * {@code src/standalone}, as {@code .src/cli/groovy/com/example/AuditReportTool.groovy}: it is compiled
 * at that path, so the folder it is in is the one the compiler holds it to. A listing that declares a
 * tool class has such a title; a fragment that is not a whole file is no example and has none.</p>
 */
class CreatingToolsExamplesSpec extends Specification {

    // the page in the checkout of this build: the tests of grails-core run in its directory
    private static final String PAGE = '../grails-doc/src/en/guide/commandLine/creatingTools.adoc'

    private static final Pattern SOURCE_FOLDER = Pattern.compile('src/(cli|standalone)/(groovy|java|resources)/.+')

    private static final Pattern TOOL_CLASS = Pattern.compile('(?m)^(?:public )?class (\\w+) implements [^{]*\\b(?:ApplicationTool|StandaloneTool)\\b')

    private static final Pattern PACKAGE = Pattern.compile('(?m)^package ([\\w.]+);?$')

    @Shared
    List<Listing> listings = Listing.of(new File(PAGE).canonicalFile)

    @TempDir
    File dir

    def "the page has example tools in Groovy and in Java, and descriptors written by hand"() {
        expect:
        examples('groovy').size() >= 9
        examples('java').size() == 1
        descriptors().size() == 2
    }

    def "every listing of the page that declares a tool class is a whole file, whose title is its path"() {
        expect:
        listings.findAll { Listing listing -> TOOL_CLASS.matcher(listing.text).find() && !listing.isExample() }*.where() == []
    }

    @Unroll
    def "the example #example.title compiles without an error or a warning, and lacks nothing"() {
        when:
        Compiled compiled = compileGroovy(example)

        then: 'it compiles'
        compiled.errors == []

        and: 'the compiler warns of nothing, also where a build shows its standard error and not its warnings'
        compiled.warnings == []
        compiled.err == ''

        and: 'its descriptor is one the build reads, and lacks nothing'
        Map descriptor = compiled.descriptor(example.toolClassName())
        descriptor != null
        descriptor.incomplete == null

        where:
        example << examples('groovy')
    }

    @Unroll
    def "the Java example #example.title compiles without an error or a warning"() {
        given:
        File source = example.writeTo(dir)
        File classes = new File(dir, 'build/classes/java')
        classes.mkdirs()
        JavaCompiler compiler = ToolProvider.systemJavaCompiler
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>()
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)

        when:
        boolean compiled = compiler.getTask(null, files, diagnostics,
                ['-classpath', System.getProperty('java.class.path'), '-d', classes.path, '-proc:none', '-Xlint:all'],
                null, files.getJavaFileObjectsFromFiles([source])).call()
        files.close()

        then:
        diagnostics.diagnostics.collect { Diagnostic diagnostic -> "${diagnostic.kind}: ${diagnostic.getMessage(Locale.ROOT)}".toString() } == []
        compiled

        where:
        example << examples('java')
    }

    @Unroll
    def "the descriptor written by hand, #example.title, is one the build reads"() {
        given:
        String fileName = example.title.substring(example.title.lastIndexOf('/') + 1)
        String className = fileName.substring(0, fileName.length() - ToolDescriptor.EXTENSION.length())

        when:
        ToolDescriptor.read(className, example.text.getBytes(StandardCharsets.UTF_8))

        then:
        noExceptionThrown()

        where:
        example << descriptors()
    }

    private List<Listing> examples(String language) {
        listings.findAll { Listing listing -> listing.language == language && listing.isExample() }
    }

    // the descriptors of tools the page shows written by hand, in the folder the build reads them from
    private List<Listing> descriptors() {
        examples('json').findAll { Listing listing -> listing.title.contains("/${ToolDescriptor.DIRECTORY}/") }
    }

    // compiles a Groovy example at its path, as the build of a project compiles a tool, to classes
    private Compiled compileGroovy(Listing example) {
        File source = example.writeTo(dir)
        File classes = new File(dir, 'build/classes/groovy')
        CompilationUnit unit = new CompilationUnit(new CompilerConfiguration(targetDirectory: classes, warningLevel: WarningMessage.LIKELY_ERRORS,
                disabledGlobalASTTransformations: [
                        'org.grails.compiler.injection.GlobalGrailsClassInjectorTransformation',
                        'org.grails.compiler.injection.GlobalImportTransformation',
                ] as Set))
        unit.addSource(source)
        ByteArrayOutputStream err = new ByteArrayOutputStream()
        PrintStream original = System.err
        List<String> errors = []
        System.err = new PrintStream(err, true, 'UTF-8')
        try {
            unit.compile(Phases.ALL)
        } catch (MultipleCompilationErrorsException e) {
            errors = e.errorCollector.errors.collect { Message message ->
                message instanceof SyntaxErrorMessage ? ((SyntaxErrorMessage) message).cause.message : message.toString()
            }
        } finally {
            System.err = original
        }
        new Compiled(classes: classes, errors: errors,
                warnings: (unit.errorCollector.warnings ?: []).collect { WarningMessage warning -> warning.message },
                err: err.toString('UTF-8'))
    }

    /**
     * What came of compiling an example.
     */
    static class Compiled {

        File classes

        List<String> errors

        List<String> warnings

        String err

        Map descriptor(String className) {
            File file = new File(classes, ToolDescriptor.location(className))
            if (!file.isFile()) {
                return null
            }
            ToolDescriptor.read(className, file.bytes)
            (Map) new JsonSlurper().parse(file, 'US-ASCII')
        }
    }

    /**
     * A listing of the page: its language, its title and its text.
     */
    static class Listing {

        String language

        String title

        String text

        int line

        static List<Listing> of(File page) {
            List<String> lines = page.readLines('UTF-8')
            List<Listing> found = []
            for (int i = 0; i < lines.size(); i++) {
                Matcher source = lines[i] =~ /^\[source,(\w+)[^\]]*\]$/
                if (!source.matches()) {
                    continue
                }
                int at = i + 1
                String title = null
                if (at < lines.size() && lines[at].startsWith('.') && !lines[at].startsWith('..')) {
                    title = lines[at].substring(1)
                    at++
                }
                if (at >= lines.size() || lines[at] != '----') {
                    continue
                }
                int end = lines.subList(at + 1, lines.size()).indexOf('----')
                if (end < 0) {
                    continue
                }
                found << new Listing(language: source.group(1), title: title, text: lines.subList(at + 1, at + 1 + end).join('\n') + '\n', line: i + 1)
                i = at + 1 + end
            }
            found
        }

        boolean isExample() {
            title != null && SOURCE_FOLDER.matcher(title).matches()
        }

        String where() {
            "line ${line}${title == null ? '' : ' (' + title + ')'}".toString()
        }

        String toolClassName() {
            Matcher packageName = PACKAGE.matcher(text)
            Matcher toolClass = TOOL_CLASS.matcher(text)
            assert toolClass.find(): "${where()} declares no tool class"
            packageName.find() ? "${packageName.group(1)}.${toolClass.group(1)}".toString() : toolClass.group(1)
        }

        File writeTo(File directory) {
            File file = new File(directory, title)
            file.parentFile.mkdirs()
            file.setText(text, 'UTF-8')
            file
        }

        @Override
        String toString() {
            title ?: where()
        }
    }
}
