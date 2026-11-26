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

import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Matcher
import java.util.regex.Pattern
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ToolCatalogFile

/**
 * {@code tools}: what of the tools of the project is written as Grails 9 deprecates it or no longer
 * has it. The sources are read as text, within bounds, and nothing is compiled:
 * <ul>
 * <li>{@code grails7-imports}, an error: a source of the project that imports a type of
 * {@code grails.dev.commands}, the commands of Grails 7, which Grails 9 does not have, so that the
 * source does not compile. Each such source is named, with the import to write instead;</li>
 * <li>{@code legacy-folder}, advice: sources in {@code grails-app/commands}, which is deprecated, and
 * which the build still compiles with the tools of {@code src/cli};</li>
 * <li>{@code legacy-type}, advice: tools of the application that implement {@code ApplicationCommand}
 * or {@code GrailsApplicationCommand}, which are deprecated, as the catalog of the tools says.</li>
 * </ul>
 *
 * @since 9.0
 */
@CompileStatic
final class ToolsCheck extends HealthCheck {

    /** The package of the types of the commands of Grails 7. */
    static final String GRAILS_7 = 'grails.dev.commands'

    /** The package of the types of the tools, which has a type of the same name for each of them. */
    static final String TOOLS = 'org.apache.grails.core.cli'

    /** The deprecated folder of the sources of tools. */
    static final String LEGACY_FOLDER = 'grails-app/commands'

    /** Where sources are looked at for an import of {@link #GRAILS_7}. */
    static final List<String> SOURCE_FOLDERS = ['src/cli', 'src/standalone', LEGACY_FOLDER, 'src/main/groovy'].asImmutable()

    /** The endings of the sources that are looked at. */
    static final List<String> ENDINGS = ['.groovy', '.java'].asImmutable()

    /** How many entries of a folder are looked at, at most. */
    static final int MAX_WALKED = 4096

    /** How deep a folder is looked at, at most. */
    static final int MAX_DEPTH = 24

    /** How many files a finding names, at most. */
    static final int MAX_NAMED = 20

    private static final Pattern IMPORT = Pattern.compile(/(?m)^[ \t]*import[ \t]+(?:static[ \t]+)?grails\.dev\.commands\.([A-Za-z0-9_.*]+)/)

    @Override
    String getName() {
        return 'tools'
    }

    @Override
    List<HealthFinding> check(HealthContext context) {
        List<HealthFinding> findings = []
        Map<String, List<String>> imports = new LinkedHashMap<>()
        List<String> legacy = []
        boolean[] cut = [false] as boolean[]
        for (String folder : SOURCE_FOLDERS) {
            for (Path source : sources(context.directory.resolve(folder), cut)) {
                String path = context.relative(source)
                if (folder == LEGACY_FOLDER) {
                    legacy.add(path)
                }
                String text = HealthContext.text(source, HealthContext.MAX_FILE)
                if (text == null) {
                    continue
                }
                List<String> named = []
                Matcher matcher = IMPORT.matcher(text)
                while (matcher.find()) {
                    named.add(matcher.group(1))
                }
                if (!named.isEmpty()) {
                    imports.put(path, named.unique())
                }
            }
        }
        for (Map.Entry<String, List<String>> imported : imports.entrySet()) {
            String path = imported.key
            List<String> named = imported.value
            findings << found('grails7-imports', HealthLevel.ERROR,
                    "${path} imports ${named.collect { String each -> "${GRAILS_7}.${each}" }.join(', ')}, which Grails ${context.commandLineMajor} does not have: " +
                            "import ${named.collect { String each -> "${TOOLS}.${each}" }.join(', ')}",
                    "Change the ${named.size() == 1 ? 'import' : 'imports'} of ${path} to ${TOOLS}: a tool implements ${TOOLS}.ApplicationTool with ToolSupport, " +
                            "or, with no other change, the deprecated ${TOOLS}.GrailsApplicationCommand, whose members are the same", null, [path], NOTE_TOOLS_GRAILS_7)
        }
        if (!legacy.isEmpty()) {
            findings << found('legacy-folder', HealthLevel.ADVICE, "${LEGACY_FOLDER} is deprecated, and has ${legacy.size()} ${legacy.size() == 1 ? 'source' : 'sources'}: " +
                    'the classes in it are compiled and run as before, and are no longer packaged with the application',
                    "Move them to src/cli/groovy, as they are; a new tool is written there by: ${context.toType('create-tool NAME')}", null, named(legacy),
                    NOTE_TOOLS_RENAMED)
        }
        ToolCatalogFile catalog = ToolCatalogFile.read(context.directory)
        if (catalog.state == ToolCatalogFile.State.CURRENT || catalog.state == ToolCatalogFile.State.STALE) {
            List<String> typed = catalog.tools.findAll { ToolCatalogFile.Entry tool -> tool.origin == ToolCatalogFile.APPLICATION && tool.isOfDeprecatedType() }*.name.sort()
            if (!typed.isEmpty()) {
                findings << found('legacy-type', HealthLevel.ADVICE, "${typed.size() == 1 ? 'The tool' : 'The tools'} ${typed.join(', ')} of the application " +
                        "${typed.size() == 1 ? 'implements' : 'implement'} ApplicationCommand or GrailsApplicationCommand, which are deprecated",
                        'Implement ApplicationTool instead (for GrailsApplicationCommand: ApplicationTool, ToolSupport); nothing else changes', null,
                        [ToolCatalogFile.LOCATION], NOTE_TOOLS_RENAMED)
            }
        }
        if (!findings.isEmpty()) {
            return findings
        }
        if (cut[0]) {
            return [skipped("The folders of the sources of the project have more than ${MAX_WALKED} entries, or are deeper than ${MAX_DEPTH}, so not every source was read")]
        }
        return [ok("No source imports ${GRAILS_7}, ${LEGACY_FOLDER} has no source, and no tool of the application implements a deprecated type")]
    }

    // the files a finding names: as many as it shows, and how many more there are
    private static List<String> named(List<String> files) {
        if (files.size() <= MAX_NAMED) {
            return files
        }
        List<String> named = new ArrayList<String>(files.subList(0, MAX_NAMED))
        named.add("(and ${files.size() - MAX_NAMED} more)".toString())
        return named
    }

    // The sources below a folder, in the order of their paths: regular files of one of the endings, no
    // link and nothing behind one. No more is looked at than a bounded number of entries; cut says that
    // something was left.
    private static List<Path> sources(Path folder, boolean[] cut) {
        List<Path> found = []
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
            return found
        }
        int[] seen = [0] as int[]
        try {
            Files.walkFileTree(folder, Collections.<FileVisitOption>emptySet(), MAX_DEPTH, new SimpleFileVisitor<Path>() {

                @Override
                FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    return count()
                }

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isDirectory()) {
                        // at the depth that is the last looked at, a directory is given as a file
                        cut[0] = true
                    } else if (attributes.isRegularFile() && ENDINGS.any { String ending -> file.getFileName().toString().endsWith(ending) }) {
                        found.add(file)
                    }
                    return count()
                }

                @Override
                FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE
                }

                private FileVisitResult count() {
                    if (++seen[0] > MAX_WALKED) {
                        cut[0] = true
                        return FileVisitResult.TERMINATE
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (IOException ignored) {
            cut[0] = true
        }
        found.sort()
        return found
    }
}
