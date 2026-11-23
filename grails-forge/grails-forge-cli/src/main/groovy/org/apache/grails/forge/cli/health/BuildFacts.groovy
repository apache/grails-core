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
package org.apache.grails.forge.cli.health

import java.util.regex.Matcher
import java.util.regex.Pattern
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ProjectEvidence

/**
 * What the build file of a project says that {@code project health} needs besides what
 * {@link ProjectEvidence} reads: the versions of Java it compiles for, and the SkillsJars Gradle
 * plugin with where it writes the skills. The file is read as text, line by line, without its
 * comments, as {@link ProjectEvidence} reads it; nothing of it is run, so what it declares through a
 * variable, in a loop or in another file is not seen.
 *
 * <p>The versions of Java are each of the forms a build names one in: the release of the compiler
 * ({@code compileJava.options.release = 21}, which Forge writes, {@code options.release = 21} in a
 * block, {@code options.release.set(21)}), the toolchain ({@code JavaLanguageVersion.of(21)}), and
 * {@code sourceCompatibility} and {@code targetCompatibility}, with a number or a
 * {@code JavaVersion}.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class BuildFacts {

    /** The release of the compiler, which decides the version of the classes it writes. */
    static final String RELEASE = 'release'

    /** The toolchain, which decides the JDK the build compiles and runs the tests with. */
    static final String TOOLCHAIN = 'toolchain'

    /** {@code targetCompatibility}. */
    static final String TARGET = 'targetCompatibility'

    /** {@code sourceCompatibility}. */
    static final String SOURCE = 'sourceCompatibility'

    /** The id of the SkillsJars Gradle plugin, which extracts the agent skills of Grails into the project. */
    static final String SKILLSJARS_PLUGIN = 'com.skillsjars.gradle-plugin'

    // the order in which the kinds say what the build compiles for, the first that is there deciding
    private static final List<String> ORDER = [RELEASE, TOOLCHAIN, TARGET, SOURCE].asImmutable()
    private static final Pattern RELEASE_SET = Pattern.compile(/(?<![A-Za-z0-9_])release\s*(?:=|\.set\s*\()\s*(?:JavaVersion\.VERSION_)?(\d{1,3})(?![\d.])/)
    private static final Pattern LANGUAGE_VERSION = Pattern.compile(/JavaLanguageVersion\s*\.\s*of\s*\(\s*["']?(\d{1,3})["']?\s*\)/)
    private static final Pattern COMPATIBILITY = Pattern.compile(
            /(?<![A-Za-z0-9_])(sourceCompatibility|targetCompatibility)\s*(?:=|\.set\s*\()?\s*(?:JavaVersion\.VERSION_(?:1_)?(\d{1,3})(?![\d_])|["']?(?:1\.)?(\d{1,3})["']?(?![\d.]))/)
    private static final Pattern SKILLSJARS = Pattern.compile(/["']com\.skillsjars\.gradle-plugin["']/)
    private static final Pattern OUTPUT_DIRECTORY = Pattern.compile(
            /(?<![A-Za-z0-9_])outputDir\s*(?:=|\.set\s*\()\s*(?:layout\.projectDirectory\.dir|project\.layout\.projectDirectory\.dir|file|project\.file)?\s*\(?\s*["']([^"'\x24]{1,200})["']/)

    /** The build file that was read, as {@link ProjectEvidence#buildFile} names it. */
    final String file

    /** Whether there was one to read. */
    final boolean read

    /** Each version of Java the build file names, in the order of the file. */
    final List<JavaTarget> javaTargets

    /** Whether the build file applies the SkillsJars Gradle plugin. */
    final boolean skillsJarsPlugin

    /** The directory the SkillsJars Gradle plugin writes the skills to, as the build file names it; {@code null} where it does not. */
    final String skillsOutputDirectory

    private BuildFacts(String file, List<String> lines) {
        this.file = file
        this.read = lines != null
        List<JavaTarget> targets = []
        boolean skills = false
        String output = null
        for (String line : ProjectEvidence.withoutComments(lines ?: Collections.<String>emptyList())) {
            Matcher release = RELEASE_SET.matcher(line)
            if (release.find()) {
                targets << new JavaTarget(RELEASE, Integer.parseInt(release.group(1)), line.contains('compileJava') ? 'compileJava.options.release' :
                        line.contains('options.release') ? 'options.release' : RELEASE)
            }
            Matcher language = LANGUAGE_VERSION.matcher(line)
            if (language.find()) {
                targets << new JavaTarget(TOOLCHAIN, Integer.parseInt(language.group(1)), 'java.toolchain.languageVersion')
            }
            Matcher compatibility = COMPATIBILITY.matcher(line)
            while (compatibility.find()) {
                targets << new JavaTarget(compatibility.group(1), Integer.parseInt(compatibility.group(2) ?: compatibility.group(3)), compatibility.group(1))
            }
            skills = skills || SKILLSJARS.matcher(line).find()
            Matcher directory = OUTPUT_DIRECTORY.matcher(line)
            if (output == null && directory.find()) {
                output = directory.group(1)
            }
        }
        this.javaTargets = Collections.unmodifiableList(targets)
        this.skillsJarsPlugin = skills
        this.skillsOutputDirectory = output
    }

    /**
     * @param file the build file, as {@link ProjectEvidence#buildFile} names it
     * @param lines its lines, or {@code null} when it could not be read
     */
    static BuildFacts of(String file, List<String> lines) {
        return new BuildFacts(file, lines)
    }

    /**
     * @return the version of Java the build compiles for: that of the release where the file names
     * one, then that of the toolchain, then {@code targetCompatibility}, then {@code sourceCompatibility},
     * each the last the file sets, as the last assignment is the one Gradle has; {@code null} where it names none
     */
    JavaTarget getCompiledFor() {
        for (String kind : ORDER) {
            JavaTarget target = javaTargets.reverse().find { JavaTarget each -> each.kind == kind }
            if (target != null) {
                return target
            }
        }
        return null
    }

    /** A version of Java the build file names, and how. */
    @CompileStatic
    static final class JavaTarget {

        /** {@link #RELEASE}, {@link #TOOLCHAIN}, {@link #TARGET} or {@link #SOURCE}. */
        final String kind

        /** The major version of Java. */
        final int version

        /** What the build file sets, as it is said: {@code compileJava.options.release}. */
        final String setting

        JavaTarget(String kind, int version, String setting) {
            this.kind = kind
            this.version = version
            this.setting = setting
        }

        @Override
        String toString() {
            return "${version} (${setting})"
        }
    }
}
