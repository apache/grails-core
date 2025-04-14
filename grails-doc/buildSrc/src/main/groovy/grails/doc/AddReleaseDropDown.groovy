/*
* Copyright 2024 The Unity Foundation
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package grails.doc

import grails.doc.dropdown.SoftwareVersion
import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

/**
 * Task to add a release dropdown to the documentation
 *
 * @author Puneet Behl
 * @since 6.2.1
 */
@CompileStatic
class AddReleaseDropDown extends DefaultTask {

    private static final String GRAILS_DOC_BASE_URL = "https://docs.grails.org"
    private static final String GITHUB_API_BASE_URL = "https://api.github.com"

    @Optional
    @Input
    String slug = "grails/grails-doc"

    @Optional
    @Input
    String docsDirName = "manual"

    @Optional
    @Input
    String version = project.version

    @InputFiles
    List<File> inputFiles = []

    @OutputDirectory
    String outputDir = project.layout.buildDirectory.dir("modified-pages").get().asFile.absolutePath

    /**
     * Add the release dropdown to the documentation
     */
    @TaskAction
    void addReleaseDropDown() {
        final String versionHtml = "<p><strong>Version:</strong> ${version}</p>"
        inputFiles.forEach { inputFile ->
            final String absolutePath = inputFile.absolutePath
            String page = absolutePath.substring(absolutePath.lastIndexOf(docsDirName) + docsDirName.size(), absolutePath.size())
            String selectHtml = select(options(version, page))
            final Path modifiedPage = Paths.get(outputDir + page)
            Files.createDirectories(modifiedPage.getParent())
            final String versionWithSelectHtml = "<p><strong>Version:</strong>&nbsp;<span style='width:100px;display:inline-block;'>${selectHtml}</span></p>"
            modifiedPage.toFile().text = inputFile.text.replace(versionHtml, versionWithSelectHtml)
        }
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

        final Object result = listRepoTags("grails/grails-core")
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
     * @param repoSlug The slug of the repository. e.g. grails/grails-core
     * @return The list of tags in the repository
     */
    private Object listRepoTags(String repoSlug) {
        final String json = new URL(GITHUB_API_BASE_URL + "/repos/" + repoSlug + "/tags").text
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
