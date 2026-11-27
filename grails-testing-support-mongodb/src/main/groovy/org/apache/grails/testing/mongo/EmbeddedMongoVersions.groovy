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
package org.apache.grails.testing.mongo

import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import de.flapdoodle.embed.mongo.distribution.Version

/**
 * Names the MongoDB release line the embedded server runs for the version a build asks for.
 *
 * <p>A build asks for a MongoDB with the {@code mongodbContainerVersion} system property, as a
 * container image tag such as {@code 7.0.19} or {@code 9.0}. The embedded server names releases
 * differently, by the lines the flapdoodle library knows: {@code V7_0}, {@code V8_0}. The line a
 * tag names is used when flapdoodle has it, and the newest line below it otherwise, so a build
 * against a MongoDB that flapdoodle does not run yet still serves its replica-set specifications
 * from the closest release flapdoodle does run, rather than failing before a server starts.
 *
 * @since 8.0
 */
@CompileStatic
class EmbeddedMongoVersions {

    /**
     * Starts the line written to standard error when an older line stands in. The MongoDB test
     * tasks of the Grails build match it to copy that line onto the build log.
     */
    @PackageScope
    static final String NOTICE = 'Embedded MongoDB: '

    /** A line of releases, as flapdoodle names one: a major and a minor, nothing else. */
    private static final Pattern LINE = ~/^V(\d+)_(\d+)$/

    /** A container image tag is the major, or the major and the minor, followed by a patch or a suffix or nothing. */
    private static final Pattern TAG = ~/^(\d+)(?:\.(\d+)(?:[.-].*)?)?$/

    private EmbeddedMongoVersions() {
    }

    /**
     * The name of the flapdoodle release line the embedded server should run for a MongoDB version.
     *
     * <p>When an older line stands in for the one the version names, a line on standard error says
     * so. It is written there rather than logged so that it reaches the test output whatever SLF4J
     * binding the tests run with, {@code slf4j-nop} or none at all included.
     *
     * @param mongoVersion a MongoDB version, as a container image tag such as {@code 8.0} or {@code 7.0.19}
     * @return the name of the line, such as {@code V8_0}: the line the version names when flapdoodle
     * has it, otherwise the newest line flapdoodle has below it
     * @throws IllegalArgumentException when the version does not start with a major and a minor number
     * @throws IllegalStateException when flapdoodle has no line at or below the version
     */
    static String lineFor(String mongoVersion) {
        def tag = TAG.matcher(mongoVersion ?: '')
        if (!tag.matches()) {
            throw new IllegalArgumentException("'$mongoVersion' is not a MongoDB version such as 8.0 or 7.0.19")
        }
        int wanted = codeOf(tag.group(1), tag.group(2) ?: '0')
        String newest = null
        int newestCode = -1
        for (def line : lines()) {
            int code = codeOf(line)
            if (code <= wanted && code > newestCode) {
                newest = line
                newestCode = code
            }
        }
        if (newest == null) {
            throw new IllegalStateException("The embedded server has no MongoDB release line at or below $mongoVersion; " +
                    "the flapdoodle library on the class path has ${lines()}")
        }
        if (newestCode != wanted) {
            System.err.println("${NOTICE}the flapdoodle library on the class path has no MongoDB $mongoVersion line yet, " +
                    "so the embedded server runs $newest")
        }
        newest
    }

    /** The lines flapdoodle has, in the order it declares them. */
    private static List<String> lines() {
        Version.Main.values()*.name().findAll { it ==~ LINE }
    }

    /** One number per line, ordered as releases are: the major first, the minor within it. */
    private static int codeOf(String major, String minor) {
        major.toInteger() * 1000 + minor.toInteger()
    }

    private static int codeOf(String line) {
        (line - 'V').tokenize('_').with { codeOf(it[0], it[1]) }
    }

}
