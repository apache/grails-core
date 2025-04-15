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

import grails.doc.DocPublisher
import grails.doc.macros.HiddenMacro
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*

/**
 * Gradle task for generating a gdoc-based HTML user guide.
 */
class PublishGuide extends DefaultTask {

    @Optional @Input String language = ""
    @Optional @Input String sourceRepo
    @Optional @Input Properties properties = new Properties()
    @Optional @Input Boolean asciidoc = false
    @Optional @Input List propertiesFiles = []

    @InputDirectory File sourceDir = new File(project.projectDir, "src")
    @Optional @InputDirectory File workDir = project.layout.buildDirectory.get().asFile
    @Optional @InputDirectory File resourcesDir = new File(project.projectDir, "resources")

    @Optional @Input Collection macros = []

    @OutputDirectory File targetDir = project.layout.buildDirectory.dir("docs").get().asFile

    @TaskAction
    def publishGuide() {
        def props = new Properties()
        def docProperties = new File("${resourcesDir}/doc.properties")
        if(docProperties.exists()) {
            docProperties.withInputStream { input ->
                props.load(input)
            }
        }

        // Add properties from any optional properties files too.
        for (f in propertiesFiles) {
            (f as File).withInputStream {input ->
                props.load(input)
            }
        }

        props.putAll(properties)

        def publisher = new DocPublisher(sourceDir, targetDir)
        publisher.ant = project.ant
        publisher.asciidoc = asciidoc
        publisher.workDir = workDir
        publisher.apiDir = targetDir
        publisher.language = language ?: ''
        publisher.sourceRepo = sourceRepo
        publisher.images = project.file("${resourcesDir}/img")
        publisher.css = project.file("${resourcesDir}/css")
        publisher.fonts = project.file("${resourcesDir}/fonts")
        publisher.js = project.file("${resourcesDir}/js")
        publisher.style = project.file("${resourcesDir}/style")
        publisher.version = props."grails.version"

        // Override doc.properties properties with their language-specific counterparts (if
        // those are defined). You just need to add entries like es.title or pt_PT.subtitle.
        if (language) {
            def pos = language.size() + 1
            def languageProps = props.findAll { k, v -> k.startsWith("${language}.") }
            languageProps.each { k, v -> props[k[pos..-1]] = v }
        }

        // Aliases and other doc.properties entries are passed in as engine properties. This
        // is how the doc title, subtitle, etc. are set.
        publisher.engineProperties = props

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

