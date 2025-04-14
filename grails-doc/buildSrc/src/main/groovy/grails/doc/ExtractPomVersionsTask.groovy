/**
 * Copyright 2024 The Unity Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.doc

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Task to extract version information from the downloaded POM file.
 *
 * @author Puneet Behl
 * @since 6.2.0
 */
abstract class ExtractPomVersionsTask extends DefaultTask {

    @InputFile
    File pomFile

    @OutputFile
    Provider<RegularFile> outputPropertiesFile = project.layout.buildDirectory.file('extracted_properties.properties')

    @TaskAction
    void extractPomVersions() {
        // Read the downloaded POM file content
        def pomContent = pomFile.text

        // Parse the XML content
        def xml = new groovy.xml.XmlSlurper().parseText(pomContent)

        // Initialize a map to store extracted properties
        def extractedProperties = [:]

        // Extract version information
        def dependencies = xml.dependencyManagement.dependencies.dependency
        dependencies.each { dependency ->
            def groupId = dependency.groupId.text()
            def artifactId = dependency.artifactId.text()
            def version = dependency.version.text()

            // Convert groupId and artifactId to a suitable format for ASCIIDoc attributes
            def key = "${groupId.replaceAll('\\.', '_').replaceAll('-', '_')}_${artifactId.replaceAll('\\.', '_').replaceAll('-', '_')}_version"

            if (version ==~ /(\$\{.*?\})/) {
                def matcher = version =~ /(?<=(\$\{))(.*?)(?=\})/
                version = matcher[0][0]
                version = xml.'properties'."$version"
            }

            // Assign version value to the key in extracted properties
            extractedProperties[key] = version
        }

        // Write the extracted properties to a properties file
        outputPropertiesFile.get().getAsFile().text = extractedProperties.collect { key, value -> "$key=$value" }.join('\n')
    }
}

