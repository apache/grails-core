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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Task to download the POM file for a given Grails version.
 *
 * @author Puneet Behl
 * @since 6.2.0
 */
abstract class DownloadPomTask extends DefaultTask {

    @Input
    String version

    @OutputFile
    Provider<RegularFile> outputFile = project.layout.buildDirectory.file('downloaded_pom.xml')

    @TaskAction
    def downloadPom() {

        // Define the POM URL based on the version
        def pomUrl
        if (version.endsWith('-SNAPSHOT')) {
            pomUrl = "https://repo.grails.org/ui/api/v1/download?repoKey=core&path=org%252Fgrails%252Fgrails-bom%252F${version}%252Fgrails-bom-${version}.pom&isNativeBrowsing=true"
        } else {
            pomUrl = "https://repo.grails.org/grails/core/org/grails/grails-bom/${version}/grails-bom-${version}.pom"
        }

        def pomContent = downloadPomContent(pomUrl)
        outputFile.get().getAsFile().text = pomContent
    }

    private String downloadPomContent(String pomUrl) {
        def connection = new URL(pomUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.requestMethod = 'GET'
        connection.connect()

        def inputStream = connection.inputStream
        def reader = new BufferedReader(new InputStreamReader(inputStream))
        def content = reader.getText()

        return content
    }
}

