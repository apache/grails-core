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
package org.apache.grails.buildsrc

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

import groovy.transform.CompileStatic

/**
 * What the sources of the framework are at one moment, as far as Git can tell without reading them: one
 * identifier for every entry of the top directory of the repository that the published framework can be
 * built from.
 *
 * <p>It is what the repository of the tests is stamped with when it is published
 * ({@link TestRepositoryMarker}), and what a build that uses that repository without publishing it
 * ({@link PrebuiltTestRepository}) compares the sources with, to tell jars that are older than the sources
 * from jars that are not.</p>
 *
 * <p>Each entry hashes the sorted paths and Git blob identifiers of its files, overlaying working-tree
 * changes on HEAD. Git's clean filters normalize checkout line endings. Committing or staging identical
 * content does not change the fingerprint, nor do checkout paths or executable bits. The identifiers are the same in
 * another checkout of the same sources, which is what lets a repository that one job of a workflow
 * published be used by another, and they do not depend on the commit: a commit that changes Forge alone
 * leaves them as they are.</p>
 *
 * <p>The entries in {@link #NOT_FRAMEWORK} are left out, because nothing in them goes into what is
 * published: a developer who changes Forge only is never told that the repository is out of date. An
 * entry is the unit, so a change to the tests of a module counts as a change of the framework; that errs
 * on the side of publishing again.</p>
 */
@CompileStatic
class FrameworkSources {

    /**
     * The entries of the top directory that nothing of the published framework is built from. A Markdown
     * file of the top directory is left out as well.
     */
    static final Set<String> NOT_FRAMEWORK = [
            'grails-forge', 'end-to-end', 'grails-test-examples', 'grails-doc',
            '.github', '.agents', '.claude', '.idea', 'media',
            '.clinerules', '.cursorrules', '.windsurfrules',
    ] as Set<String>

    private static final String SEPARATOR = '\t'
    private static final String NUL = '\u0000'

    /** The commit that was checked out, or {@code null} when it is not known. */
    final String commit

    /** The number of files with changes that are not committed, in the entries that count. */
    final int uncommitted

    /** The identifier of every entry that counts, by its name, in the order of the names. */
    final SortedMap<String, String> entries

    FrameworkSources(String commit, int uncommitted, Map<String, String> entries) {
        this.commit = commit
        this.uncommitted = uncommitted
        this.entries = new TreeMap<String, String>(entries).asUnmodifiable()
    }

    /**
     * @param root the top directory of the repository
     * @return the sources as they are now, or {@code null} when Git cannot say: a source release has no
     * repository, and Git may not be installed
     */
    static FrameworkSources of(File root) {
        // the directory has to be the top of the repository itself: a source release that is unpacked inside
        // some other repository is not described by that repository
        String prefix = git(root, 'rev-parse', '--show-prefix')
        if (prefix == null || !prefix.trim().isEmpty()) {
            return null
        }
        String commit = git(root, 'rev-parse', 'HEAD')?.trim()
        String tree = git(root, 'ls-tree', '-r', '-z', 'HEAD')
        String status = git(root, 'status', '--porcelain=v1', '-z', '--untracked-files=all', '--no-renames')
        if (!commit || tree == null || status == null) {
            return null
        }
        Map<String, String> files = new TreeMap<String, String>()
        tree.split(NUL).each { String line ->
            // <mode> <type> <hash>\t<name>
            int tab = line.indexOf(SEPARATOR)
            if (tab > 0) {
                String name = line.substring(tab + 1)
                if (counts(topEntry(name))) {
                    files.put(name, line.substring(0, tab).split(' ').last())
                }
            }
        }
        int uncommitted = 0
        status.split(NUL).each { String line ->
            // XY <path>
            if (line.length() > 3) {
                String path = line.substring(3)
                if (counts(topEntry(path))) {
                    uncommitted++
                    File file = new File(root, path)
                    if (file.isFile()) {
                        String blob = git(root, 'hash-object', "--path=${path}".toString(), '--', path)?.trim()
                        if (!blob) {
                            throw new IllegalStateException("Cannot fingerprint framework source ${path}")
                        }
                        files.put(path, blob)
                    } else if (!file.exists()) {
                        files.remove(path)
                    } else {
                        throw new IllegalStateException("Cannot fingerprint framework source directory ${path}")
                    }
                }
            }
        }
        Map<String, List<String>> grouped = new TreeMap<String, List<String>>()
        files.each { String path, String blob ->
            grouped.computeIfAbsent(topEntry(path)) { String key -> [] as List<String> } << "${path}${NUL}${blob}".toString()
        }
        Map<String, String> entries = grouped.collectEntries { String entry, List<String> contents -> [(entry): sha256(contents.join(NUL))] }
        return new FrameworkSources(commit, uncommitted, entries)
    }

    /** @return whether an entry of the top directory is one the published framework can be built from */
    static boolean counts(String entry) {
        return !(entry in NOT_FRAMEWORK) && !entry.toLowerCase(Locale.ROOT).endsWith('.md')
    }

    /** @return one value for all the entries: equal for equal sources */
    String getFingerprint() {
        return sha256(entryLines().join('\n'))
    }

    /** @return the names of the entries that are not what they were in the earlier sources, in their order */
    List<String> changedSince(FrameworkSources earlier) {
        Set<String> names = new TreeSet<String>(entries.keySet())
        names.addAll(earlier.entries.keySet())
        return names.findAll { String name -> entries.get(name) != earlier.entries.get(name) }.toList()
    }

    /** @return the sources as lines, to be read again with {@link #parse(String)} */
    String toText() {
        return (["commit${SEPARATOR}${commit ?: ''}".toString(), "uncommitted${SEPARATOR}${uncommitted}".toString()] + entryLines()).join('\n') + '\n'
    }

    /** @return the sources {@link #toText()} wrote, or {@code null} when the text is not that */
    static FrameworkSources parse(String text) {
        List<String> lines = text.readLines().findAll { String line -> !line.isEmpty() }
        if (lines.size() < 2 || !lines[0].startsWith("commit${SEPARATOR}") || !lines[1].startsWith("uncommitted${SEPARATOR}")) {
            return null
        }
        Map<String, String> entries = [:]
        lines.drop(2).each { String line ->
            int tab = line.indexOf(SEPARATOR)
            if (tab > 0) {
                entries.put(line.substring(tab + 1), line.substring(0, tab))
            }
        }
        String uncommitted = lines[1].substring(lines[1].indexOf(SEPARATOR) + 1)
        return new FrameworkSources(lines[0].substring(lines[0].indexOf(SEPARATOR) + 1) ?: null, uncommitted.isInteger() ? uncommitted.toInteger() : 0, entries)
    }

    private List<String> entryLines() {
        return entries.collect { String name, String id -> "${id}${SEPARATOR}${name}".toString() }
    }

    private static String topEntry(String path) {
        return path.contains('/') ? path.substring(0, path.indexOf('/')) : path
    }

    private static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8))
    }

    private static String sha256(byte[] bytes) {
        return MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
    }

    // the output of a Git command in the directory, or null when it cannot be had
    private static String git(File directory, String... arguments) {
        try {
            ProcessBuilder builder = new ProcessBuilder((['git', '-c', 'core.quotePath=false'] + arguments.toList()) as List<String>)
            builder.directory(directory)
            builder.redirectError(ProcessBuilder.Redirect.DISCARD)
            // reading the state must not wait for, or get in the way of, a Git command of the developer
            builder.environment().put('GIT_OPTIONAL_LOCKS', '0')
            Process process = builder.start()
            process.outputStream.close()
            String output = new String(process.inputStream.readAllBytes(), StandardCharsets.UTF_8)
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            return process.exitValue() == 0 ? output : null
        } catch (IOException | InterruptedException ignored) {
            return null
        }
    }
}
