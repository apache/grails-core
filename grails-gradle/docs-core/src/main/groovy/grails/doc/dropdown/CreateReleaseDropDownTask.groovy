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

import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CompileStatic
class CreateReleasesDropdownTask extends DefaultTask {

    @Input
    String slug

    @Input
    String version

    @OutputFile
    File guide

    @OutputFile
    File index

    @TaskAction
    void modifyHtmlAndAddReleasesDropdown() {

        String selectHtml = composeSelectHtml()

        String versionHtml = "<p><strong>Version:</strong> ${version}</p>"
        String versionWithSelectHtml = "<p><strong>Version:</strong>&nbsp;<span style='width:100px;display:inline-block;'>${selectHtml}</span></p>"
        guide.text = guide.text.replace(versionHtml, versionWithSelectHtml)
        index.text = index.text.replace(versionHtml, versionWithSelectHtml)
    }

    String composeSelectHtml() {
        String repo = slug.split('/')[1]
        String org = slug.split('/')[0]
        JsonSlurper slurper = new JsonSlurper()
        String json = new URL("https://api.github.com/repos/${slug}/tags").text
        def result = slurper.parseText(json)
        String selectHtml = "<select onChange='window.document.location.href=this.options[this.selectedIndex].value;'>"
        String snapshotHref = "https://${org}.github.io/${repo}/snapshot/guide/single.html"
        if (version.endsWith("-SNAPSHOT")) {
            selectHtml += "<option selected='selected' value='${snapshotHref}'>SNAPSHOT</option>"
        } else {
            selectHtml += "<option value='${snapshotHref}'>SNAPSHOT</option>"
        }
        parseSoftwareVersions(result).each { softwareVersion ->
            String versionName = softwareVersion.versionText
            String href = "https://${org}.github.io/${repo}/${versionName}/guide/single.html"
            if (slug == 'apache/grails-core') {
                href = "https://docs.grails.org/${versionName}/guide/single.html"
            }
            if (version == versionName) {
                selectHtml += "<option selected='selected' value='${href}'>${versionName}</option>"
            } else {
                selectHtml += "<option value='${href}'>${versionName}</option>"
            }
        }
        selectHtml += '</select>'
        selectHtml
    }

    @CompileDynamic
    List<SoftwareVersion> parseSoftwareVersions(Object result) {
        result.findAll { it.name.startsWith('v') }.collect { SoftwareVersion.build(it.name.replace('v', '')) }.sort().unique().reverse()
    }
}
