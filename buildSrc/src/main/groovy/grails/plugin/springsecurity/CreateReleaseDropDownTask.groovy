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
package grails.plugin.springsecurity

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.util.regex.Pattern

abstract class CreateReleaseDropDownTask extends DefaultTask {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+([-.])?((RC|M)[0-9])?")

    @Input
    abstract Property<String> getGithubSlug()

    @Input
    abstract Property<String> getCurrentVersion()

    @Optional
    @Input
    abstract ListProperty<String> getVersions()

    @Optional
    @OutputFile
    abstract RegularFileProperty getGuide()

    @OutputFile
    abstract RegularFileProperty getIndex()

    @TaskAction
    void createReleaseDropdown() {
        def softwareVersions = getSoftwareVersions()
        def versionHtml = "<span id=\"revnumber\">version ${currentVersion.get()}</span>"
        def selectHtml = createVersionSelectDropDownHtml(softwareVersions)
        def versionWithSelectHtml = "<span id=\"revnumber\">version:&nbsp;<span style=\"display:inline-block;\">$selectHtml</span></span>"
        if (guide.present) {
            def guideFile = guide.get().asFile
            guideFile.text = guideFile.text.replace(versionHtml, versionWithSelectHtml)
        }
        def indexFile = index.get().asFile
        indexFile.text = indexFile.text.replace(versionHtml, versionWithSelectHtml)
    }

    private List<SoftwareVersion> getSoftwareVersions() {
        List<SoftwareVersion> softwareVersions = []
        if (versions.get().isEmpty()) {
            def tags = new JsonSlurper()
                    .parse(new URL("https://api.github.com/repos/${this.githubSlug.get()}/tags"))
            if (tags instanceof List) {
                tags.stream()
                        .<String> map(tagInfo -> { return (String) tagInfo['name'] })
                        .filter(version -> version.startsWith('v'))
                        .map(version -> version.replace('v', ''))
                        .filter(VERSION_PATTERN.asPredicate())
                        .map(version -> SoftwareVersion.build(version))
                        .forEach(softwareVersions::add)
            }
        } else {
            versions.get().stream()
                    .map(version -> SoftwareVersion.build(version))
                    .forEach(softwareVersions::add)
        }

        softwareVersions = softwareVersions.sort().unique().reverse()
        softwareVersions
    }

    private String createVersionSelectDropDownHtml(List<SoftwareVersion> softwareVersions) {
        def repo = githubSlug.get().split('/')[1]
        def selectHtml = '<select onChange="window.document.location.href=this.options[this.selectedIndex].value;">'
        def snapshotHref = "https://apache.github.io/$repo/snapshot/index.html"
        if (currentVersion.get().endsWith('-SNAPSHOT')) {
            selectHtml += "<option selected=\"selected\" value=\"$snapshotHref\">SNAPSHOT</option>"
        } else {
            selectHtml += "<option value=\"$snapshotHref\">SNAPSHOT</option>"
            selectHtml += "<option selected=\"selected\" value=\"https://apache.github.io/$repo/${currentVersion.get()}/index.html\">${currentVersion.get()}</option>"
        }
        softwareVersions.forEach {softwareVersion ->
            def versionName = softwareVersion.versionText
            def href = "https://apache.github.io/$repo/$versionName/index.html"
            if (currentVersion.get() == versionName) {
                selectHtml += "<option selected=\"selected\" value=\"$href\">$versionName</option>"
            } else {
                selectHtml += "<option value=\"$href\">$versionName</option>"
            }
        }
        selectHtml += '</select>'
        selectHtml
    }
}
