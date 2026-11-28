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
package org.grails.gradle.plugin.commands

import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

import groovy.json.JsonOutput

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

import org.apache.grails.gradle.model.cli.CommandArgumentsFile
import org.grails.gradle.plugin.core.GradleSpecification

/**
 * The application of the {@code command-catalog} test project, with the artifacts its build resolves
 * written into its {@code maven-repo} directory by the specification: the build reaches no
 * repository, and what a jar holds is whatever a feature needs it to hold.
 */
abstract class CommandProjectSpecification extends GradleSpecification {

    protected static final String GRAILS_VERSION = '0.0.1-test'
    protected static final String COMMAND_KEY = 'org.apache.grails.core.cli.ApplicationCommand'
    protected static final String CLI_FACTORIES = 'META-INF/grails-cli.factories'

    protected GradleRunner runner
    protected File projectDir

    /**
     * Sets the project up with the command contract and the command runner the Grails Gradle plugin
     * adds to every project, as jars that hold what a catalog reads of the real ones.
     */
    protected void setupCommandProject(String dependencies = '') {
        runner = setupTestResourceProject('command-catalog')
        projectDir = runner.projectDir
        publish('org.apache.grails', 'grails-core-cli', GRAILS_VERSION, [
                (CLI_FACTORIES): registrations('org.apache.grails.core.cli.ConfigReportCommand'),
                (descriptorPath('org.apache.grails.core.cli.ConfigReportCommand')):
                        descriptor('org.apache.grails.core.cli.ConfigReportCommand', 'config-report', 'Generates an AsciiDoc report of the application configuration'),
        ])
        publish('org.apache.grails', 'grails-console', GRAILS_VERSION, [:])
        commands(dependencies)
    }

    /** Writes the part of the build script a feature decides: what the application depends on. */
    protected void commands(String script) {
        // the stand-in for the class that runs a command reads a file of arguments with the code the real one uses
        File model = new File(CommandArgumentsFile.protectionDomain.codeSource.location.toURI())
        new File(projectDir, 'commands.gradle').text = """
            dependencies {
                implementation files('${model.absolutePath.replace('\\', '/')}')
            }
        """ + script
    }

    protected static String registrations(String... classNames) {
        "${COMMAND_KEY}=${classNames.join(',')}\n"
    }

    protected static String descriptorPath(String className) {
        "META-INF/grails-tools/${className}.json"
    }

    protected static String descriptor(String className, String name, String description = null) {
        JsonOutput.toJson([format: 1, class: className, name: name] +
                (description == null ? [:] : [description: description]) + [target: 'application', startsApplication: true])
    }

    /**
     * Writes a module into the repository of the project: a POM and a jar with these entries.
     *
     * @param entries the content of each entry by its path, text (stored as UTF-8) or bytes
     * @param manifest attributes of the manifest of the jar
     */
    protected File publish(String group, String artifact, String version, Map<String, Object> entries, Map<String, String> manifest = [:]) {
        File directory = new File(projectDir, "maven-repo/${group.replace('.', '/')}/${artifact}/${version}")
        directory.mkdirs()
        new File(directory, "${artifact}-${version}.pom").text = """\
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>${group}</groupId>
              <artifactId>${artifact}</artifactId>
              <version>${version}</version>
            </project>
            """.stripIndent()
        File jar = new File(directory, "${artifact}-${version}.jar")
        writeJar(jar, entries, manifest)
        jar
    }

    protected static void writeJar(File jar, Map<String, Object> entries, Map<String, String> manifest = [:]) {
        Manifest jarManifest = new Manifest()
        jarManifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, '1.0')
        manifest.each { String name, String value -> jarManifest.mainAttributes.putValue(name, value) }
        jar.withOutputStream { OutputStream output ->
            JarOutputStream jarOutput = new JarOutputStream(output, jarManifest)
            entries.each { String path, Object content ->
                jarOutput.putNextEntry(new JarEntry(path))
                jarOutput.write(content instanceof byte[] ? (byte[]) content : content.toString().getBytes('UTF-8'))
                jarOutput.closeEntry()
            }
            jarOutput.close()
        }
    }

    /** Writes a file of the project, text (stored as UTF-8) or bytes. */
    protected File projectFile(String path, Object content) {
        File file = new File(projectDir, path)
        file.parentFile.mkdirs()
        file.bytes = content instanceof byte[] ? (byte[]) content : content.toString().getBytes('UTF-8')
        file
    }

    /** The arguments of the tool, with the class of the application after them, as the stand-in for the class that runs a tool has them. */
    protected static List<String> arguments(BuildResult result) {
        said(result, 'ARGUMENT')
    }

    /** What the JVM of the tool was started with, as the stand-in for the class that runs a tool says. */
    protected static List<String> started(BuildResult result) {
        said(result, 'STARTED')
    }

    private static List<String> said(BuildResult result, String what) {
        List<String> lines = result.output.readLines()
        int count = lines.find { String line -> line.startsWith("${what}S=") }.substring(what.length() + 2) as int
        List<String> values = lines.findAll { String line -> line.startsWith("${what}[") }.collect { String line ->
            new String(line.substring(line.indexOf('<') + 1, line.lastIndexOf('>')).decodeHex(), 'UTF-8')
        }
        assert values.size() == count
        values
    }
}
