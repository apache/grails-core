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
package org.apache.grails.forge.cli.console

import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Matcher
import java.util.regex.Pattern
import groovy.transform.CompileStatic
import org.apache.grails.forge.core.state.EarlierRecord
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateException
import org.grails.forge.options.ServletImpl

/**
 * What the files of a Grails project say of what the application is, for a project that has no
 * record of how it was generated: the record an earlier version wrote, the profile the Grails shell
 * went by, the plugins and the dependencies of the build, the group, and the package of the
 * {@code Application} class.
 *
 * <p>The files are read as text, within bounds, and nothing of them is run: a build file is Groovy
 * or Kotlin, and what it declares in a loop, through a variable, in a convention plugin or in another
 * file is not seen. So what is found here is evidence and no more, and what is not found is not known
 * not to be there. Each thing that was found is kept with where it was found, so that it can be said.
 * A comment is not read as what it comments out, and what the build file has that is known not to
 * have been read (another file it applies, a version catalog, what it declares for other projects)
 * is kept as well, see {@link #notRead}, so that it can be said too.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ProjectEvidence {

    /** The configuration of the application, in the project */
    public static final String CONFIGURATION = 'grails-app/conf/application.yml'

    /** The build file of the application, in the project */
    public static final String BUILD = 'build.gradle'

    /** The build file of an application whose build is written in Kotlin, which is read in a project that has no {@link #BUILD} */
    public static final String KOTLIN_BUILD = 'build.gradle.kts'

    /** The settings of a build, in the project that is the root of one */
    public static final String SETTINGS = 'settings.gradle'

    /** The settings of a build that is written in Kotlin, which are read in a project that has no {@link #SETTINGS} */
    public static final String KOTLIN_SETTINGS = 'settings.gradle.kts'

    private static final int MAX_FILE = 1024 * 1024
    private static final int MAX_LINE = 2000
    private static final int MAX_VALUE = 255
    private static final int MAX_INIT_FILES = 2000
    private static final List<String> EARLIER_RECORDS = [ProjectState.EARLIER_RECORD, 'grails-cli.yml']
    private static final List<String> PROFILE_GROUPS = ['org.apache.grails.profiles', 'org.grails.profiles']
    // the dependencies that give an application its servlet container: the starters of Spring Boot, among them the one
    // for Undertow of the Spring Boot that earlier versions of Grails are on, and the module of Grails for Undertow
    private static final Map<String, ServletImpl> SERVLET_STARTERS = Collections.unmodifiableMap([
            'org.springframework.boot:spring-boot-starter-tomcat': ServletImpl.TOMCAT,
            'org.springframework.boot:spring-boot-starter-jetty': ServletImpl.JETTY,
            'org.springframework.boot:spring-boot-starter-undertow': ServletImpl.UNDERTOW,
            'org.apache.grails:grails-undertow': ServletImpl.UNDERTOW] as Map<String, ServletImpl>)
    private static final Pattern YAML_KEY = Pattern.compile(/^( *)([A-Za-z0-9_.-]+):(?:[ \t]+(.*))?$/)
    private static final Pattern DEPENDENCY = Pattern.compile(/^\s*([A-Za-z][A-Za-z0-9]*)[ (]+(?:(?:platform|enforcedPlatform|testFixtures)\()?["']([^:"'\s]+):([^:"'\s]+)(?::[^"']*)?["']/)
    // the same with the group and the name each said by its own: implementation group: 'g', name: 'n', and (group = "g", name = "n") in Kotlin
    private static final Pattern MAP_DEPENDENCY = Pattern.compile(
            /^\s*([A-Za-z][A-Za-z0-9]*)[ (]+(?:platform\()?\s*group\s*[:=]\s*["']([^:"'\s]+)["']\s*,\s*name\s*[:=]\s*["']([^:"'\s]+)["']/)
    // a dependency that is named in a version catalog, which is another file
    private static final Pattern CATALOG_DEPENDENCY = Pattern.compile(/^\s*[A-Za-z][A-Za-z0-9]*[ (]+(?:platform\()?libs\.[A-Za-z]/)
    // a block that says something of other projects than the one the build file is of
    private static final Pattern FOR_OTHERS = Pattern.compile(/^\s*(?:subprojects|configure\s*\(.*\)|project\s*\(.*\))\s*\{/)
    private static final Pattern APPLIED_FILE = Pattern.compile(/^\s*apply\s*\(?\s*from\s*[:=]/)
    private static final Pattern GRADLE_PLUGIN = Pattern.compile(/["'](?:org\.apache\.grails\.gradle|org\.grails)\.(grails-[a-z-]+)["']/)
    private static final Pattern GROUP = Pattern.compile(/^\s*group\s*=?\s*["']([^"']+)["']\s*$/)
    private static final Pattern ROOT_NAME = Pattern.compile(/^\s*rootProject\.name\s*=\s*["']([^"']+)["']\s*$/)
    private static final Pattern NOT_PLAIN = Pattern.compile('[\\[{&*!|>\\x40`$%].*')
    private static final Pattern PACKAGE = Pattern.compile(/^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)\s*;?\s*$/)
    private static final List<Pattern> JAVA = [Pattern.compile(/JavaLanguageVersion\.of\(\s*(\d{1,3})\s*\)/),
                                               Pattern.compile(/JavaVersion\.VERSION_(\d{1,3})\b(?!_)/),
                                               Pattern.compile(/^\s*(?:sourceCompatibility|targetCompatibility)\s*=?\s*["']?(\d{1,3})["']?\s*$/)]

    /** The file of the record an earlier version wrote, when the project has one */
    final String earlierRecord

    /** Why the record an earlier version wrote is not read, in one line, when it cannot be */
    final String earlierRecordRefused

    /** The type the earlier record names, as it is written there */
    final String recordedType

    /** The profile the earlier record names */
    final String recordedProfile

    /** The package the earlier record names */
    final String recordedPackage

    /** How the earlier record says the application reloads */
    final String recordedReloading

    /** The features the earlier record names; {@code null} when it names none */
    final List<String> recordedFeatures

    /** {@code grails.profile} of the configuration */
    final String configuredProfile

    /** {@code grails.codegen.defaultPackage} of the configuration */
    final String configuredPackage

    /** The profile the build depends on, as {@code group:name}, of whatever group */
    final String profileDependency

    /** The Grails Gradle plugins the build names, without the group of their ids: {@code grails-web}, {@code grails-gsp}, {@code grails-plugin} */
    final Set<String> gradlePlugins

    /** Every dependency the build declares in a line of its own, as {@code group:artifact} */
    final Set<String> dependencies

    /**
     * The servlet containers the build declares the starter of, each with the first of {@link #dependencies}
     * that declares it, in the order of the build: {@code spring-boot-starter-tomcat}, {@code -jetty} and
     * {@code -undertow} of Spring Boot, and {@code grails-undertow}
     */
    final Map<ServletImpl, String> servletStarters

    /** The group of the build */
    final String group

    /** The version of Java the build names */
    final Integer javaVersion

    /** The name of the root project, of a project that has the settings of a build */
    final String rootProjectName

    /** Whether the project has the settings of a build, and so is one of its own */
    final boolean ownBuild

    /** The package of the {@code Application} class under {@code grails-app/init} */
    final String applicationClassPackage

    /** Whether the project has a build file that was read */
    final boolean buildRead

    /** The build file that was read: {@link #BUILD}, or {@link #KOTLIN_BUILD} in a project that has only that; {@link #BUILD} when none was read */
    final String buildFile

    /** The settings that were read, as {@link #buildFile} is the build file; {@link #SETTINGS} when none were read */
    final String settingsFile

    /**
     * What the build file has that says something of the dependencies of the application and was not
     * read, each as a sentence without its full stop: another build file it applies, a dependency of
     * a version catalog, and what it declares for other projects, which is not counted for this one.
     * Empty when the build file has nothing of the kind, which is not to say that everything was found.
     */
    final List<String> notRead

    private ProjectEvidence(Path directory) {
        String recordFile = EARLIER_RECORDS.find { String name -> Files.exists(directory.resolve(name), LinkOption.NOFOLLOW_LINKS) }
        Map<String, Object> record = [:]
        String refused = null
        if (recordFile != null) {
            try {
                record = EarlierRecord.read(directory.resolve(recordFile))
            } catch (ProjectStateException e) {
                refused = e.getMessage()
            }
        }
        earlierRecord = recordFile
        earlierRecordRefused = refused
        recordedType = value(record.get('applicationType'))
        recordedProfile = value(record.get('profile'))
        recordedPackage = value(record.get('defaultPackage'))
        recordedReloading = value(record.get('reloading'))
        Object features = record.get('features')
        recordedFeatures = features instanceof List ? ((List) features).findAll { Object each ->
            each instanceof String && EarlierRecord.FEATURE_NAME.matcher((String) each).matches()
        }.collect { Object each -> (String) each } : null

        Map<String, String> configuration = yamlValues(lines(directory.resolve(CONFIGURATION)), ['grails.profile', 'grails.codegen.defaultPackage'])
        configuredProfile = configuration.get('grails.profile')
        configuredPackage = configuration.get('grails.codegen.defaultPackage')

        List<String> build = lines(directory.resolve(BUILD))
        String read = BUILD
        if (build == null && !Files.exists(directory.resolve(BUILD), LinkOption.NOFOLLOW_LINKS)) {
            build = lines(directory.resolve(KOTLIN_BUILD))
            read = build == null ? BUILD : KOTLIN_BUILD
        }
        buildRead = build != null
        buildFile = read
        String profile = null
        String ofGroup = null
        Integer java = null
        Set<String> plugins = new LinkedHashSet<>()
        Set<String> declared = new LinkedHashSet<>()
        int applied = 0
        int ofCatalog = 0
        boolean ofOthers = false
        // how deep in braces the line is, and how deep the block for other projects began that the line is in
        int depth = 0
        int others = -1
        for (String line : withoutComments(build ?: [] as List<String>)) {
            if (others < 0 && FOR_OTHERS.matcher(line).find()) {
                others = depth
            }
            // what is said for other projects is not said of this one
            if (others >= 0) {
                ofOthers = ofOthers || DEPENDENCY.matcher(line).find() || MAP_DEPENDENCY.matcher(line).find() || CATALOG_DEPENDENCY.matcher(line).find()
            } else {
                Matcher dependency = DEPENDENCY.matcher(line)
                boolean found = dependency.find()
                if (!found) {
                    dependency = MAP_DEPENDENCY.matcher(line)
                    found = dependency.find()
                }
                if (found) {
                    String coordinates = "${dependency.group(2)}:${dependency.group(3)}".toString()
                    if (dependency.group(1) == 'profile') {
                        profile = profile ?: coordinates
                    } else {
                        declared.add(coordinates)
                    }
                } else if (CATALOG_DEPENDENCY.matcher(line).find()) {
                    ofCatalog++
                }
                if (APPLIED_FILE.matcher(line).find()) {
                    applied++
                }
                Matcher plugin = GRADLE_PLUGIN.matcher(line)
                while (plugin.find()) {
                    plugins.add(plugin.group(1))
                }
                Matcher grouped = GROUP.matcher(line)
                if (ofGroup == null && grouped.matches()) {
                    ofGroup = value(grouped.group(1))
                }
                for (Pattern pattern : JAVA) {
                    Matcher version = pattern.matcher(line)
                    if (java == null && version.find()) {
                        java = Integer.valueOf(version.group(1))
                    }
                }
            }
            depth += line.count('{') - line.count('}')
            if (others >= 0 && depth <= others) {
                others = -1
            }
        }
        profileDependency = profile
        gradlePlugins = Collections.unmodifiableSet(plugins)
        dependencies = Collections.unmodifiableSet(declared)
        Map<ServletImpl, String> starters = new LinkedHashMap<>()
        for (String each : declared) {
            ServletImpl servlet = SERVLET_STARTERS.get(each)
            if (servlet != null && !starters.containsKey(servlet)) {
                starters.put(servlet, each)
            }
        }
        servletStarters = Collections.unmodifiableMap(starters)
        group = ofGroup
        javaVersion = java
        List<String> unread = []
        if (applied > 0) {
            unread << "${read} applies ${applied == 1 ? 'another build file' : "${applied} other build files"} (apply from), which ${applied == 1 ? 'was' : 'were'} not read".toString()
        }
        if (ofCatalog > 0) {
            unread << ("${read} names ${ofCatalog == 1 ? 'a dependency' : "${ofCatalog} dependencies"} by a version catalog (libs.), which was not read: " +
                    "what ${ofCatalog == 1 ? 'it is' : 'they are'} is not known here").toString()
        }
        if (ofOthers) {
            unread << ("${read} declares dependencies for other projects (in a subprojects, configure or project block), which are not those of this " +
                    'application and were not counted').toString()
        }
        notRead = Collections.unmodifiableList(unread)

        List<String> settings = lines(directory.resolve(SETTINGS))
        String settingsRead = SETTINGS
        if (settings == null && !Files.exists(directory.resolve(SETTINGS), LinkOption.NOFOLLOW_LINKS)) {
            settings = lines(directory.resolve(KOTLIN_SETTINGS))
            settingsRead = settings == null ? SETTINGS : KOTLIN_SETTINGS
        }
        settingsFile = settingsRead
        ownBuild = settings != null || Files.isRegularFile(directory.resolve(SETTINGS)) || Files.isRegularFile(directory.resolve(KOTLIN_SETTINGS))
        rootProjectName = settings == null ? null : withoutComments(settings).findResult { String line ->
            Matcher named = ROOT_NAME.matcher(line)
            named.matches() ? value(named.group(1)) : null
        }
        applicationClassPackage = applicationPackage(directory)
    }

    /**
     * @param directory the directory of a Grails project
     * @return what its files say of the application
     */
    static ProjectEvidence of(Path directory) {
        return new ProjectEvidence(directory)
    }

    /**
     * @return the name of the profile the build depends on when it is a profile of Grails, of
     * whichever group Grails published its profiles under; {@code null} for none and for a profile of
     * another group, which is one this version knows nothing of
     */
    String getProfileOfGrails() {
        if (profileDependency == null) {
            return null
        }
        int colon = profileDependency.indexOf(':')
        return profileDependency.substring(0, colon) in PROFILE_GROUPS ? profileDependency.substring(colon + 1) : null
    }

    // one line of text that can be kept and shown, or nothing
    private static String value(Object written) {
        if (!(written instanceof String)) {
            return null
        }
        String text = ((String) written).trim()
        return EarlierRecord.isLine(text, MAX_VALUE) ? text : null
    }

    // the lines of a file of the project that is text of a size a file of a build has; null when there is no such file
    private static List<String> lines(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE) {
                return null
            }
            byte[] read
            try (InputStream input = Files.newInputStream(file)) {
                read = input.readNBytes(MAX_FILE + 1)
            }
            if (read.length > MAX_FILE) {
                return null
            }
            return new String(read, StandardCharsets.UTF_8).readLines().findAll { String line -> line.length() <= MAX_LINE }
        } catch (IOException ignored) {
            return null
        }
    }

    /**
     * The lines of a build file without their comments: a comment that begins with two slashes ends
     * with its line, and one between a slash with a star and a star with a slash ends where it ends,
     * on whichever line. What is between quotes is no comment, so the address of a repository is kept
     * whole. A text of several lines is read line by line, as if each began outside quotes.
     */
    static List<String> withoutComments(List<String> lines) {
        List<String> code = []
        boolean comment = false
        for (String line : lines) {
            StringBuilder kept = new StringBuilder()
            char quote = (char) 0
            int i = 0
            while (i < line.length()) {
                char c = line.charAt(i)
                char next = i + 1 < line.length() ? line.charAt(i + 1) : (char) 0
                if (comment) {
                    comment = !(c == (char) '*' && next == (char) '/')
                    i += comment ? 1 : 2
                } else if (quote != (char) 0) {
                    kept.append(c)
                    if (c == (char) '\\' && next != (char) 0) {
                        kept.append(next)
                        i++
                    } else if (c == quote) {
                        quote = (char) 0
                    }
                    i++
                } else if (c == (char) '/' && next == (char) '/') {
                    break
                } else if (c == (char) '/' && next == (char) '*') {
                    comment = true
                    i += 2
                } else {
                    if (c == (char) '"' || c == (char) "'") {
                        quote = c
                    }
                    kept.append(c)
                    i++
                }
            }
            code << kept.toString()
        }
        return code
    }

    /**
     * The first value each of the keys has in a YAML file, read by its lines and their indentation: a
     * key is found where it is written in steps ({@code grails:} above {@code profile:}), in one
     * ({@code grails.profile:}) or in both. Only a value that is on the line of its key is one.
     */
    private static Map<String, String> yamlValues(List<String> lines, List<String> keys) {
        Map<String, String> values = [:]
        // the keys above the line that is read, each with the indentation it is written at
        List<Map.Entry<Integer, String>> above = []
        for (String line : (lines ?: [] as List<String>)) {
            if (line.startsWith('---')) {
                above.clear()
                continue
            }
            Matcher key = YAML_KEY.matcher(line)
            if (!key.matches()) {
                continue
            }
            int indentation = key.group(1).length()
            while (!above.isEmpty() && above.last().key >= indentation) {
                above.removeLast()
            }
            above.add(new AbstractMap.SimpleEntry<Integer, String>(indentation, key.group(2)))
            String path = above*.value.join('.')
            String written = scalar(key.group(3))
            if (path in keys && written != null && !values.containsKey(path)) {
                values.put(path, written)
            }
        }
        return values
    }

    // a plain or a quoted value without the comment after it; nothing for a value YAML reads as more than text
    private static String scalar(String written) {
        if (written == null) {
            return null
        }
        String text = written.trim()
        Matcher quoted = text =~ /^(["'])([^"']*)\1\s*(?:#.*)?$/
        if (quoted.matches()) {
            return value(quoted.group(2))
        }
        int comment = text.indexOf(' #')
        text = (comment < 0 ? text : text.substring(0, comment)).trim()
        return text.isEmpty() || text.startsWith('#') || NOT_PLAIN.matcher(text).matches() ? null : value(text)
    }

    // the package of the Application class of the application, when there is one such class
    private static String applicationPackage(Path directory) {
        Path init = directory.resolve('grails-app').resolve('init')
        if (!Files.isDirectory(init, LinkOption.NOFOLLOW_LINKS)) {
            return null
        }
        List<Path> found = []
        int[] seen = [0]
        try {
            Files.walkFileTree(init, new SimpleFileVisitor<Path>() {

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (++seen[0] > MAX_INIT_FILES) {
                        return FileVisitResult.TERMINATE
                    }
                    if (file.getFileName().toString() == 'Application.groovy') {
                        found.add(file)
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (IOException ignored) {
            return null
        }
        if (found.size() != 1) {
            return null
        }
        return withoutComments(lines(found.first()) ?: [] as List<String>).findResult { String line ->
            Matcher declared = PACKAGE.matcher(line)
            declared.matches() ? declared.group(1) : null
        }
    }
}
