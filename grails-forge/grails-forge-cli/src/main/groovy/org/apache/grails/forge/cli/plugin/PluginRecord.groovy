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
package org.apache.grails.forge.cli.plugin

import java.util.regex.Matcher
import java.util.regex.Pattern
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.pack.PackCatalog

/**
 * What is known of one Grails plugin, from the list this command line has in itself or from the plugin
 * index. Every text of a record is one line that a terminal and the command line take as text: what is
 * read from elsewhere is made so before it is kept, see {@link #line}.
 *
 * @since 9.0
 */
@CompileStatic
final class PluginRecord {

    /** The most characters of a name */
    public static final int MAX_NAME = 100

    /** The most characters of what a plugin is said to be for */
    public static final int MAX_DESCRIPTION = 300

    /** The most characters of an address */
    public static final int MAX_ADDRESS = 500

    private static final Pattern ADDRESS = Pattern.compile(/https?:\/\/[A-Za-z0-9\-._~:\/?#!&()*+,;=%]+/)
    private static final Pattern LEADS_TO = Pattern.compile(/(\d{1,9})(?:\.(\d{1,9}))?(?:\.(\d{1,9}))?(?:\.(\d{1,9}))?.*/)
    private static final Pattern BOUND = Pattern.compile(/(\d{1,9}|\*)(?:\.(\d{1,9}|\*))?(?:\.(\d{1,9}|\*))?(?:\.(\d{1,9}|\*))?.*/)
    private static final Pattern RANGE = Pattern.compile(/\s*(\S+)\s*>=?\s*(\S+)\s*/)
    private static final String ANY = '*'

    /** The group and the artifact, as {@code group:artifact} */
    String coordinates
    /** The name people know the plugin by */
    String name
    /** What the plugin is for, or {@code null} */
    String description
    /** The address of its documentation, or {@code null} */
    String documentation
    /** The address of its sources, or {@code null} */
    String source
    /** The address of its issue tracker, or {@code null} */
    String issues
    /** Its license, or {@code null} */
    String license
    /** Why the plugin is deprecated, which is empty when no reason is given; {@code null} for one that is not */
    String deprecated
    /** The repository the plugin index names for the plugin, or {@code null} */
    URI repository
    /** Whether the plugin is one of the release of Grails this command line belongs to */
    boolean ofGrails
    /** The features of the command line that add the plugin to an application */
    List<String> features = []
    /** The versions the plugin index has, in its order */
    List<Release> versions = []
    /**
     * The feature packs the plugin index records for the plugin, read as the create commands read
     * them when the index is their feature catalog, or {@code null} when it records none
     */
    PackCatalog.Plugin packs

    String getGroup() {
        return coordinates.substring(0, coordinates.indexOf(':'))
    }

    String getArtifact() {
        return coordinates.substring(coordinates.indexOf(':') + 1)
    }

    /**
     * @param grails a version of Grails
     * @return the latest release the index has that says it is for that Grails; else, of a plugin
     * without a release, the first other version that says so; {@code null} when there is none
     */
    Release latestFor(String grails) {
        List<Release> fitting = versions.findAll { Release each -> each.isFor(grails) }
        Release latest = null
        for (Release each : fitting) {
            if (each.release && (latest == null || compare(each.version, latest.version) > 0)) {
                latest = each
            }
        }
        return latest ?: fitting.find { Release each -> !each.snapshot }
    }

    /**
     * @param text a text that was read from elsewhere, or {@code null}
     * @param limit the most characters that are kept of it
     * @return the text as one line that is neither markup of the command line nor anything a terminal
     * acts on, or {@code null} when nothing is left of it
     */
    static String line(String text, int limit) {
        if (text == null) {
            return null
        }
        String cut = text.length() > limit * 4 ? text.substring(0, limit * 4) : text
        String safe = GradleRunner.line(cut.replaceAll(/[\s\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}]+/, ' ').trim())
        if (safe.isEmpty()) {
            return null
        }
        int count = safe.codePointCount(0, safe.length())
        return count > limit ? safe.substring(0, safe.offsetByCodePoints(0, limit)).trim() + '...' : safe
    }

    /**
     * @param text an address that was read from elsewhere, or {@code null}
     * @return the address when it is an http or https address written in the characters of one, and
     * {@code null} otherwise: an address is shown, and never asked
     */
    static String address(String text) {
        String trimmed = text?.trim()
        return trimmed && trimmed.length() <= MAX_ADDRESS && ADDRESS.matcher(trimmed).matches() && !trimmed.contains('@|') ? trimmed : null
    }

    /**
     * @return which of two versions is the later one by the numbers they begin with
     */
    static int compare(String one, String other) {
        long[] first = numbers(LEADS_TO.matcher(one), 0L)
        long[] second = numbers(LEADS_TO.matcher(other), 0L)
        for (int index = 0; index < 4; index++) {
            int difference = Long.compare(first[index], second[index])
            if (difference != 0) {
                return difference
            }
        }
        return 0
    }

    private static long[] numbers(Matcher matcher, long any) {
        long[] numbers = new long[4]
        if (matcher.matches()) {
            for (int part = 0; part < 4; part++) {
                String number = matcher.group(part + 1)
                numbers[part] = number == null ? 0L : number == ANY ? any : Long.parseLong(number)
            }
        }
        return numbers
    }

    /**
     * One version of a plugin, as the plugin index has it.
     */
    @CompileStatic
    static final class Release {

        private static final Pattern RELEASE = Pattern.compile(/\d{1,9}(\.\d{1,9}){0,3}/)

        final String version
        /** The range of Grails the descriptor of the plugin gives, such as {@code 7.0.0 > *}, or {@code null} */
        final String grailsVersion

        Release(String version, String grailsVersion) {
            this.version = version
            this.grailsVersion = grailsVersion
        }

        boolean isRelease() {
            return RELEASE.matcher(version).matches()
        }

        boolean isSnapshot() {
            return version.endsWith('-SNAPSHOT')
        }

        /**
         * @param grails a version of Grails, which can be one that leads to a release, such as a snapshot
         * @return whether the range of the descriptor has it. A version without a range, or with one
         * that is not read, is not known to be for any Grails
         */
        boolean isFor(String grails) {
            Matcher range = grailsVersion == null ? null : RANGE.matcher(grailsVersion)
            if (range == null || !range.matches() || !LEADS_TO.matcher(grails ?: '').matches()) {
                return false
            }
            Matcher lower = BOUND.matcher(range.group(1))
            Matcher upper = BOUND.matcher(range.group(2))
            if (!lower.matches() || !(range.group(2) == ANY || upper.matches())) {
                return false
            }
            long[] asked = numbers(LEADS_TO.matcher(grails), 0L)
            long[] lowest = numbers(lower, 0L)
            long[] highest = range.group(2) == ANY ? null : numbers(upper, Long.MAX_VALUE)
            return order(asked, lowest) >= 0 && (highest == null || order(asked, highest) <= 0)
        }

        private static int order(long[] one, long[] other) {
            for (int index = 0; index < 4; index++) {
                int difference = Long.compare(one[index], other[index])
                if (difference != 0) {
                    return difference
                }
            }
            return 0
        }
    }
}
