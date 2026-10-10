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

import java.nio.file.Files

import groovy.ant.AntBuilder

import org.apache.tools.ant.BuildLogger
import org.apache.tools.ant.Project

import grails.doc.macros.HiddenMacro

/**
 * Collects the settings a user guide is built from and drives a {@link DocPublisher} with them.
 *
 * <p>Deliberately free of Gradle types. The guide is rendered in a process of its own - booting
 * a JRuby runtime for AsciidoctorJ costs more than the rest of the build put together, and it
 * used to do that inside the Gradle daemon - so everything here has to travel as plain values.
 * Keeping it Gradle-free is also what lets it be tested directly.</p>
 */
class UserGuideBuilder {

    /** Directory holding the guide's sources, containing {@code guide/toc.yml}. */
    File sourceDir

    /** Directory holding doc.properties and any image, css, font, js or style overrides. */
    File resourcesDir

    /** Directory the rendered guide is written to. Emptied first. */
    File targetDir

    boolean asciidoc = true

    /** Language sub-directory to build, or empty for the default one. */
    String language = ''

    /** Base URL of the 'edit this page' links. */
    String sourceRepo = ''

    /** Extra properties files merged over doc.properties, in order. */
    List<File> propertiesFiles = []

    /** Engine properties, merged over the properties files. */
    Map<String, Object> properties = [:]

    /** Engine properties whose values are file paths. */
    Map<String, File> propertiesWithFilePaths = [:]

    /**
     * Fully qualified names of extra Radeox macros. Names rather than instances, because a
     * macro instance cannot be handed to the process that renders the guide.
     */
    List<String> macroClassNames = []

    /**
     * Whether Ant's own INFO messages - every mkdir, copy and unjar the publisher does - are
     * printed. Gradle routed them to its hidden INFO level when this ran in the daemon.
     */
    boolean verboseAnt = false

    void build() {
        Properties combinedProperties = new Properties()

        File workingDir = Files.createTempDirectory('grails-doc-publish-guide').toFile()

        File docProperties = new File(resourcesDir, 'doc.properties')
        if (docProperties.exists()) {
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
        combinedProperties.putAll(properties)
        combinedProperties.putAll(propertiesWithFilePaths)

        File apiDir = targetDir
        apiDir.deleteDir()
        apiDir.mkdirs()

        def publisher = new DocPublisher(sourceDir, apiDir)
        publisher.ant = antBuilder()
        publisher.asciidoc = asciidoc
        publisher.workDir = workingDir
        publisher.apiDir = apiDir
        publisher.language = language ?: ''
        publisher.sourceRepo = sourceRepo ?: ''
        publisher.images = new File(resourcesDir, 'img')
        publisher.css = new File(resourcesDir, 'css')
        publisher.fonts = new File(resourcesDir, 'fonts')
        publisher.js = new File(resourcesDir, 'js')
        publisher.style = new File(resourcesDir, 'style')
        publisher.version = combinedProperties['grails.version']

        // Override doc.properties properties with their language-specific counterparts (if
        // those are defined). You just need to add entries like es.title or pt_PT.subtitle.
        if (language) {
            def pos = language.size() + 1
            def languageProps = combinedProperties.findAll { k, v -> k.startsWith("${language}.") }
            languageProps.each { k, v -> combinedProperties[k[pos..-1]] = v }
        }

        // Aliases and other doc.properties entries are passed in as engine properties. This
        // is how the doc title, subtitle, etc. are set.
        publisher.engineProperties = combinedProperties

        // Add custom macros.

        // {hidden} macro for enabling translations.
        publisher.registerMacro(new HiddenMacro())

        for (String macroClassName : macroClassNames) {
            publisher.registerMacro(
                    Class.forName(macroClassName, true, getClass().classLoader).getDeclaredConstructor().newInstance()
            )
        }

        // Radeox loads its bundles off the context class loader, which
        // unfortunately doesn't contain the grails-docs JAR. So, we
        // temporarily switch the DocPublisher class loader into the
        // thread so that the Radeox bundles can be found.
        def oldClassLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = publisher.getClass().classLoader

        try {
            publisher.publish()
        }
        finally {
            Thread.currentThread().contextClassLoader = oldClassLoader
            workingDir.deleteDir()
        }
    }

    /**
     * DocPublisher makes its own AntBuilder when given none, and a bare one logs at INFO.
     */
    private AntBuilder antBuilder() {
        AntBuilder ant = new AntBuilder()
        int level = verboseAnt ? Project.MSG_INFO : Project.MSG_WARN
        ant.antProject.buildListeners.each { listener ->
            if (listener instanceof BuildLogger) {
                listener.messageOutputLevel = level
            }
        }
        ant
    }
}
