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

import groovy.transform.CompileStatic
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.apache.grails.forge.core.pack.ArtefactTokens
import org.apache.grails.forge.core.pack.PackDescriptorParser
import org.grails.forge.application.Project
import org.grails.forge.util.NameUtils

/**
 * Finds and renders the template a code generation command writes a class from: the template of the
 * project under {@code src/main/templates} when there is one, and the one this program has otherwise.
 *
 * <p>A template is text in which names are replaced, in one of two ways of writing them, see
 * {@link ArtefactTemplate.Syntax}. Nothing of a template is run. A template of a project is read
 * within a bound, as UTF-8, and only from inside the project.</p>
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
    private static final String PACKAGE = ArtefactTokens.PACKAGE
    private static final Pattern PLACEHOLDER = Pattern.compile(/\$\{([A-Za-z_][A-Za-z0-9_]*)\}/)
    // the declaration of the package on a line of its own, which a class without a package has not
    private static final Pattern PACKAGE_LINE = Pattern.compile(/package \$\{packageName\}[ \t]*;?[ \t]*(?:\r\n|\r|\n|\z)(?:\r\n|\r|\n)?/)
    private static final Pattern LINE_END = Pattern.compile(/\r\n|\r|\n/)
    private static final char BYTE_ORDER_MARK = (char) 0xFEFF
    private static final char BACKSLASH = (char) 0x5C
    private static final char DOLLAR = (char) 0x24
    private static final char LINE_FEED = (char) 0x0A
    private static final char CARRIAGE_RETURN = (char) 0x0D

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
        InputStream stream = ArtefactTemplates.getResourceAsStream(BUILT_IN + template.resource)
        if (stream == null) {
            throw new IllegalStateException("The template ${template.resource} is missing from the command line")
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
        Source ofProject = findInProject(projectDirectory, projectPath(template))
        return ofProject ?: new Source(builtIn(template), null)
    }

    /**
     * The template a project has at a path, for a command whose templates are files of the project and
     * of nothing else, as those of a command of a feature are.
     *
     * @param projectDirectory the directory of the project
     * @param path the path of the template in the project, with {@code /} between the steps
     * @return the template, or {@code null} when the project has none there
     * @throws ArtefactTemplateException when the project has a template there that cannot be used: one
     * that is outside the project by a link, is no file, is too large or is not text
     */
    static Source findInProject(File projectDirectory, String path) {
        // a template is there when every step to it has the very name that is asked for: a file
        // system that takes TESTING for testing finds a file under either, and another does not
        Path file = projectDirectory.toPath()
        for (String step : path.split('/')) {
            if (!Files.isDirectory(file) || !ProjectOutputHandler.named(file, step)) {
                return null
            }
            file = file.resolve(step)
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
        String text
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString()
        } catch (CharacterCodingException e) {
            throw new ArtefactTemplateException("The template ${path} is not used: it is not text in UTF-8", e)
        }
        int nul = text.indexOf(0)
        if (nul >= 0) {
            throw new ArtefactTemplateException("The template ${path} is not used: line ${line(text, nul)}: it has a NUL character, which no source file has")
        }
        // one byte order mark at the start is no text of the template; any other is
        return new Source(!text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text, path)
    }

    /**
     * Renders a template for a class. Nothing of a template is run.
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
        return template.syntax == ArtefactTemplate.Syntax.TOKENS ? tokens(source.text, name, names) : placeholders(source.text, name, names)
    }

    /**
     * Renders a template of a project in which the tokens of {@link ArtefactTokens} are replaced.
     * Nothing of it is run.
     *
     * @param source the template, see {@link #findInProject(File, String)}
     * @param names the names of the class
     * @return the text of the file
     * @throws ArtefactTemplateException when the template cannot be rendered
     */
    static String renderTokens(Source source, Project names) {
        return tokens(source.text, source.projectPath, names)
    }

    private static String tokens(String text, String name, Project names) {
        String packageName = names.getPackageName() ?: ''
        Map<String, String> values = [
                (ArtefactTokens.PACKAGE_NAME): packageName,
                (ArtefactTokens.PACKAGE_PATH): packageName.replace('.', '/'),
                (ArtefactTokens.NAME): names.getClassName(),
                (ArtefactTokens.PROPERTY_NAME): names.getPropertyName()
        ]
        StringBuilder rendered = new StringBuilder(text.length() + 256)
        Matcher token = ArtefactTokens.TOKEN.matcher(text)
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
                throw new ArtefactTemplateException("The template ${name} cannot be rendered: line ${line(text, token.start())}: there is no @${shown(key)}@; " +
                        "a template has @${PACKAGE}@, @${values.keySet().join('@, @')}@")
            }
            from = token.end()
        }
        rendered.append(text, from, text.length())
        return rendered.toString()
    }

    /**
     * Renders a template that names what it wants as {@code ${className}}. The shell handed such a
     * template to the template engine of Groovy, which runs it; here a name in braces is replaced
     * and {@code \$} and {@code \\} are a dollar sign and a backslash, and everything the engine
     * would have had to evaluate is refused, so that no template is rendered differently than it
     * was, and none is run.
     */
    private static String placeholders(String text, String name, Project names) {
        String packageName = names.getPackageName() ?: ''
        String className = names.getClassName()
        // the names the templates of the shell's scripts were given
        Map<String, String> values = [
                className: className,
                fullName: packageName ? "${packageName}.${className}".toString() : className,
                propertyName: names.getPropertyName(),
                modelName: names.getPropertyName(),
                packageName: packageName,
                packagePath: packageName.replace('.', '/'),
                simpleName: className,
                lowerCaseName: NameUtils.getScriptName(className)
        ]
        // the text is not changed before it is read, so that a line is counted as the template has it
        String source = text
        boolean declared = !packageName.isEmpty()
        StringBuilder rendered = new StringBuilder(source.length() + 256)
        int i = 0
        while (i < source.length()) {
            char c = source.charAt(i)
            if (!declared && c == ('p' as char) && (i == 0 || source.charAt(i - 1) == LINE_FEED || source.charAt(i - 1) == CARRIAGE_RETURN)) {
                // the declaration of a package there is not, with the line it is and the empty line after it
                Matcher declaration = PACKAGE_LINE.matcher(source).region(i, source.length())
                if (declaration.lookingAt()) {
                    declared = true
                    i = declaration.end()
                    continue
                }
            }
            String next = i + 1 < source.length() ? source.substring(i + 1, i + 2) : ''
            if (c == BACKSLASH) {
                if (next != '$' && next != '\\') {
                    throw notRun(name, source, i, 'a backslash is written \\\\, and stands otherwise only in front of a $ that is to be written')
                }
                rendered.append(next)
                i += 2
            } else if (c == DOLLAR && next == '{') {
                Matcher placeholder = PLACEHOLDER.matcher(source).region(i, source.length())
                if (!placeholder.lookingAt()) {
                    throw notRun(name, source, i, 'between ${ and } there can only be one of the names ' + values.keySet().join(', ') + '; a $ that is to be written is \\$')
                }
                String value = values.get(placeholder.group(1))
                if (value == null) {
                    throw new ArtefactTemplateException("The template ${name} cannot be rendered: line ${line(source, i)}: it has no '${shown(placeholder.group(1))}'; " +
                            "a template has ${values.keySet().join(', ')}")
                }
                rendered.append(value)
                i = placeholder.end()
            } else if (c == DOLLAR && next && Character.isJavaIdentifierStart(next.charAt(0))) {
                throw notRun(name, source, i, 'a name is written in braces, as in ${className}, and a $ that is to be written is \\$')
            } else if (c == ('<' as char) && next == '%') {
                throw notRun(name, source, i, 'it has code between <% and %>')
            } else {
                rendered.append(c)
                i++
            }
        }
        return rendered.toString()
    }

    private static ArtefactTemplateException notRun(String name, String text, int at, String why) {
        return new ArtefactTemplateException("The template ${name} cannot be rendered: line ${line(text, at)}: ${why}. Templates are not run")
    }

    // a line ends with a line feed, with a carriage return, or with both
    private static int line(String text, int at) {
        Matcher end = LINE_END.matcher(text).region(0, at)
        int line = 1
        while (end.find()) {
            line++
        }
        return line
    }

    // one line, with nothing in it a terminal or the command line acts on, and not all of a long one
    private static String shown(String text) {
        return PackDescriptorParser.shown(text.readLines().join(' ').trim(), MAX_REASON).replace('@|', '@ |')
    }
}
