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
package org.grails.gradle.plugin.core

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic

import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult

/**
 * What of the class path of Forge the repositories of a build did not give, as text, so that the task
 * that checks a feature pack can say it in words of its own and not run Forge on a part of its class
 * path: Gradle would fail before the task for the one and say nothing for the other.
 *
 * @since 9.0
 */
@CompileStatic
class ForgeToolingProblems implements Serializable {

    private static final long serialVersionUID = 1L

    private static final int MAX_REASON_LINES = 8
    private static final String UNREADABLE = /[\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}]+/
    // as Gradle names a file it could not get: the file, then the module in parentheses
    private static final Pattern FILE_OF_MODULE = Pattern.compile(/Could not \w+ (\S+) \(([^)]+)\)/)

    /** What the configuration declares and was not found, which is Forge: each with the first lines of why. */
    final List<String> notFound

    /** The modules that what was found needs and that were not found, by their coordinates. */
    final List<String> neededNotFound

    /** The files of modules that were found which could not be fetched: each as the file of its module. */
    final List<String> notFetched

    ForgeToolingProblems(List<String> notFound, List<String> neededNotFound, List<String> notFetched) {
        this.notFound = new ArrayList<String>(notFound)
        this.neededNotFound = new ArrayList<String>(neededNotFound)
        this.notFetched = new ArrayList<String>(notFetched)
    }

    boolean isEmpty() {
        return notFound.isEmpty() && neededNotFound.isEmpty() && notFetched.isEmpty()
    }

    /**
     * Resolves the configuration without failing for what it cannot get.
     *
     * @param configuration the configuration that holds Forge
     * @return what could not be resolved, and when every module was, what of them could not be fetched
     */
    static ForgeToolingProblems of(Configuration configuration) {
        Set<String> notFound = new LinkedHashSet<>()
        Set<String> neededNotFound = new LinkedHashSet<>()
        ResolvedComponentResult root = configuration.incoming.resolutionResult.root
        Set<ResolvedComponentResult> seen = new HashSet<>()
        Deque<ResolvedComponentResult> pending = new ArrayDeque<>()
        pending.add(root)
        while (!pending.isEmpty()) {
            ResolvedComponentResult component = pending.poll()
            if (!seen.add(component)) {
                continue
            }
            for (DependencyResult dependency : component.dependencies) {
                if (dependency instanceof ResolvedDependencyResult) {
                    pending.add(((ResolvedDependencyResult) dependency).selected)
                } else if (dependency instanceof UnresolvedDependencyResult) {
                    UnresolvedDependencyResult missing = (UnresolvedDependencyResult) dependency
                    if (component.is(root)) {
                        // with where it was looked for, which Gradle says on the lines after the first
                        List<String> why = lines(missing.failure).take(MAX_REASON_LINES)
                        notFound << "${missing.attempted.displayName}${why ? ': ' + why.join('\n      ') : ''}".toString()
                    } else {
                        neededNotFound << missing.attempted.displayName
                    }
                }
            }
        }
        Set<String> notFetched = new LinkedHashSet<>()
        if (notFound.isEmpty() && neededNotFound.isEmpty()) {
            Collection<Throwable> failures = configuration.incoming
                    .artifactView { ArtifactView.ViewConfiguration view -> view.lenient(true) }.artifacts.failures
            for (Throwable failure : failures) {
                notFetched.addAll(filesOfModules(failure))
            }
        }
        return new ForgeToolingProblems(notFound.toList(), neededNotFound.toList(), notFetched.toList())
    }

    /**
     * The files a failure names, each with its module, or the first line of the failure where it names
     * none in the way Gradle does. A failure can be one of several, which are then its causes.
     */
    private static List<String> filesOfModules(Throwable failure) {
        List<String> files = []
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>())
        Deque<Throwable> pending = new ArrayDeque<>()
        pending.add(failure)
        while (!pending.isEmpty()) {
            Throwable each = pending.poll()
            if (each == null || !seen.add(each)) {
                continue
            }
            Matcher named = FILE_OF_MODULE.matcher(lines(each).join(' '))
            if (named.find()) {
                files << "${named.group(1)} of ${named.group(2)}".toString()
            } else if (each.cause != null) {
                pending.add(each.cause)
            } else {
                files << (lines(each) ? lines(each).first() : each.class.name)
            }
        }
        return files
    }

    private static List<String> lines(Throwable failure) {
        String message
        try {
            message = failure?.message
        } catch (RuntimeException ignored) {
            message = null
        }
        return (message ?: '').readLines()
                .collect { String line -> line.replaceAll(UNREADABLE, ' ').trim() }
                .findAll { String line -> !line.isEmpty() }
    }
}
