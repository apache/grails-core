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
package org.apache.grails.forge.cli.health

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.regex.Pattern
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.WrapperFiles
import org.grails.forge.cli.command.ProjectRegenerateCommand

/**
 * {@code git-files}: what the {@code .gitignore} and {@code .gitattributes} files of the project
 * and of the directories above it, up to the root of its Git repository, say of the record under
 * {@code .grails} and of the wrappers. The files are read as text; Git is not run, so the ignore
 * files of the user ({@code core.excludesFile}) and {@code .git/info/exclude} are not read. A
 * {@code .gitignore} in a directory decides for what is below it, a deeper one before one above, and
 * nothing below a directory that is left out is committed, as Git does it.
 *
 * @since 9.0
 */
@CompileStatic
final class GitFilesCheck extends HealthCheck {

    private static final String IGNORE = '.gitignore'
    private static final String ATTRIBUTES = '.gitattributes'
    private static final String RECORD = '.grails'
    // what is under .grails and is of one machine, which create-app has the .gitignore leave out, each with whether it is a directory
    private static final Map<String, Boolean> OF_ONE_MACHINE = ['.grails/transactions': true, '.grails/history': false].asImmutable()
    // a name below a directory, for whether the directory's own .gitignore leaves out everything in it
    private static final String ANY_NAME = 'anything'
    private static final String LF = 'eol=lf'
    private static final String CRLF = 'eol=crlf'
    private static final String LINES = '*.bat text eol=crlf, gradlew text eol=lf, grailsw text eol=lf and *.jar binary'

    @Override
    String getName() {
        return 'git-files'
    }

    @Override
    List<HealthFinding> check(HealthContext context) {
        Path top = top(context.project)
        List<HealthFinding> findings = []
        Decision record = decision(top, context.directory.resolve(RECORD), true)
        if (record.ignored) {
            findings << found('record-ignored', HealthLevel.WARNING, "${context.relative(record.file)} leaves out ${RECORD}, so the record of the project is not committed",
                    "Take the pattern out of ${context.relative(record.file)}, and leave out .grails/cache/, .grails/transactions/, .grails/run/ and .grails/history in " +
                            'its place, as create-app writes them', null, [context.relative(record.file)], NOTE_RECORD)
        } else {
            List<String> kept = OF_ONE_MACHINE.findAll { String path, Boolean directory -> !leftOut(top, context.directory.resolve(path), directory) }.keySet().toList()
            if (!kept.isEmpty()) {
                findings << found('state-ignored', HealthLevel.ADVICE, "No ${IGNORE} of the project leaves out ${kept.join(' and ')}, which " +
                        "${kept.size() == 1 ? 'is' : 'are'} of one machine: what a change that did not end leaves, and the history of the interactive console",
                        "Add ${kept.collect { String path -> OF_ONE_MACHINE.get(path) ? path + '/' : path }.join(' and ')} to the ${IGNORE} of the project, as " +
                                'create-app writes them', null, [IGNORE], NOTE_RECORD)
            }
        }
        List<String> converted = converted(context, top)
        if (!converted.isEmpty()) {
            findings << found('wrapper-line-endings', HealthLevel.ADVICE, "No ${ATTRIBUTES} keeps the line endings of ${converted.join(', ')}, so Git can change " +
                    "${converted.size() == 1 ? 'it' : 'them'} on a clone, and a wrapper with the line endings of another system does not run",
                    "Add to the ${ATTRIBUTES} of the project the lines create-app writes: ${LINES}", null, [ATTRIBUTES], null)
        }
        return findings ?: [ok("${RECORD} is committed, what is of one machine under it is left out, and the line endings of the wrappers are kept", [IGNORE, ATTRIBUTES])]
    }

    // The directory the ignore files are read from down to the project: the root of the Git repository the project is in, or,
    // outside one, the root of the build the project is part of, or the project
    private static Path top(ConsoleProject project) {
        Path each = project.directory
        for (int level = 0; each != null && level <= ConsoleProject.MAX_LEVELS; level++) {
            if (Files.exists(each.resolve('.git'), LinkOption.NOFOLLOW_LINKS)) {
                return each
            }
            each = each.getParent()
        }
        return project.buildRoot ?: project.directory
    }

    // whether a path is left out, by itself or with a directory above it, or, for a directory, by its own .gitignore that leaves out everything in it
    private static boolean leftOut(Path top, Path path, boolean directory) {
        if (decision(top, path, directory).ignored) {
            return true
        }
        if (!directory) {
            return false
        }
        ProjectRegenerateCommand.IgnoreRules own = rules(path.resolve(IGNORE))
        return own != null && own.decides(ANY_NAME, true) && own.decides(ANY_NAME, false)
    }

    // whether Git leaves a path out, and by which .gitignore: a directory above it that is left out leaves it out
    private static Decision decision(Path top, Path path, boolean directory) {
        if (!path.startsWith(top)) {
            return new Decision(false, null)
        }
        List<Path> steps = []
        Path each = path
        while (each != top) {
            steps.add(0, each)
            each = each.getParent()
        }
        for (int i = 0; i < steps.size(); i++) {
            Path step = steps[i]
            boolean isDirectory = i < steps.size() - 1 || directory
            Path decidedBy = null
            Boolean ignored = null
            // from the directory that holds it up to the top: the deepest file that has a line for it decides
            Path holder = step.getParent()
            while (holder != null && holder.startsWith(top) && ignored == null) {
                ProjectRegenerateCommand.IgnoreRules rules = rules(holder.resolve(IGNORE))
                ignored = rules?.decides(holder.relativize(step).join('/'), isDirectory)
                decidedBy = holder.resolve(IGNORE)
                holder = holder == top ? null : holder.getParent()
            }
            if (ignored) {
                return new Decision(true, decidedBy)
            }
        }
        return new Decision(false, null)
    }

    private static ProjectRegenerateCommand.IgnoreRules rules(Path file) {
        String text = HealthContext.text(file, HealthContext.MAX_FILE)
        return text == null ? null : new ProjectRegenerateCommand.IgnoreRules(text)
    }

    // the files of the wrappers the project has whose line endings no .gitattributes keeps
    private static List<String> converted(HealthContext context, Path top) {
        Map<Path, String> needed = [:]
        ConsoleProject.Wrapper gradle = context.project.gradleWrapper(false)
        Path gradleDirectory = gradle.file?.getParent()
        Path grailsDirectory = WrapperFiles.directoryOf(context.project)
        if (gradleDirectory != null) {
            needed.put(gradleDirectory.resolve('gradlew'), LF)
            needed.put(gradleDirectory.resolve('gradlew.bat'), CRLF)
            needed.put(gradleDirectory.resolve('gradle/wrapper/gradle-wrapper.jar'), null)
        }
        if (grailsDirectory != null) {
            needed.put(grailsDirectory.resolve(WrapperFiles.SCRIPT), LF)
            needed.put(grailsDirectory.resolve(WrapperFiles.BATCH), CRLF)
            needed.put(grailsDirectory.resolve(WrapperFiles.JAR), null)
        }
        return needed.findAll { Path file, String eol -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !kept(top, file, eol) }
                .keySet().collect { Path file -> context.relative(file) }
    }

    // whether the .gitattributes files from the top down to the file keep it as it is: with the line endings it needs, or as no text at all
    private static boolean kept(Path top, Path file, String eol) {
        Map<String, String> attributes = [:]
        List<Path> holders = []
        Path each = file.getParent()
        while (each != null && each.startsWith(top)) {
            holders.add(0, each)
            each = each == top ? null : each.getParent()
        }
        for (Path holder : holders) {
            String text = HealthContext.text(holder.resolve(ATTRIBUTES), HealthContext.MAX_FILE)
            for (String line : (text == null ? Collections.<String>emptyList() : text.readLines())) {
                List<String> words = line.trim().split(/\s+/).toList()
                if (words.size() < 2 || words[0].startsWith('#') || !matches(words[0], holder.relativize(file).join('/'))) {
                    continue
                }
                for (String word : words.drop(1)) {
                    if (word == 'binary' || word == '-text') {
                        attributes.put('text', 'unset')
                    } else if (word == 'text' || word.startsWith('text=')) {
                        attributes.put('text', 'set')
                    } else if (word.startsWith('eol=')) {
                        attributes.put('eol', word)
                    }
                }
            }
        }
        return attributes.get('text') == 'unset' || (eol != null && attributes.get('eol') == eol)
    }

    // whether a pattern of .gitattributes matches a path below the directory of the file: one without a slash matches the name anywhere
    private static boolean matches(String pattern, String path) {
        boolean anywhere = !pattern.contains('/')
        String glob = pattern.startsWith('/') ? pattern.substring(1) : pattern
        StringBuilder expression = new StringBuilder(anywhere ? '(?:.*/)?' : '')
        for (String part : glob.split(/(?<=[*?])|(?=[*?])/)) {
            expression.append(part == '*' ? '[^/]*' : part == '?' ? '[^/]' : part.isEmpty() ? '' : Pattern.quote(part))
        }
        return path ==~ expression.toString()
    }

    /** Whether a path is left out, and the .gitignore that leaves it out. */
    @CompileStatic
    private static final class Decision {

        final boolean ignored
        final Path file

        Decision(boolean ignored, Path file) {
            this.ignored = ignored
            this.file = file
        }
    }
}
