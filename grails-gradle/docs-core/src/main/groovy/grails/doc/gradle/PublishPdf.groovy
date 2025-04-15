/* Copyright 2004-2005 the original author or authors.
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
package grails.doc.gradle


import grails.doc.PdfPublisher
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task for generating a gdoc-based PDF user guide. Assumes the
 * single page HTML user guide has already been created in the default
 * location.
 */
class PublishPdf extends DefaultTask {
    @Input String pdfName = "single.pdf"
    @Input String language = ""
    @OutputDirectory File outputDirectory = project.layout.buildDirectory.get().asFile

    @TaskAction
    def publish() {
        File outputDir = new File(outputDirectory, language ?: "")
        try {
            PdfPublisher.publishPdfFromHtml(outputDir, "guide/single.html", pdfName)
        }
        catch (Exception ex) {
            ex.printStackTrace()
        }
    }
}
