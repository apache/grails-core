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

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.apache.grails.forge.core.change.ProjectTransaction.Write
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.function.Consumer

/**
 * What adding features to a project comes to, worked out for every file before any is written: see
 * {@link FeatureAddition#plan}. Nothing of the project is changed until {@link #apply()}.
 *
 * @since 9.0
 */
@CompileStatic
final class ChangePlan {

    /**
     * What becomes of a file of the project.
     */
    static enum Action {

        /** The features write a file the project has not */
        ADD,

        /** The features change a file the project has as it was generated */
        UPDATE,

        /** The features change a file that was changed in the project too, in other places: it gets the changes of the features and keeps its own */
        MERGE,

        /** The project already has the file as the features write or change it; it is not written */
        ADOPT,

        /** The file cannot be written without losing something of the project */
        CONFLICT
    }

    /**
     * A file of the project the features write, change or cannot change.
     */
    @CompileStatic
    static final class PlannedFile {

        final String path
        final Action action

        /** For a conflict, why the file cannot be written; {@code null} otherwise */
        final String reason

        private final boolean binary
        private final byte[] current
        private final byte[] result
        private final Boolean executable
        private final byte[] base
        private final byte[] proposed

        @PackageScope
        PlannedFile(String path, Action action, String reason, boolean binary, byte[] current, byte[] result, Boolean executable, byte[] base, byte[] proposed) {
            this.path = path
            this.action = action
            this.reason = reason
            this.binary = binary
            this.current = current
            this.result = result
            this.executable = executable
            this.base = base
            this.proposed = proposed
        }

        boolean isWritten() {
            return action == Action.ADD || action == Action.UPDATE || action == Action.MERGE
        }
    }

    /** The features that were asked for and are added, by their names */
    final List<String> requested

    /** The features that were asked for and that the application has already */
    final List<String> alreadyThere

    /** Every feature the application gains: those that were asked for, and what they require */
    final List<String> added

    /** The files the features write, change or cannot change, in the order of their paths. A file the features leave as it is is not among them */
    final List<PlannedFile> files

    private final Path project
    private final List<Write> state

    @PackageScope
    ChangePlan(Path project, List<String> requested, List<String> alreadyThere, List<String> added, List<PlannedFile> files, List<Write> state) {
        this.project = project
        this.requested = Collections.unmodifiableList(new ArrayList<String>(requested))
        this.alreadyThere = Collections.unmodifiableList(new ArrayList<String>(alreadyThere))
        this.added = Collections.unmodifiableList(new ArrayList<String>(added))
        this.files = Collections.unmodifiableList(new ArrayList<PlannedFile>(files))
        this.state = state
    }

    /**
     * @return whether the application has every feature that was asked for, so that there is nothing to add
     */
    boolean isNothingToAdd() {
        return requested.isEmpty()
    }

    /**
     * @return the files that cannot be written. When there is one, nothing can be applied
     */
    List<PlannedFile> getConflicts() {
        return files.findAll { PlannedFile file -> file.action == Action.CONFLICT }
    }

    /**
     * What would change in the files of the project, as a unified diff of each file that is written
     * from what the project has to what it would have. For a file that cannot be written, the change
     * the features make to it is shown instead, marked as rejected. A line has no control character
     * but the tabulator.
     */
    List<String> diff() {
        List<String> lines = []
        for (PlannedFile file : files) {
            if (file.action == Action.ADOPT) {
                continue
            }
            boolean conflict = file.action == Action.CONFLICT
            byte[] from = conflict ? (file.base ?: file.current) : file.current
            byte[] to = conflict ? file.proposed : file.result
            if (conflict) {
                lines << "rejected: ${file.path} (${file.reason})".toString()
            }
            if (file.binary) {
                lines << "Binary file ${file.path} ${from == null ? 'is added' : 'changes'}".toString()
                continue
            }
            List<String> before = from == null ? [] as List<String> : text(from)
            List<String> after = text(to)
            List<String> unified = UnifiedDiffUtils.generateUnifiedDiff(from == null ? '/dev/null' : 'a/' + file.path, 'b/' + file.path, before, DiffUtils.diff(before, after), 3)
            if (unified.isEmpty()) {
                lines << "${file.path}: whether the file can be executed changes".toString()
            }
            lines.addAll(unified)
        }
        return lines.collect { String line -> line.replaceAll(/[\p{Cc}&&[^\t]]/, '?') }
    }

    private static List<String> text(byte[] bytes) {
        List<String> lines = new String(bytes, StandardCharsets.UTF_8).split('\n', -1).toList()
        return lines.last().isEmpty() ? lines.subList(0, lines.size() - 1) : lines
    }

    /**
     * Writes the files and the new state of the project, all of them or none.
     *
     * @throws IllegalStateException for a plan with conflicts
     * @throws ProjectChangeException when the files could not be written, or one of them is not what
     * it was when the plan was made; the message says whether the project is as it was
     */
    void apply() {
        apply { String path -> }
    }

    /**
     * Writes the files and the new state of the project, all of them or none.
     *
     * @param written told the path of each file once it is written, the files of the state under
     * {@code .grails} among them. What it throws is not handled: the change then stays where it is,
     * as it does when the program ends at that point, and is undone by {@link FeatureAddition#recover(Path)}
     * @throws IllegalStateException for a plan with conflicts
     * @throws ProjectChangeException when the files could not be written, or one of them is not what
     * it was when the plan was made; the message says whether the project is as it was
     */
    void apply(Consumer<String> written) {
        if (!getConflicts().isEmpty()) {
            throw new IllegalStateException('A plan with conflicts cannot be applied')
        }
        if (isNothingToAdd()) {
            return
        }
        List<Write> writes = []
        for (PlannedFile file : files) {
            if (file.isWritten()) {
                writes << new Write(file.path, file.result, file.executable, file.current == null ? null : ProjectTransaction.sha256(file.current))
            }
        }
        writes.addAll(state)
        ProjectTransaction.run(project, writes, written)
    }
}
