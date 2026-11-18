/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.grails.gradle.plugin.aot

import groovy.json.JsonOutput
import groovy.transform.CompileStatic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Records the application's own classes so they survive into a native image.
 *
 * <p>Grails reaches an application's artefacts reflectively -- a controller's actions, a domain
 * class's properties, a tag library's methods -- and a precompiled page is looked up by the name
 * recorded for its view. A native image keeps only the members something asks for, so without this
 * the classes are present but unusable.</p>
 *
 * <p>The answer is already in the build output: the compiled classes are on disk and the pages are
 * listed in the manifest the GSP compiler writes. Reading them here means an application does not
 * have to run under the tracing agent to be buildable, which matters because the agent records only
 * the paths that were exercised -- a page never visited during the trace is a page missing from the
 * image.</p>
 *
 * @since 8.0
 */
@CacheableTask
@CompileStatic
abstract class GenerateNativeMetadataTask extends DefaultTask {

    /** Where the generated metadata is written within the artifact. */
    static final String METADATA_PATH = 'META-INF/native-image/grails/reachability-metadata.json'

    /** The manifest the GSP compiler writes, mapping a view to the class it compiled to. */
    static final String VIEWS_MANIFEST = 'gsp/views.properties'

    /** Compiled application classes. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getClassesDirs()

    /** Compiled pages, including the manifest naming them. */
    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getPageClassesDirs()

    /**
     * The classpath whose artifacts may carry their own pages. A plugin ships compiled pages and the
     * manifest naming them, and an application renders those as readily as its own.
     */
    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getPageClasspath()

    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory()

    @TaskAction
    void generate() {
        Set<String> types = [] as Set
        types.addAll(applicationClasses())
        types.addAll(pageClasses())
        types.addAll(pageClassesFromClasspath())

        List<Map<String, Object>> reflection = types.sort().collect { String type ->
            [
                    type: type,
                    allDeclaredMethods: true,
                    allDeclaredFields: true,
                    allDeclaredConstructors: true
            ] as Map<String, Object>
        }

        File target = new File(outputDirectory.get().asFile, METADATA_PATH)
        target.parentFile.mkdirs()
        target.text = JsonOutput.prettyPrint(JsonOutput.toJson([reflection: reflection]))
        logger.info("Recorded ${reflection.size()} application types for reflection")
    }

    /** The pages a plugin contributes, read from the manifest each of its artifacts carries. */
    private Set<String> pageClassesFromClasspath() {
        Set<String> found = [] as Set
        for (File entry : pageClasspath.files) {
            if (!entry.isFile() || !entry.name.endsWith('.jar')) {
                continue
            }
            try {
                new java.util.jar.JarFile(entry).withCloseable { java.util.jar.JarFile jar ->
                    java.util.jar.JarEntry manifest = jar.getJarEntry(VIEWS_MANIFEST)
                    if (manifest == null) {
                        return
                    }
                    Properties views = new Properties()
                    jar.getInputStream(manifest).withCloseable { views.load(it) }
                    for (Object value : views.values()) {
                        found << value.toString()
                    }
                    // the closures a page declares are reached the same way its body is
                    for (java.util.jar.JarEntry e : jar.entries()) {
                        String name = e.name
                        if (name.endsWith('.class') && name.contains('$') && !name.contains('/')) {
                            String simple = name[0..<name.length() - 6]
                            if (views.values().any { simple.startsWith(it.toString() + '$') }) {
                                found << simple
                            }
                        }
                    }
                }
            }
            catch (Exception ignored) {
                logger.info("Could not read pages from ${entry.name}")
            }
        }
        found
    }

    /** Every compiled application class, including the closures its artefacts declare. */
    private Set<String> applicationClasses() {
        Set<String> found = [] as Set
        for (File dir : classesDirs.files) {
            if (!dir.isDirectory()) {
                continue
            }
            dir.eachFileRecurse { File file ->
                if (file.name.endsWith('.class')) {
                    String relative = dir.toPath().relativize(file.toPath()).toString()
                    found << relative[0..<relative.length() - 6].replace(File.separator, '.')
                }
            }
        }
        found
    }

    /**
     * The classes the pages compiled to. Taken from the manifest rather than the directory listing,
     * so a class left behind by an earlier build is not recorded.
     */
    private Set<String> pageClasses() {
        Set<String> found = [] as Set
        for (File dir : pageClassesDirs.files) {
            File manifest = new File(dir, VIEWS_MANIFEST)
            if (!manifest.isFile()) {
                continue
            }
            Properties views = new Properties()
            manifest.withInputStream { views.load(it) }
            Set<String> pages = views.values().collect { it.toString() } as Set
            found.addAll(pages)
            // a page's closures are reached the same way its body is; the directory is walked once
            // rather than per page, which matters for an application with many views
            dir.eachFileRecurse { File file ->
                if (!file.name.endsWith('.class')) {
                    return
                }
                String name = file.name[0..<file.name.length() - 6]
                int nested = name.indexOf('$')
                if (nested > 0 && pages.contains(name.substring(0, nested))) {
                    found << name
                }
            }
        }
        found
    }
}
