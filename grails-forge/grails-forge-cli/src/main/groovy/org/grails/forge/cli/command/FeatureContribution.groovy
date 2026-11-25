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
package org.grails.forge.cli.command

import com.github.difflib.DiffUtils
import com.github.difflib.patch.AbstractDelta
import groovy.transform.CompileStatic
import org.apache.grails.forge.core.change.ChangePlan
import org.apache.grails.forge.core.change.ChangePlan.Action
import org.apache.grails.forge.core.change.ChangePlan.PlannedFile
import org.apache.grails.forge.core.state.ProjectState

/**
 * What a feature contributes to an application, as {@code feature info} says it: the files it adds,
 * changes and removes, and the lines it puts into the build files and takes out of them. It is
 * worked out by the generator: from what {@code feature add} would do to the application of the
 * current directory, or from an application of a type generated with the feature and without it.
 *
 * @since 9.0
 */
@CompileStatic
final class FeatureContribution {

    public static final String ADDED = 'added'
    public static final String CHANGED = 'changed'
    public static final String REMOVED = 'removed'

    private static final List<String> BUILD_FILES = ['build.gradle', 'settings.gradle', 'gradle.properties']
    private static final String OLD = '--- '
    private static final String NEW = '+++ '
    private static final String HUNK = '@@'
    private static final String NOWHERE = '/dev/null'
    private static final String PLUS = '+'
    private static final String MINUS = '-'

    /** The files, each by its {@code path} in the project and with its {@code change}: added, changed or removed. */
    final List<Map<String, String>> files

    /** The build files that change, each with its {@code file} and the lines that are {@code added} to it and {@code removed} from it. */
    final List<Map<String, Object>> build

    private FeatureContribution(List<Map<String, String>> files, List<Map<String, Object>> build) {
        this.files = files.asImmutable()
        this.build = build.asImmutable()
    }

    /**
     * @param plan what adding the feature to an application comes to
     * @return what the feature contributes to that application
     */
    static FeatureContribution ofPlan(ChangePlan plan) {
        List<Map<String, String>> files = []
        for (PlannedFile file : plan.files) {
            if (file.action == Action.ADOPT) {
                continue
            }
            files << entry(file.path, file.action == Action.ADD ? ADDED : file.action == Action.REMOVE ? REMOVED : CHANGED)
        }
        return new FeatureContribution(files, buildOf(plan.diff()))
    }

    /**
     * @param without the files of an application without the feature, by their paths
     * @param with the files of the same application with it
     * @return what the feature contributes to that application
     */
    static FeatureContribution ofGenerated(Map<String, String> without, Map<String, String> with) {
        List<Map<String, String>> files = []
        List<Map<String, Object>> build = []
        Set<String> paths = new TreeSet<String>(without.keySet())
        paths.addAll(with.keySet())
        for (String path : paths) {
            if (ProjectState.isStatePath(path)) {
                continue
            }
            String before = without.get(path)
            String after = with.get(path)
            if (before == after) {
                continue
            }
            files << entry(path, before == null ? ADDED : after == null ? REMOVED : CHANGED)
            if (isBuildFile(path)) {
                List<String> added = []
                List<String> removed = []
                for (AbstractDelta<String> delta : DiffUtils.diff(lines(before), lines(after)).getDeltas()) {
                    removed.addAll(delta.getSource().getLines())
                    added.addAll(delta.getTarget().getLines())
                }
                build << changes(path, added, removed)
            }
        }
        return new FeatureContribution(files, build)
    }

    // The changes of the build files in the unified diff of a plan: each file starts with the two lines that name it, and its hunks follow
    private static List<Map<String, Object>> buildOf(List<String> diff) {
        List<Map<String, Object>> build = []
        String path = null
        List<String> added = []
        List<String> removed = []
        for (int i = 0; i < diff.size(); i++) {
            String line = diff.get(i)
            if (line.startsWith(OLD) && i + 2 < diff.size() && diff.get(i + 1).startsWith(NEW) && diff.get(i + 2).startsWith(HUNK)) {
                if (path != null && isBuildFile(path)) {
                    build << changes(path, added, removed)
                }
                String to = diff.get(i + 1).substring(NEW.length())
                String named = to == NOWHERE ? line.substring(OLD.length()) : to
                path = named.startsWith('a/') || named.startsWith('b/') ? named.substring(2) : named
                added = []
                removed = []
                i++
            } else if (path != null && line.startsWith(PLUS)) {
                added << line.substring(1)
            } else if (path != null && line.startsWith(MINUS)) {
                removed << line.substring(1)
            } else if (path != null && !line.startsWith(' ') && !line.startsWith(HUNK) && !line.isEmpty()) {
                // a line of the plan that is of no file: what it says of the next one
                if (isBuildFile(path)) {
                    build << changes(path, added, removed)
                }
                path = null
            }
        }
        if (path != null && isBuildFile(path)) {
            build << changes(path, added, removed)
        }
        return build
    }

    private static boolean isBuildFile(String path) {
        return path.substring(path.lastIndexOf('/') + 1) in BUILD_FILES
    }

    private static List<String> lines(String content) {
        return content == null || content.isEmpty() ? Collections.<String>emptyList() : content.split('\n').toList()
    }

    private static Map<String, String> entry(String path, String change) {
        Map<String, String> entry = new LinkedHashMap<>()
        entry.put('path', path)
        entry.put('change', change)
        return entry
    }

    private static Map<String, Object> changes(String file, List<String> added, List<String> removed) {
        Map<String, Object> changes = new LinkedHashMap<>()
        changes.put('file', file)
        changes.put('added', added.findAll { String line -> !line.trim().isEmpty() })
        changes.put('removed', removed.findAll { String line -> !line.trim().isEmpty() })
        return changes
    }
}
