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
package grails.doc

import grails.doc.dropdown.SoftwareVersion
import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

import javax.inject.Inject
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.stream.Collectors

/**
 * Duplicates the documentation and modifies source files to add a release dropdown to the documentation
 * @since 6.2.1
 */
@CompileStatic
@CacheableTask
abstract class AddReleaseDropDown extends DefaultTask {

    private static final String GRAILS_DOC_BASE_URL = "https://docs.grails.org"
    private static final String GITHUB_API_BASE_URL = "https://api.github.com"

    @Input
    final Property<String> slug

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    final DirectoryProperty docsDirectory

    @Input
    final Property<String> version

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    final ConfigurableFileCollection inputFiles

    @OutputDirectory
    final DirectoryProperty outputDir

    @Inject
    AddReleaseDropDown(ObjectFactory objects, Project project) {
        slug = objects.property(String).convention('apache/grails-doc')
        docsDirectory = objects.directoryProperty().convention(project.layout.buildDirectory.dir('manual'))
        version = objects.property(String).convention(project.provider { project.version.toString() })
        inputFiles = objects.fileCollection()
        outputDir = objects.directoryProperty().convention(project.layout.buildDirectory.dir("modified-pages"))
        group = 'documentation'
    }

    /**
     * Add the release dropdown to the documentation
     */
    @TaskAction
    void addReleaseDropDown() {
        String projectVersion = version.get()

        final String versionHtml = "<p><strong>Version:</strong> ${projectVersion}</p>"
        Path guideDirectory = docsDirectory.get().asFile.toPath()
        Path targetOutputDirectory = outputDir.get().asFile.toPath()

        Map<String, Path> filesToChange = inputFiles.collectEntries { [it.absolutePath, it.toPath()] }
        Files.walkFileTree(guideDirectory, new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = targetOutputDirectory.resolve(guideDirectory.relativize(dir))
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir)
                }
                FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = targetOutputDirectory.resolve(guideDirectory.relativize(file))
                if (Files.exists(targetFile)) {
                    Files.deleteIfExists(targetFile)
                }

                String absolutePath = targetFile.toAbsolutePath().toString()
                if (filesToChange.containsKey(absolutePath)) {
                    //Need to add the version dropdown
                    String page = guideDirectory.toFile().relativePath(file.toFile())
                    String selectHtml = select(options(projectVersion, page))

                    final String versionWithSelectHtml = "<p><strong>Version:</strong>&nbsp;<span style='width:100px;display:inline-block;'>${selectHtml}</span></p>"
                    targetFile.toFile().text = file.text.replace(versionHtml, versionWithSelectHtml)

                    filesToChange.remove(absolutePath)
                }
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
                FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
                throw new GradleException("Unable to copy file: ${file}", e)
            }
        })
    }

    /**
     * Generate the options for the select tag.
     *
     * @param version The current version of the documentation
     * @param page The page to add the dropdown to
     * @return The list of options for the select tag
     */
    private List<String> options(String version, String page) {
        List<String> options = []
        final String snapshotHref = GRAILS_DOC_BASE_URL + "/snapshot" + page
        options << option(snapshotHref, "SNAPSHOT", version.endsWith("-SNAPSHOT"))

        final Object result = listRepoTags("apache/grails-core")
        parseSoftwareVersions(result)
                .forEach { softwareVersion ->
                    final String versionName = softwareVersion.versionText
                    final String href = GRAILS_DOC_BASE_URL + "/" + versionName  + page
                    options << option(href, versionName, version == versionName)
                }
        options
    }

    /**
     * List all tags in the repository using the GitHub API.
     *
     * @param repoSlug The slug of the repository. e.g. apache/grails-core
     * @return The list of tags in the repository
     */
    private Object listRepoTags(String repoSlug) {
        URL url = new URL(GITHUB_API_BASE_URL + "/repos/" + repoSlug + "/tags")
        URLConnection connection = url.openConnection()
        connection.setRequestProperty("User-Agent", "apache/grails-core")

        // See https://github.com/orgs/community/discussions/42748#discussioncomment-4709316
        String token = System.getenv('GITHUB_TOKEN')
        if(token) {
            connection.setRequestProperty("Authorization", "Bearer ${token}")
        }

        final String json = connection.inputStream.text

        def result = new JsonSlurper().parseText(json)
        result
    }


    /**
     * Generate the select tag
     *
     * @param options The List of options tags for the select tag
     * @return The select tag with the options
     */
    private String select(List<String> options) {
        String selectHtml = "<select onChange='window.document.location.href=this.options[this.selectedIndex].value;'>"
        options.each { option ->
            selectHtml += option
        }
        selectHtml += '</select>'
        selectHtml
    }

    /**
     * Generate the option tag
     *
     * @param value The URL to navigate to
     * @param text The version to display
     * @param selected Whether the option is selected
     *
     * @return The option tag
     */
    private String option(String value, String text, boolean selected = false) {
        if (selected) {
            return "<option selected='selected' value='${value}'>${text}</option>"
        } else {
            return "<option value='${value}'>${text}</option>"
        }
    }

    /**
     * Parse the software versions from the resultant JSON
     *
     * @param result List of all tags in the repository.
     * @return The list of software versions
     */
    @CompileDynamic
    private List<SoftwareVersion> parseSoftwareVersions(def result) {
        result.stream()
            .filter(v -> v.name.startsWith('v'))
            .map(v -> v.name.replace('v', ''))
            .map(SoftwareVersion::build)
            .sorted()
            .distinct()
            .collect(Collectors.toList())
            .reverse()
    }
}
