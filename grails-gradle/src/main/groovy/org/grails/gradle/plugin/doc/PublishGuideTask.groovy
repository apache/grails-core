/*
 * Copyright 2014-2024 original authors
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
package org.grails.gradle.plugin.doc

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.AbstractCompile

/**
 * A task used to publish the user guide if a publin that is in GDoc format
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class PublishGuideTask extends AbstractCompile {

    @InputDirectory
    @Optional
    File resourcesDir

    @InputFile
    @Optional
    File propertiesFile

    @InputDirectory
    @Optional
    File groovydocDir

    @InputDirectory
    @Optional
    File javadocDir

    @InputDirectory
    File srcDir

    @Override
    void setSource(Object source) {
        try {
            srcDir = project.file(source)
            if (srcDir.exists() && !srcDir.isDirectory()) {
                throw new IllegalArgumentException("The source for GSP compilation must be a single directory, but was $source")
            }
            super.setSource(source)
        } catch (e) {
            throw new IllegalArgumentException("The source for GSP compilation must be a single directory, but was $source")
        }
    }

    @CompileDynamic
    @TaskAction
    void compile() {
        def urls = getClasspath().files.collect() { File f -> f.toURI().toURL() }

        URLClassLoader classLoader = new URLClassLoader(urls as URL[], (ClassLoader) null)
        def docPublisher = classLoader.loadClass("grails.doc.DocPublisher").newInstance(srcDir, destinationDir, project.logger)
        if (groovydocDir?.exists()) {
            project.copy {
                from groovydocDir
                into "$destinationDir/gapi"
            }
        }
        if (javadocDir?.exists()) {
            project.copy {
                from javadocDir
                into "$destinationDir/api"
            }
        }
        docPublisher.title = project.name
        docPublisher.version = project.version
        docPublisher.src = srcDir
        docPublisher.target = destinationDir
        docPublisher.workDir = new File(project.buildDir, "doc-tmp")
        docPublisher.apiDir = destinationDir
        if (resourcesDir) {
            docPublisher.images = new File(resourcesDir, "img")
            docPublisher.css = new File(resourcesDir, "css")
            docPublisher.js = new File(resourcesDir, "js")
            docPublisher.style = new File(resourcesDir, "style")
        }
        if (propertiesFile) {
            docPublisher.propertiesFile = propertiesFile
        }


        docPublisher.publish()
    }

}
