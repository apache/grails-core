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

import grails.doc.DocPublisher
import grails.doc.macros.HiddenMacro
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

import javax.inject.Inject

/**
 * Gradle task for generating a gdoc-based HTML user guide.
 */
@CacheableTask
class PublishGuide extends DefaultTask {

    @Optional
    @Input
    final Property<String> language

    @Optional
    @Input
    final Property<String> sourceRepo

    @Optional
    @Input
    final MapProperty<String, Object> properties

    @Optional
    @Input
    final Property<Boolean> asciidoc

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    final ConfigurableFileCollection propertiesFiles

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    final DirectoryProperty sourceDir

    @Optional
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    final DirectoryProperty workDir

    @Optional
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    final DirectoryProperty resourcesDir

    @Optional
    @Input
    final ListProperty<Object> macros

    @OutputDirectory
    final DirectoryProperty targetDir

    @Inject
    PublishGuide(ObjectFactory objects, Project project) {
        language = objects.property(String).convention(null as String)
        sourceRepo = objects.property(String)
        properties = objects.mapProperty(String, Object).convention([:])
        asciidoc = objects.property(Boolean).convention(true)
        propertiesFiles = objects.fileCollection()
        sourceDir = objects.directoryProperty().convention(project.layout.projectDirectory.dir("src"))
        workDir = objects.directoryProperty().convention(project.layout.buildDirectory)
        resourcesDir = objects.directoryProperty().convention(project.layout.projectDirectory.dir("resources"))
        macros = objects.listProperty(Object).convention([])
        targetDir = objects.directoryProperty().convention(project.layout.buildDirectory.dir("docs"))
        group = 'documentation'
    }

    @TaskAction
    def publishGuide() {
        Properties combinedProperties = new Properties()

        File resources = resourcesDir.get().asFile
        File docProperties = new File(resources, 'doc.properties')
        if(docProperties.exists()) {
            docProperties.withInputStream { input ->
                combinedProperties.load(input)
            }
        }

        // Add properties from any optional properties files too.
        for (File f : propertiesFiles) {
            f.withInputStream { input ->
                combinedProperties.load(input)
            }
        }
        combinedProperties.putAll(properties.get())

        File apiDir = targetDir.get().asFile
        apiDir.deleteDir()
        apiDir.mkdirs()

        def publisher = new DocPublisher(sourceDir.get().asFile, apiDir)
        publisher.ant = project.ant
        publisher.asciidoc = asciidoc
        publisher.workDir = workDir.get().asFile
        publisher.apiDir = apiDir
        publisher.language = language.getOrElse('')
        publisher.sourceRepo = sourceRepo.getOrElse('')
        publisher.images = new File(resources, 'img')
        publisher.css = new File(resources, 'css')
        publisher.fonts = new File(resources, 'fonts')
        publisher.js = new File(resources, 'js')
        publisher.style = new File(resources, 'style')
        publisher.version = combinedProperties['grails.version']

        // Override doc.properties properties with their language-specific counterparts (if
        // those are defined). You just need to add entries like es.title or pt_PT.subtitle.
        if (language.isPresent()) {
            String lang = language.get()
            def pos = lang.size() + 1
            def languageProps = combinedProperties.findAll { k, v -> k.startsWith("${lang}.") }
            languageProps.each { k, v -> combinedProperties[k[pos..-1]] = v }
        }

        // Aliases and other doc.properties entries are passed in as engine properties. This
        // is how the doc title, subtitle, etc. are set.
        publisher.engineProperties = combinedProperties

        // Add custom macros.

        // {hidden} macro for enabling translations.
        publisher.registerMacro(new HiddenMacro())

        for (m in macros) {
            publisher.registerMacro(m)
        }

        // Radeox loads its bundles off the context class loader, which
        // unfortunately doesn't contain the grails-docs JAR. So, we
        // temporarily switch the DocPublisher class loader into the
        // thread so that the Radeox bundles can be found.
        def oldClassLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = publisher.getClass().classLoader

        publisher.publish()

        // Restore the old context class loader.
        Thread.currentThread().contextClassLoader = oldClassLoader
    }
}

