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
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.xml.sax.SAXException

import javax.inject.Inject
import javax.xml.parsers.ParserConfigurationException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@CompileStatic
@CacheableTask
abstract class WriteGrailsVersionInfoTask extends DefaultTask {

    @Input
    final Property<String> projectVersion

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
            new GradleException("Unable to parse BOM publication file: ${pomFile.absolutePath}", e)
        }

        TreeMap<String, String> props = []
        props.put("grails.version", getProjectVersion().get())
        ((GPathResult) pom.getProperty("properties")).children().forEach(child -> {
            NodeChild node = (NodeChild) child
            props.put(node.name(), node.text())
        })

        try (OutputStream out = Files.newOutputStream(versions.toPath())) {
            for (Map.Entry<String, String> entry : props.entrySet()) {
                getLogger().info("Writing version property: {} => {}", entry.getKey(), entry.getValue())
                String line = "${entry.getKey()}=${entry.getValue()}\n" as String
                out.write(line.getBytes(StandardCharsets.ISO_8859_1))
            }
        }
    }
}
