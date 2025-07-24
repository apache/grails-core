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

package grails.doc.dropdown

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

import javax.inject.Inject

import groovy.transform.CompileStatic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Duplicates the documentation and modifies source files to add a release dropdown to the documentation
 * @since 6.2.1
 */
@CompileStatic
@CacheableTask
abstract class CreateReleaseDropDownTask extends DefaultTask {

    @Input
    final Property<String> docBaseUrl

    @Input
    final Property<String> githubSlug

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    final DirectoryProperty sourceDocsDirectory

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    final RegularFileProperty gitTags

    @Input
    final Property<String> projectVersion

    @Input
    final Property<SoftwareVersion> minimumVersion

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    final ConfigurableFileCollection filesToAddDropdowns

    @OutputDirectory
    final DirectoryProperty modifiedPagesDirectory

    @Input
    final Property<String> versionHtml

    @Input
    final Property<String> additionalPath

    @Inject
    CreateReleaseDropDownTask(ObjectFactory objects, Project project) {
        group = 'documentation'
        githubSlug = objects.property(String).convention(project.provider {
            project.findProperty('githubSlug') as String ?: 'apache/grails-doc'
        })
        sourceDocsDirectory = objects.directoryProperty().convention(project.layout.buildDirectory.dir('manual'))
        projectVersion = objects.property(String).convention(project.provider { project.version as String })
        filesToAddDropdowns = objects.fileCollection()
        modifiedPagesDirectory = objects.directoryProperty().convention(project.layout.buildDirectory.dir("modified-pages"))
        gitTags = objects.fileProperty().convention(project.layout.buildDirectory.file('git-tags.txt'))
        minimumVersion = objects.property(SoftwareVersion).convention(new SoftwareVersion(major: 5))
        docBaseUrl = objects.property(String).convention("https://docs.grails.org")
        versionHtml = objects.property(String).convention(project.provider{ "<p><strong>Version:</strong> " + projectVersion.get() + "</p>" })
        additionalPath = objects.property(String).convention("")
    }

    /**
     * Add the release dropdown to the documentation*/
    @TaskAction
    void createReleaseDropDown() {
        Path targetOutputDirectory = modifiedPagesDirectory.get().asFile.toPath()
        if (Files.exists(targetOutputDirectory)) {
            targetOutputDirectory.deleteDir()
        }
        Files.createDirectories(targetOutputDirectory)

        String projectVersion = projectVersion.get()

        final List<String> result = gitTags.get().asFile.readLines()*.trim()
        List<SoftwareVersion> softwareVersions = parseSoftwareVersions(projectVersion, result)
        logger.lifecycle("Detected Project Version: ${projectVersion} and Software Versions: ${softwareVersions*.versionText.join(',')}")

        Path guideDirectory = sourceDocsDirectory.get().asFile.toPath()

        Map<String, Path> filesToAddDropdown = filesToAddDropdowns.collectEntries { [it.absolutePath, it.toPath()] }
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
                String absolutePath = targetFile.toAbsolutePath().toString()
                if (filesToAddDropdown.containsKey(absolutePath)) {
                    String pageRelativePath = guideDirectory.toFile().relativePath(file.toFile())
                    String selectHtml = select(options(projectVersion, pageRelativePath, softwareVersions))

                    final String versionWithSelectHtml = "<p><strong>Version:</strong>&nbsp;<span style='display:inline-block;'>${selectHtml}</span></p>"
                    targetFile.toFile().text = file.text.replace(versionHtml.get(), versionWithSelectHtml)

                    filesToAddDropdown.remove(absolutePath)
                } else {
                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
                }
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
     * @param pageRelativePath The relative path for the page to add the dropdown to
     * @param softwareVersions The list of software versions to include in the dropdown
     * @return The list of options for the select tag
     */
    private List<String> options(String version, String pageRelativePath, List<SoftwareVersion> softwareVersions) {
        List<String> options = []

        final String snapshotHref = docBaseUrl.get() + "/snapshot/" + additionalPath.get() + pageRelativePath
        options << option(snapshotHref, "SNAPSHOT", version.endsWith("-SNAPSHOT"))

        softwareVersions
                .forEach { softwareVersion ->
                    final String versionName = softwareVersion?.versionText
                    final String href = docBaseUrl.get() + "/" + (versionName?.endsWith("-SNAPSHOT") ? "snapshot" : versionName) + "/" + additionalPath.get() + pageRelativePath
                    options << option(href, versionName, version == versionName)
                }

        options
    }

    /**
     * Generate the select tag
     *
     * @param options The List of options tags for the select tag
     * @return The select tag with the options
     */
    private String select(List<String> options) {
        String selectHtml = "<select onChange='window.document.location.href=this.options[this.selectedIndex].value;'>"
        options.each { option -> selectHtml += option
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
     * @param minimumVersion Minimum SoftwareVersion to include in the list. Default version is 0.0.0
     * @return The list of software versions
     */
    private List<SoftwareVersion> parseSoftwareVersions(String projectVersion, List<String> tags) {
        def minimum = minimumVersion.get()

        LinkedHashSet<String> combined = ["v${projectVersion}" as String]
        combined.addAll(tags)

        combined.findAll { it?.startsWith('v') }
                .collect { it.replace('v', '') }
                .collect { SoftwareVersion.build(it) }
                .findAll { it >= minimum }
                .toSorted()
                .unique()
                .reverse()
    }
}
