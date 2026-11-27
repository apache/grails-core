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
package org.grails.gradle.plugin.core

import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.compile.AbstractCompile

import org.apache.grails.gradle.common.PropertyFileUtils

/**
 * Drops the entries of a factories file the Grails compiler generated that name a class the
 * compilation no longer produces, keeping those declared by the hand-authored file it merged in.
 *
 * <p>The compiler writes a registration as it compiles the class, so it can only change one while it
 * is compiling. When the only change is a deleted source that nothing depends on, incremental
 * compilation deletes the class file and compiles nothing, and the entry naming the class would
 * survive into what the build packages: a registration of a class that cannot be loaded. This runs
 * after the compilation, when every class it produces has been written.</p>
 *
 * <p>Only an incremental Groovy compilation, which a build opts into with
 * {@code options.incremental = true}, leaves such an entry behind. The default compilation
 * recompiles everything and cleans its destination directory first, so the compiler writes the
 * generated file again from the classes that remain and this action finds nothing to drop.</p>
 *
 * @since 8.0.1
 */
@CompileStatic
class PruneGeneratedFactories implements Action<Task> {

    static final String GRAILS_FACTORIES_LOCATION = 'META-INF/grails.factories'
    static final String CLI_FACTORIES_LOCATION = 'META-INF/grails-cli.factories'

    private static final String REGISTERED = 'grailsPruneGeneratedFactories'

    private final String factoriesLocation
    private final List<File> handAuthoredFiles

    PruneGeneratedFactories(String factoriesLocation, List<File> handAuthoredFiles) {
        this.factoriesLocation = factoriesLocation
        this.handAuthoredFiles = handAuthoredFiles
    }

    /**
     * @param factoriesLocation the generated file, relative to the compilation's destination directory
     * @param handAuthoredPaths the project-relative files the compiler merges into it
     */
    static void register(Project project, AbstractCompile task, String factoriesLocation, List<String> handAuthoredPaths) {
        String marker = "${REGISTERED}:${factoriesLocation}"
        if (task.extensions.extraProperties.has(marker)) {
            return
        }
        task.extensions.extraProperties.set(marker, true)
        task.doLast(new PruneGeneratedFactories(factoriesLocation,
                handAuthoredPaths.collect { String path -> project.layout.projectDirectory.file(path).asFile }))
    }

    @Override
    void execute(Task task) {
        File classesDirectory = ((AbstractCompile) task).destinationDirectory.get().asFile
        File factoriesFile = new File(classesDirectory, factoriesLocation)
        if (!factoriesFile.isFile()) {
            return
        }
        Properties generated = load(factoriesFile)
        Map<String, Set<String>> declared = [:]
        for (File handAuthoredFile : handAuthoredFiles) {
            if (handAuthoredFile.isFile()) {
                Properties handAuthored = load(handAuthoredFile)
                for (String type : handAuthored.stringPropertyNames()) {
                    declared.computeIfAbsent(type) { new HashSet<String>() }.addAll(names(handAuthored.getProperty(type)))
                }
            }
        }

        Properties kept = new SortedProperties()
        for (String type : generated.stringPropertyNames()) {
            Set<String> declaredForType = declared.getOrDefault(type, Collections.<String> emptySet())
            List<String> names = names(generated.getProperty(type)).findAll { String name ->
                declaredForType.contains(name) || new File(classesDirectory, "${name.replace('.', '/')}.class").isFile()
            }
            if (names) {
                kept.setProperty(type, names.join(','))
            }
        }
        if (kept == generated) {
            return
        }
        if (kept.isEmpty()) {
            factoriesFile.delete()
            return
        }
        // the same form the compiler writes: ISO-8859-1, sorted, and without the timestamp comment
        factoriesFile.withWriter(StandardCharsets.ISO_8859_1.name()) { Writer writer ->
            kept.store(writer, 'Grails Factories File')
        }
        PropertyFileUtils.makePropertiesFileReproducible(factoriesFile)
    }

    private static Properties load(File file) {
        Properties properties = new Properties()
        file.withInputStream { InputStream input -> properties.load(input) }
        properties
    }

    private static List<String> names(String value) {
        value ? value.tokenize(',')*.trim().findAll() : []
    }

    private static class SortedProperties extends Properties {

        @Override
        Set<Map.Entry<Object, Object>> entrySet() {
            Map<Object, Object> sorted = new TreeMap<>()
            for (Map.Entry<Object, Object> entry : super.entrySet()) {
                sorted.put(entry.key, entry.value)
            }
            sorted.entrySet()
        }

    }

}
