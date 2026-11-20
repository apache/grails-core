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
package org.apache.grails.forge.cli.artefact

import groovy.text.GStringTemplateEngine
import groovy.text.Template
import groovy.transform.CompileStatic
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.apache.grails.forge.core.pack.PackDescriptorParser
import org.grails.forge.application.Project
import org.grails.forge.util.NameUtils

/**
 * Finds and renders the template a code generation command writes a class from: the template of the
 * project under {@code src/main/templates} when there is one, and the one this program has otherwise.
 *
 * <p>A template of a project is code of the project, as its build file is. One in the
 * {@link ArtefactTemplate.Syntax#GROOVY Groovy syntax} is run by the command, with all that this
 * program can do; one in the {@link ArtefactTemplate.Syntax#TOKENS token syntax} is text and nothing
 * of it is run. Either is read within a bound, as UTF-8, and only from inside the project.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ArtefactTemplates {

    /** Where a project has its templates */
    public static final String DIRECTORY = 'src/main/templates'

    /** The most bytes a template of a project can have */
    public static final int MAX_TEMPLATE = 256 * 1024

    private static final String BUILT_IN = '/META-INF/grails-forge-cli/templates/'
    private static final int MAX_REASON = 300
    private static final String PACKAGE = 'artifact.package'
    // a token, and the end of the line when the token is the last of it
    private static final Pattern TOKEN = Pattern.compile(/@(artifact\.[A-Za-z0-9_.]*)@(\r?\n)?/)
    // the script a Groovy template is compiled to has one line in front of the template
    private static final int LINES_BEFORE_TEMPLATE = 1
    private static final String SCRIPT_PACKAGE = 'groovy.tmp.templates.'
    private static final Pattern COMPILER = Pattern.compile(/(?s)GStringTemplateScript\d+\.groovy: (\d+): (.+?)\s+@ line \d+, column \d+\./)

    private ArtefactTemplates() {
    }

    /**
     * The source of a template.
     */
    static final class Source {

        /** The text of the template */
        final String text
        /** The path of the template in the project, or null for the template of this program */
        final String projectPath

        private Source(String text, String projectPath) {
            this.text = text
            this.projectPath = projectPath
        }
    }

    /**
     * @param template a template
     * @return its path in a project, with {@code /} between the steps
     */
    static String projectPath(ArtefactTemplate template) {
        return "${DIRECTORY}/${template.location}"
    }

    /**
     * @param template a template
     * @return the text of the template this program has
     */
    static String builtIn(ArtefactTemplate template) {
        InputStream stream = ArtefactTemplates.getResourceAsStream(BUILT_IN + template.location)
        if (stream == null) {
            throw new IllegalStateException("The template ${template.location} is missing from the command line")
        }
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
        } finally {
            stream.close()
        }
    }

    /**
     * The template to use in a project: the one the project has under {@code src/main/templates}, or
     * the one of this program when the project has none.
     *
     * @param projectDirectory the directory of the project
     * @param template the template
     * @throws ArtefactTemplateException when the project has a template that cannot be used: one that
     * is outside the project by a link, is no file, is too large or is not text
     */
    static Source find(File projectDirectory, ArtefactTemplate template) {
        String path = projectPath(template)
        Path file = projectDirectory.toPath().resolve(DIRECTORY).resolve(template.location)
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.exists(file)) {
            return new Source(builtIn(template), null)
        }
        byte[] content
        try {
            Path project = projectDirectory.toPath().toRealPath()
            Path real = file.toRealPath()
            if (!real.startsWith(project)) {
                throw new ArtefactTemplateException("The template ${path} is not used: it is a link, or below one, that leads out of the project")
            }
            if (!Files.isRegularFile(real)) {
                throw new ArtefactTemplateException("The template ${path} is not used: it is not a file")
            }
            if (Files.size(real) > MAX_TEMPLATE) {
                throw new ArtefactTemplateException("The template ${path} is not used: it has more than the ${MAX_TEMPLATE} bytes a template can have")
            }
            // not all of a file that has grown since it was measured
            InputStream stream = Files.newInputStream(real)
            try {
                content = stream.readNBytes(MAX_TEMPLATE + 1)
            } finally {
                stream.close()
            }
        } catch (IOException e) {
            throw new ArtefactTemplateException("The template ${path} cannot be read (${e.getClass().getSimpleName()})", e)
        }
        if (content.length > MAX_TEMPLATE) {
            throw new ArtefactTemplateException("The template ${path} is not used: it has more than the ${MAX_TEMPLATE} bytes a template can have")
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString()
            return new Source(text.startsWith('﻿') ? text.substring(1) : text, path)
        } catch (CharacterCodingException e) {
            throw new ArtefactTemplateException("The template ${path} is not used: it is not text in UTF-8", e)
        }
    }

    /**
     * Renders a template for a class.
     *
     * @param source the source of the template, see {@link #find(File, ArtefactTemplate)}
     * @param template the template it is the source of
     * @param names the names of the class: its package, which can be missing, its name without what
     * the kind of class adds to it, and its property name
     * @return the text of the file
     * @throws ArtefactTemplateException when the template cannot be rendered
     */
    static String render(Source source, ArtefactTemplate template, Project names) {
        String name = source.projectPath ?: template.location
        return template.syntax == ArtefactTemplate.Syntax.TOKENS ? tokens(source.text, name, names) : groovy(source.text, name, names)
    }

    private static String tokens(String text, String name, Project names) {
        String packageName = names.getPackageName() ?: ''
        Map<String, String> values = [
                'artifact.package.name': packageName,
                'artifact.package.path': packageName.replace('.', '/'),
                'artifact.name': names.getClassName(),
                'artifact.propertyName': names.getPropertyName()
        ]
        StringBuilder rendered = new StringBuilder(text.length() + 256)
        Matcher token = TOKEN.matcher(text)
        int from = 0
        while (token.find()) {
            String key = token.group(1)
            String lineEnd = token.group(2) ?: ''
            rendered.append(text, from, token.start())
            if (key == PACKAGE) {
                // the declaration is a line of its own; without a package neither it nor its line is there
                if (packageName) {
                    rendered.append('package ').append(packageName).append(lineEnd ?: '\n').append(lineEnd)
                }
            } else if (values.containsKey(key)) {
                rendered.append(values.get(key)).append(lineEnd)
            } else {
                int line = text.substring(0, token.start()).count('\n') + 1
                throw new ArtefactTemplateException("The template ${name} cannot be rendered: line ${line}: there is no @${shown(key)}@; " +
                        "a template has @${PACKAGE}@, @${values.keySet().join('@, @')}@")
            }
            from = token.end()
        }
        rendered.append(text, from, text.length())
        return rendered.toString()
    }

    private static String groovy(String text, String name, Project names) {
        String packageName = names.getPackageName() ?: ''
        String className = names.getClassName()
        // the names the templates of the shell's scripts were given
        Map<String, Object> model = [
                className: className,
                fullName: packageName ? "${packageName}.${className}".toString() : className,
                propertyName: names.getPropertyName(),
                modelName: names.getPropertyName(),
                packageName: packageName,
                packagePath: packageName.replace('.', '/'),
                simpleName: className,
                lowerCaseName: NameUtils.getScriptName(className)
        ] as Map<String, Object>
        try {
            Template compiled = new GStringTemplateEngine(ArtefactTemplates.getClassLoader()).createTemplate(text)
            StringWriter out = new StringWriter()
            compiled.make(model).writeTo(out)
            return out.toString()
        } catch (Throwable e) {
            if (e instanceof VirtualMachineError) {
                throw e
            }
            throw new ArtefactTemplateException("The template ${name} cannot be rendered: ${problem(e, text, model.keySet())}", e)
        }
    }

    /**
     * What went wrong in a Groovy template, with the line of the template where that can be said.
     */
    private static String problem(Throwable e, String text, Set<String> names) {
        if (e instanceof MissingPropertyException) {
            String missing = ((MissingPropertyException) e).getProperty()
            // a name is looked up where the text it is in starts: the line is that of where it is first written
            Matcher written = missing ==~ /\w+/ ? Pattern.compile('\\b' + missing + '\\b').matcher(text) : null
            String line = written != null && written.find() ? "line ${text.substring(0, written.start()).count('\n') + 1}: " : ''
            return "${line}it has no '${shown(missing)}'; a template has ${names.join(', ')}"
        }
        String message
        try {
            message = e.getMessage() ?: e.getClass().getName()
        } catch (Throwable ignored) {
            message = e.getClass().getName()
        }
        // the engine says what the compiler said in its own message, and does not hand the error on
        Matcher compiler = COMPILER.matcher(message)
        if (compiler.find()) {
            return "${where(text, Integer.parseInt(compiler.group(1)))}it does not compile: ${shown(compiler.group(2))}"
        }
        StackTraceElement frame = e.getStackTrace().find { StackTraceElement each ->
            each.getLineNumber() > 0 && each.getClassName().startsWith(SCRIPT_PACKAGE)
        }
        return (frame == null ? '' : where(text, frame.getLineNumber())) + shown(message)
    }

    /**
     * The line of a template for a line of the script the engine compiles it to, to go in front of a
     * message: {@code line 3: }, or nothing for a line of the script that is none of the template,
     * as the one an error is on that the compiler finds only where the script ends. The script has
     * a line in front of the template and one more after each scriptlet.
     */
    private static String where(String text, int scriptLine) {
        int script = 1 + LINES_BEFORE_TEMPLATE
        int line = 1
        int i = 0
        while (i < text.length() && script < scriptLine) {
            if (text.charAt(i) == ('\n' as char)) {
                line++
                script++
                i++
            } else if (text.startsWith('<%', i) && !text.startsWith('<%=', i)) {
                int end = text.indexOf('%>', i + 2)
                if (end < 0) {
                    break
                }
                int within = text.substring(i, end).count('\n')
                if (script + within >= scriptLine) {
                    return "line ${line + scriptLine - script}: "
                }
                line += within
                script += within + 1
                i = end + 2
            } else {
                i++
            }
        }
        return script == scriptLine && line <= text.readLines().size() ? "line ${line}: " : ''
    }

    // one line, with nothing in it a terminal or the command line acts on, and not all of a long one
    private static String shown(String text) {
        return PackDescriptorParser.shown(text.readLines().join(' ').trim(), MAX_REASON).replace('@|', '@ |')
    }
}
