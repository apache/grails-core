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
package grails.doc.gradle

import grails.doc.PdfPublisher
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

import javax.inject.Inject

/**
 * Gradle task for generating a gdoc-based PDF user guide. Assumes the
 * single page HTML user guide has already been created in the default
 * location.
 */
@CacheableTask
@CompileStatic
abstract class PublishPdf extends DefaultTask {
    @Optional
    @Input
    final Property<String> language

    @Input
    final Property<String> pdfName

    @OutputDirectory
    final Property<Directory> outputDirectory

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    final Property<RegularFile> guideSingleFile

    @Inject
    PublishPdf(ObjectFactory objects) {
        outputDirectory = objects.directoryProperty().convention(project.layout.buildDirectory.dir("pdf"))
        guideSingleFile = objects.fileProperty().convention(project.layout.buildDirectory.file('guide/single.html'))
        pdfName = objects.property(String).convention('single.pdf')
        language = objects.property(String).convention(null as String)
        group = 'documentation'
    }

    @TaskAction
    def publish() {
        File baseOutputDir = outputDirectory.get().asFile
        File i18nOutputDir = new File(baseOutputDir, language.getOrElse(''))
        File inputFile = guideSingleFile.get().asFile
        try {
            PdfPublisher.publishPdfFromHtml(i18nOutputDir, inputFile, pdfName.get())
        }
        catch (Exception ex) {
            throw new GradleException("Unable to generate PDF from ${inputFile.absolutePath}", ex)
        }
    }
}
