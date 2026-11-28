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
package org.apache.grails.forge.core.change

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.apache.grails.forge.core.change.ProjectTransaction.Write
import org.apache.grails.forge.core.state.ProjectState
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * The record of an application that was there before it had one, with the baseline this generator
 * writes for what the application is taken to be, worked out before anything is written: see
 * {@link FeatureAddition#adopt}. It also says how the files of the application compare with that
 * baseline, which shows how well the choices fit the application: a file of the baseline that the
 * application has otherwise, or does not have, is to a feature that is added later a change of the
 * application.
 *
 * @since 9.0
 */
@CompileStatic
final class RecordAdoption {

    /** The record as it is written: the manifest, the lock and the index of the baseline */
    final ProjectState record

    /** The paths of the files of the baseline the application has as this generator writes them, sorted */
    final List<String> asGenerated

    /** The paths of the files of the baseline the application has otherwise than this generator writes them, sorted */
    final List<String> otherwise

    /** The paths of the files of the baseline the application does not have, sorted */
    final List<String> absent

    private final Path project
    private final Path home
    private final List<Write> writes
    // what tells Git how to keep the record, where the project does not have it: written beside the change, which it is no part of
    private final Map<String, byte[]> forGit

    @PackageScope
    RecordAdoption(ProjectState record, Path project, Path home, List<String> asGenerated, List<String> otherwise, List<String> absent, List<Write> writes,
                   Map<String, byte[]> forGit) {
        this.forGit = Collections.unmodifiableMap(new LinkedHashMap<String, byte[]>(forGit))
        this.record = record
        this.project = project
        this.home = home
        this.asGenerated = Collections.unmodifiableList(new ArrayList<String>(asGenerated).sort())
        this.otherwise = Collections.unmodifiableList(new ArrayList<String>(otherwise).sort())
        this.absent = Collections.unmodifiableList(new ArrayList<String>(absent).sort())
        this.writes = Collections.unmodifiableList(new ArrayList<Write>(writes))
    }

    /**
     * @return the paths of the files of the record that are written or removed, in the order they are written in
     */
    List<String> getWritten() {
        return forGit.keySet().toList() + writes.collect { Write write -> write.path }
    }

    /**
     * Writes the baseline, its index, the lock and the manifest, all of them or none, with the lock
     * of the project and a journal by which a change that is interrupted is undone, as a feature is
     * added; and then, where the project does not have them, the two files that tell Git how to keep
     * the record. No file of the application outside {@code .grails} is written.
     *
     * @throws ProjectChangeException when the files could not be written, or one of them is not what
     * it was when the record was worked out; the message says whether the project is as it was
     */
    void apply() {
        apply { String path -> }
    }

    /**
     * @param written told the path of each file once it is written. What it throws is not handled:
     * the change then stays where it is, as it does when the program ends at that point, and is
     * undone by {@link FeatureAddition#recover(Path)}
     * @see #apply()
     */
    void apply(Consumer<String> written) {
        // a project without a record has no directory for it, which a change of a project expects to be there
        Path state = project.resolve(ProjectState.DIRECTORY)
        boolean made = false
        if (Files.exists(state, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(state, LinkOption.NOFOLLOW_LINKS)) {
            throw new ProjectChangeException("Cannot write ${ProjectState.MANIFEST}: ${ProjectState.DIRECTORY} is a file where a directory is needed")
        }
        if (!Files.exists(state, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(state)
                made = true
            } catch (IOException e) {
                throw new ProjectChangeException("The directory ${ProjectState.DIRECTORY} could not be created (${e.getClass().getSimpleName()}); nothing of the project was changed", e)
            }
        }
        try {
            ProjectTransaction.run(project, home, writes, written)
        } catch (ProjectChangeException e) {
            if (made) {
                removeWhenUnused(state)
            }
            throw e
        }
        for (Map.Entry<String, byte[]> file : forGit.entrySet()) {
            Path target = project.resolve(file.key)
            try {
                if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    Files.write(target, file.value)
                }
            } catch (IOException e) {
                throw new ProjectChangeException("The record was written, and ${file.key}, which tells Git how to keep it, could not be (${e.getClass().getSimpleName()}): " +
                        'the record reads without it, and the next project migrate --force writes it', e)
            }
        }
    }

    // the directory that was made for the record goes again when nothing was written to it but the lock of the change
    private static void removeWhenUnused(Path state) {
        try {
            Path transactions = state.resolve('transactions')
            List<String> inState = names(state)
            if (inState.isEmpty()) {
                Files.delete(state)
            } else if (inState == ['transactions'] && names(transactions).every { String name -> name == 'lock' }) {
                Files.deleteIfExists(transactions.resolve('lock'))
                Files.delete(transactions)
                Files.delete(state)
            }
        } catch (IOException ignored) {
            // what is left is an empty directory, which the next command uses
        }
    }

    private static List<String> names(Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return []
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.collect(Collectors.toList()).collect { Path each -> each.getFileName().toString() }
        }
    }
}
