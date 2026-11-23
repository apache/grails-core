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

package org.apache.grails.buildsrc

import groovy.transform.CompileStatic
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import groovy.xml.slurpersupport.NodeChild
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.xml.sax.SAXException

import javax.inject.Inject
import javax.xml.parsers.ParserConfigurationException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.regex.Matcher
import java.util.regex.Pattern

@CompileStatic
@CacheableTask
abstract class WriteGrailsVersionInfoTask extends DefaultTask {

    /** The key of the version of Gradle the wrapper of a generated application names */
    static final String GRADLE_VERSION = 'gradle.version'

    /** The key of the earliest version of Gradle a Grails application is built with */
    static final String GRADLE_MINIMUM = 'gradle.minimum'

    // a version of Gradle as a distribution is named by it: 9.7, 9.9.2, 9.10.0-rc-1
    private static final Pattern GRADLE = Pattern.compile(/(\d{1,4})\.(\d{1,4})(?:\.(\d{1,4}))?(?:-[A-Za-z0-9.\-]{1,40})?/)

    @Input
    final Property<String> projectVersion

    @Input
    @Optional
    final Property<String> endOfSupport

    /**
     * The version of Gradle the wrapper of a generated application names, written as
     * {@value #GRADLE_VERSION}: the generator renders the wrapper from it, and the command line
     * compares the wrapper of a project with it.
     */
    @Input
    @Optional
    final Property<String> gradleVersion

    /**
     * The earliest version of Gradle a Grails application is built with, written as
     * {@value #GRADLE_MINIMUM}, for the command line to hold the wrapper of a project to.
     */
    @Input
    @Optional
    final Property<String> gradleMinimumVersion

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    final RegularFileProperty bomPublicationFile

    @OutputDirectory
    final DirectoryProperty versionsDirectory

    @Inject
    WriteGrailsVersionInfoTask(ObjectFactory objects, Project project) {
        projectVersion = objects.property(String).convention(project.provider {
            project.version as String
        })
        endOfSupport = objects.property(String)
        gradleVersion = objects.property(String)
        gradleMinimumVersion = objects.property(String)
        bomPublicationFile = objects.fileProperty()
        versionsDirectory = objects.directoryProperty()
    }

    @TaskAction
    void writeVersionInfo() {
        File versionsDirectory = versionsDirectory.get().asFile
        if (versionsDirectory.exists()) {
            versionsDirectory.deleteDir()
        }
        versionsDirectory.mkdirs()

        File versions = new File(versionsDirectory, 'grails-versions.properties')

        File pomFile = bomPublicationFile.get().asFile

        GPathResult pom = null
        try {
            pom = new XmlSlurper().parse(pomFile)
        } catch (IOException | SAXException | ParserConfigurationException e) {
            throw new GradleException("Unable to parse BOM publication file: ${pomFile.absolutePath}", e)
        }

        TreeMap<String, String> props = []
        props.put('grails.version', getProjectVersion().get())
        if (endOfSupport.present) {
            String date = endOfSupport.get().trim()
            try {
                LocalDate.parse(date)
            } catch (DateTimeParseException e) {
                throw new GradleException("grailsEndOfSupport must be an ISO date (yyyy-MM-dd) but was: ${date}", e)
            }
            props.put('grails.endOfSupport', date)
        }
        ((GPathResult) pom.getProperty('properties')).children().forEach(child -> {
            NodeChild node = (NodeChild) child
            props.put(node.name(), node.text())
        })
        putGradleVersions(props)

        try (OutputStream out = Files.newOutputStream(versions.toPath())) {
            for (Map.Entry<String, String> entry : props.entrySet()) {
                getLogger().info('Writing version property: {} => {}', entry.getKey(), entry.getValue())
                String line = "${entry.getKey()}=${entry.getValue()}\n" as String
                out.write(line.getBytes(StandardCharsets.ISO_8859_1))
            }
        }
    }

    // The two versions of Gradle, each from the one property of the build that has it, after the
    // properties of the BOM: a property of the BOM of the same name is not taken for one of them
    private void putGradleVersions(Map<String, String> props) {
        Map<String, Property<String>> given = [(GRADLE_VERSION): gradleVersion, (GRADLE_MINIMUM): gradleMinimumVersion]
        for (Map.Entry<String, Property<String>> each : given.entrySet()) {
            if (!each.value.present) {
                continue
            }
            if (props.containsKey(each.key)) {
                throw new GradleException("The BOM has a property ${each.key}, which is the version of Gradle this task writes itself".toString())
            }
            String version = each.value.get().trim()
            if (!GRADLE.matcher(version).matches()) {
                throw new GradleException("${each.key} must be a version of Gradle, such as 9.9.2, but was: ${version}".toString())
            }
            props.put(each.key, version)
        }
        String version = props.get(GRADLE_VERSION)
        String minimum = props.get(GRADLE_MINIMUM)
        if (version != null && minimum != null && release(minimum) > release(version)) {
            throw new GradleException("The earliest version of Gradle Grails is built with, ${minimum}, is later than the version applications are generated with, ${version}".toString())
        }
    }

    // the major, minor and patch of a version, as one number to compare
    private static long release(String version) {
        Matcher matched = GRADLE.matcher(version)
        if (!matched.matches()) {
            throw new GradleException("Not a version of Gradle: ${version}".toString())
        }
        return Long.parseLong(matched.group(1)) * 100_000_000L + Long.parseLong(matched.group(2)) * 10_000L + Long.parseLong(matched.group(3) ?: '0')
    }
}
