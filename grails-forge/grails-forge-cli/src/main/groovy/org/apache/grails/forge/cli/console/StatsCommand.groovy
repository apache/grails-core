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
package org.apache.grails.forge.cli.console

import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Matcher
import java.util.regex.Pattern
import groovy.transform.CompileStatic
import picocli.CommandLine

/**
 * {@code stats}: the number of source files of the project and of their lines of code, by the kind of
 * class. Gradle is not run for it, and nothing of the project is loaded: the files are read as text.
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'stats', description = 'Prints how many source files the Grails project has, and how many lines of code, by the kind of class')
@CompileStatic
class StatsCommand extends InProjectCommand {

    /** How many files are looked at, at most. */
    public static final int MAX_FILES = 100_000

    /** The size above which a file is counted as a file and its lines are not read. */
    public static final long MAX_FILE_BYTES = 16L * 1024 * 1024

    private static final int MAX_DEPTH = 64
    private static final Pattern EMPTY = Pattern.compile(/^\s*$/)
    private static final Pattern LINE_COMMENT = Pattern.compile(/^\s*\/\/.*/)
    private static final Pattern BLOCK_COMMENT = Pattern.compile(/^(.*)\/\*(.*)\*\/(.*)$/)

    @Override
    protected String notDone() {
        return 'Nothing was counted'
    }

    @Override
    protected int run(ConsoleProject project) {
        List<Kind> kinds = kinds()
        int[] seen = [0] as int[]
        List<Path> notRead = []
        List<String> linked = []
        for (String directory : kinds*.directory.unique()) {
            // what is reached through a link, src itself for one, is not a source directory of this project
            if (project.isLinked(directory)) {
                linked.add(directory)
                continue
            }
            Path root = project.directory.resolve(directory)
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                continue
            }
            List<Kind> inDirectory = kinds.findAll { Kind kind -> kind.directory == directory }
            boolean complete = count(root, inDirectory, seen, notRead)
            if (!complete) {
                err("The project in ${GradleRunner.line(project.directory)} has more than ${MAX_FILES} files in its source directories, which is more than is counted. Nothing is reported")
                return 1
            }
        }
        print(kinds)
        if (linked) {
            warning("Not counted, because it is reached through a link and links are not followed: ${linked.join(', ')}")
        }
        if (notRead) {
            warning("The lines of ${notRead.size()} file(s) larger than ${MAX_FILE_BYTES.intdiv(1024 * 1024)} MiB were not counted, the first of them ${GradleRunner.line(project.directory.relativize(notRead[0]))}")
        }
        return 0
    }

    private static List<Kind> kinds() {
        return [
                new Kind('Controllers', 'grails-app/controllers', ['Controller.groovy']),
                new Kind('URL Mappings', 'grails-app/controllers', ['UrlMappings.groovy']),
                new Kind('Interceptors', 'grails-app/controllers', ['Interceptor.groovy']),
                new Kind('Domain Classes', 'grails-app/domain', ['.groovy']),
                new Kind('Jobs', 'grails-app/jobs', ['.groovy']),
                new Kind('Services', 'grails-app/services', ['Service.groovy']),
                new Kind('Tag Libraries', 'grails-app/taglib', ['TagLib.groovy']),
                new Kind('Helpers', 'src/main/groovy', ['.groovy', '.java']),
                new Kind('Unit Tests', 'src/test/groovy', ['.groovy', '.java']),
                new Kind('Integration Tests', 'src/integration-test/groovy', ['.groovy', '.java']),
                new Kind('Scripts', 'src/main/scripts', ['.groovy'])
        ]
    }

    // Links are not followed: what a link points to is not a source file of this project
    private static boolean count(Path root, List<Kind> kinds, int[] seen, List<Path> notRead) {
        boolean[] complete = [true] as boolean[]
        Files.walkFileTree(root, Collections.emptySet(), MAX_DEPTH, new SimpleFileVisitor<Path>() {

            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (++seen[0] > MAX_FILES) {
                    complete[0] = false
                    return FileVisitResult.TERMINATE
                }
                if (!attributes.isRegularFile()) {
                    return FileVisitResult.CONTINUE
                }
                String name = file.getFileName().toString()
                Kind kind = kinds.find { Kind each -> each.endings.any { String ending -> name.endsWith(ending) } }
                if (kind != null) {
                    kind.files++
                    if (attributes.size() > MAX_FILE_BYTES) {
                        notRead.add(file)
                    } else {
                        kind.lines += linesOfCode(file)
                    }
                }
                return FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult visitFileFailed(Path file, IOException e) {
                return FileVisitResult.CONTINUE
            }
        })
        return complete[0]
    }

    // A line counts when it is not empty, not a comment of one line and not within a comment of several
    private static int linesOfCode(Path file) {
        int lines = 0
        int comment = 0
        try {
            // a file in another encoding is read all the same: what cannot be decoded is replaced
            new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8).withCloseable { Reader reader ->
                BufferedReader buffered = new BufferedReader(reader)
                String line
                while ((line = buffered.readLine()) != null) {
                    if (EMPTY.matcher(line).matches() || LINE_COMMENT.matcher(line).matches()) {
                        continue
                    }
                    Matcher block = BLOCK_COMMENT.matcher(line)
                    if (block.matches() && EMPTY.matcher(block.group(1)).matches() && EMPTY.matcher(block.group(3)).matches()) {
                        continue
                    }
                    int open = line.indexOf('/*')
                    int close = line.indexOf('*/')
                    if (open != -1 && (close - open) <= 1) {
                        comment++
                    } else if (close != -1 && comment > 0) {
                        comment--
                    }
                    if (comment == 0) {
                        lines++
                    }
                }
            }
        } catch (IOException ignored) {
            // a file that cannot be read has no lines that can be counted
        }
        return lines
    }

    private void print(List<Kind> kinds) {
        String rule = '+----------------------+-------+-------+'
        int files = 0
        int lines = 0
        out('')
        out(rule)
        out('| Name                 | Files |  LOC  |')
        out(rule)
        for (Kind kind : kinds) {
            if (kind.files > 0) {
                out("| ${kind.name.padRight(20)} | ${String.valueOf(kind.files).padLeft(5)} | ${String.valueOf(kind.lines).padLeft(5)} | ")
                files += kind.files
                lines += kind.lines
            }
        }
        out(rule)
        out("| Totals               | ${String.valueOf(files).padLeft(5)} | ${String.valueOf(lines).padLeft(5)} | ")
        out(rule)
        out('')
    }

    @CompileStatic
    private static class Kind {

        final String name
        final String directory
        final List<String> endings
        int files
        int lines

        Kind(String name, String directory, List<String> endings) {
            this.name = name
            this.directory = directory
            this.endings = endings
        }
    }
}
