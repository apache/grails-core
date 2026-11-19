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
package org.grails.forge.build.gradle

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull

/**
 * What the features of a project add to its build file besides dependencies, plugins and repositories.
 *
 * <p>An <em>extension</em> is the body of a named block. Every feature that contributes to the same name
 * adds to one block, in the order the features are applied, so two features can both configure
 * {@code assets { }} without either replacing the other. The name is the start of the block as it is
 * written, such as {@code grails} or {@code tasks.withType(Test).configureEach}. What two features
 * contribute is not compared: the same line contributed twice is in the block twice.</p>
 *
 * <p>A <em>task</em> contribution is an extension of the configuration of one task,
 * {@code tasks.named('<task>') { }}.</p>
 *
 * <p>A <em>block</em> is text added as it is at a {@link BuildFileAnchor}, for what is not the body of a
 * named block. Blocks at one anchor keep the order they were added in and are never merged.</p>
 *
 * <p>Every rendered block is followed by an empty line, and lines end with a line feed.</p>
 *
 * <p>The build file is rendered from these when the build feature is applied, so a feature has to
 * contribute before that: a contribution made afterwards is an error, not a line that silently goes
 * missing.</p>
 *
 * @since 9.0
 */
@CompileStatic
class BuildFileContributions {

    private static final String INDENT = '    '

    private final Map<String, List<String>> extensions = new LinkedHashMap<>()
    private final Map<BuildFileAnchor, List<String>> blocks = new EnumMap<>(BuildFileAnchor)
    private boolean closed

    /**
     * Adds to the body of a named block of the build file.
     *
     * @param name the start of the block as it is written in the build file
     * @param body the lines to add, not indented
     */
    void extension(@Nonnull String name, @Nonnull String body) {
        requireOpen(name)
        requireText(name, 'The name of a build file extension must not be blank')
        requireText(body, "The body of the build file extension '${name}' must not be blank")
        extensions.computeIfAbsent(name.trim()) { String key -> new ArrayList<String>() }.add(body)
    }

    /**
     * Adds to the configuration of a task of the build, {@code tasks.named('<task>') { }}.
     *
     * @param taskName the name of the task
     * @param body the lines to add, not indented
     */
    void task(@Nonnull String taskName, @Nonnull String body) {
        requireText(taskName, 'The name of a task of the build file must not be blank')
        if (taskName.contains("'") || taskName.contains('\\')) {
            throw new IllegalArgumentException("The task name '${taskName}' cannot be written in the build file")
        }
        extension("tasks.named('${taskName.trim()}')".toString(), body)
    }

    /**
     * Adds text to the build file as it is.
     *
     * @param anchor where to add it
     * @param text the lines to add
     */
    void block(@Nonnull BuildFileAnchor anchor, @Nonnull String text) {
        Objects.requireNonNull(anchor, 'The anchor of a build file block must be set')
        requireOpen(anchor.name())
        requireText(text, "The build file block at ${anchor} must not be blank")
        blocks.computeIfAbsent(anchor) { BuildFileAnchor key -> new ArrayList<String>() }.add(text)
    }

    /**
     * Marks the contributions as read by the build file: later contributions are errors.
     */
    void close() {
        closed = true
    }

    /**
     * @return the named blocks, each followed by an empty line, or an empty string when there are none
     */
    @Nonnull
    String renderExtensions() {
        StringBuilder rendered = new StringBuilder()
        extensions.each { String name, List<String> bodies ->
            rendered.append(name).append(' {\n')
            for (String body : bodies) {
                for (String line : lines(body)) {
                    rendered.append(line.isBlank() ? '' : INDENT + line).append('\n')
                }
            }
            rendered.append('}\n\n')
        }
        return rendered.toString()
    }

    /**
     * @return the blocks added at the anchor, each followed by an empty line, or an empty string when there are none
     */
    @Nonnull
    String renderBlocks(@Nonnull BuildFileAnchor anchor) {
        StringBuilder rendered = new StringBuilder()
        for (String text : blocks.getOrDefault(anchor, Collections.<String>emptyList())) {
            for (String line : lines(text)) {
                rendered.append(line).append('\n')
            }
            rendered.append('\n')
        }
        return rendered.toString()
    }

    private static List<String> lines(String text) {
        List<String> lines = text.stripTrailing().readLines()
        while (lines.first().isBlank()) {
            lines.removeAt(0)
        }
        return lines
    }

    private void requireOpen(String contribution) {
        if (closed) {
            throw new IllegalStateException(
                    "The build file is already rendered, so '${contribution}' cannot be added to it. " +
                    'A feature that contributes to the build file must be applied before the build feature (FeaturePhase.BUILD).')
        }
    }

    private static void requireText(String text, String message) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(message)
        }
    }
}
