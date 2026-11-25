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

import com.github.difflib.UnifiedDiffUtils
import com.github.difflib.patch.ChangeDelta
import com.github.difflib.patch.Chunk
import com.github.difflib.patch.DeleteDelta
import com.github.difflib.patch.InsertDelta
import com.github.difflib.patch.Patch
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.apache.grails.forge.core.change.ProjectTransaction.Write
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.function.Consumer
import java.util.regex.Pattern

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
        CONFLICT,

        /**
         * The features change a file that an application which was there before it had a record does
         * not have: the file is not written, the features are added without it, and what they would
         * have changed in it is said
         *
         * @since 9.0
         */
        LEAVE,

        /** The features leave out a file that a feature they replace wrote, and the project has it as it was generated: it is removed. A plan that is refused shows the file so, and removes nothing */
        REMOVE
    }

    /**
     * A file of the project the features write, change or cannot change.
     */
    @CompileStatic
    static final class PlannedFile {

        final String path
        final Action action

        /** For a conflict, why the file cannot be written, and for a file that is left out, why it is; {@code null} otherwise */
        final String reason

        /**
         * For a conflict, what is in the way and what to do about it, a line each: for lines that
         * both changed, the lines of the two. No line has a character that acts on a terminal.
         */
        final List<String> details

        private final boolean binary
        private final byte[] current
        private final byte[] result
        private final Boolean executable
        private final byte[] base
        private final byte[] proposed

        @PackageScope
        PlannedFile(String path, Action action, String reason, boolean binary, byte[] current, byte[] result, Boolean executable, byte[] base, byte[] proposed,
                    List<String> details) {
            this.details = Collections.unmodifiableList(details.collect { String line -> safe(line) })
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

        boolean isRemoved() {
            return action == Action.REMOVE
        }
    }

    /** The most lines {@link #diff()} shows of one file */
    public static final int MAX_DIFF_OF_FILE = 400

    /** The most lines {@link #diff()} shows */
    public static final int MAX_DIFF = 2000

    // control, format and private-use characters but the tabulator, unpaired surrogates, line and paragraph separators
    private static final Pattern UNSAFE = Pattern.compile(/[\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}&&[^\t]]/)

    /** The features that were asked for and are added, by their names */
    final List<String> requested

    /** The features that were asked for and that the application has already */
    final List<String> alreadyThere

    /** Every feature the application gains: those that were asked for, and what they require */
    final List<String> added

    /** The files the features write, change or cannot change, in the order of their paths. A file the features leave as it is is not among them */
    final List<PlannedFile> files

    /**
     * Why the features are not added, whatever the files are: they would take away something the
     * application was created with by name, an option or a feature that was asked for. {@code null}
     * otherwise. The files of such a plan show what would be added and removed, and it cannot be
     * applied.
     */
    final String refusal

    /** The features the application would lose, for a plan that is refused */
    final List<String> lostFeatures

    /**
     * The features the application has by default and has no more once the plan is applied, because a
     * feature that is added takes their place: the default of an exclusive group for another of the
     * group, and what only that default brought. Empty for a plan that is refused.
     */
    final List<String> replaced

    /** What is said of each of {@link #replaced}, in their order: which feature replaces it, and what it was */
    final List<String> replacements

    /** The options the application would have in the place of those it was created with, as the create commands take them, for a plan that is refused */
    final List<String> changedOptions

    private final Path project
    private final Path home
    private final List<Write> state

    @PackageScope
    ChangePlan(Path project, Path home, List<String> requested, List<String> alreadyThere, List<String> added, List<PlannedFile> files, List<Write> state,
               String refusal, List<String> lostFeatures, List<String> changedOptions, List<String> replaced, List<String> replacements) {
        this.replaced = Collections.unmodifiableList(new ArrayList<String>(replaced))
        this.replacements = Collections.unmodifiableList(replacements.collect { String line -> safe(line) })
        this.project = project
        this.home = home
        this.refusal = refusal
        this.lostFeatures = Collections.unmodifiableList(new ArrayList<String>(lostFeatures))
        this.changedOptions = Collections.unmodifiableList(new ArrayList<String>(changedOptions))
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
     * @return whether the features are not added because they would take away something the
     * application has
     */
    boolean isRefused() {
        return refusal != null
    }

    /**
     * What would change in the files of the project, as a unified diff of each file that is written
     * from what the project has to what it would have. For a file that cannot be written, the change
     * the features make to it is shown instead, marked as rejected; for a file that is removed, and
     * for one a refused plan would leave out, its removal. A line has no control or format character but the tabulator, and the
     * carriage return of a line that ends with CR LF is not shown. At most {@link #MAX_DIFF_OF_FILE}
     * lines are shown of a file and {@link #MAX_DIFF} of all of them; a line says how many were
     * left out.
     */
    List<String> diff() {
        List<String> lines = []
        int left = 0
        for (PlannedFile file : files) {
            if (file.action == Action.ADOPT) {
                continue
            }
            List<String> ofFile = diff(file)
            if (lines.size() + ofFile.size() > MAX_DIFF) {
                left += ofFile.size()
                continue
            }
            lines.addAll(ofFile)
        }
        if (left > 0) {
            lines << "... ${left} more lines of other files are not shown".toString()
        }
        return lines.collect { String line -> safe(line) }
    }

    private static List<String> diff(PlannedFile file) {
        List<String> lines = []
        // what the features change in a file that is left out is shown as what they change in one that cannot be written is
        boolean left = file.action == Action.LEAVE
        boolean conflict = file.action == Action.CONFLICT || left
        boolean removal = file.action == Action.REMOVE
        byte[] from = conflict ? (file.base != null ? file.base : file.current) : removal && file.current == null ? file.base : file.current
        byte[] to = conflict ? file.proposed : file.result
        if (conflict) {
            lines << "${left ? 'left out' : 'rejected'}: ${file.path} (${file.reason})".toString()
        }
        if (file.binary) {
            lines << "Binary file ${file.path} ${removal ? 'is removed' : from == null ? 'is added' : 'changes'}".toString()
            return lines
        }
        List<String> before = from == null ? [] as List<String> : text(from)
        List<String> after = text(to)
        // the changes are worked out within the bounds of the merge, whatever the files are
        List<int[]> changes = before.size() > ThreeWayMerge.MAX_LINES || after.size() > ThreeWayMerge.MAX_LINES ? null : ThreeWayMerge.changes(before, after)
        if (changes == null) {
            lines << "${file.path}: the file differs in more lines than are shown".toString()
            return lines
        }
        Patch<String> patch = new Patch<>()
        for (int[] change : changes) {
            Chunk<String> removed = new Chunk<>(change[0], new ArrayList<String>(before.subList(change[0], change[1])))
            Chunk<String> added = new Chunk<>(change[2], new ArrayList<String>(after.subList(change[2], change[3])))
            patch.addDelta(removed.size() == 0 ? new InsertDelta<String>(removed, added) : added.size() == 0 ? new DeleteDelta<String>(removed, added) :
                    new ChangeDelta<String>(removed, added))
        }
        List<String> unified = UnifiedDiffUtils.generateUnifiedDiff(from == null ? '/dev/null' : 'a/' + file.path, removal ? '/dev/null' : 'b/' + file.path,
                before, patch, 3)
        if (unified.isEmpty()) {
            lines << "${file.path}: whether the file can be executed changes".toString()
        }
        lines.addAll(unified.take(MAX_DIFF_OF_FILE))
        if (unified.size() > MAX_DIFF_OF_FILE) {
            lines << "... ${unified.size() - MAX_DIFF_OF_FILE} more lines of ${file.path} are not shown".toString()
        }
        return lines
    }

    // the lines of a file, without what ends them
    private static List<String> text(byte[] bytes) {
        List<String> lines = new String(bytes, StandardCharsets.UTF_8).split('\n', -1).toList()
        lines = lines.last().isEmpty() ? lines.subList(0, lines.size() - 1) : lines
        return lines.collect { String line -> line.endsWith('\r') ? line.substring(0, line.length() - 1) : line }
    }

    // what is shown of a file is not what a terminal acts on, or reads backwards
    private static String safe(String line) {
        return UNSAFE.matcher(line).replaceAll('?')
    }

    /**
     * Writes the files and the new state of the project, all of them or none.
     *
     * @throws IllegalStateException for a plan with conflicts
     * @throws ProjectChangeException for a plan that is refused, with why
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
     * @throws ProjectChangeException for a plan that is refused, with why
     * @throws ProjectChangeException when the files could not be written, or one of them is not what
     * it was when the plan was made; the message says whether the project is as it was
     */
    void apply(Consumer<String> written) {
        if (isRefused()) {
            throw new ProjectChangeException(refusal)
        }
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
            } else if (file.isRemoved()) {
                writes << new Write(file.path, null, null, ProjectTransaction.sha256(file.current))
            }
        }
        writes.addAll(state)
        ProjectTransaction.run(project, home, writes, written)
    }
}
