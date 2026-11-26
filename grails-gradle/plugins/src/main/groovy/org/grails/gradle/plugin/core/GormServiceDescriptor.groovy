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

import java.nio.file.Files

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.GroovyCompile
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

/** Combines generated and authored ServiceLoader registrations for the main artifact. */
@CompileStatic
@CacheableTask
abstract class GormServiceDescriptor extends DefaultTask {

    static final String PATH = 'META-INF/services/org.grails.datastore.mapping.services.Service'

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getDescriptors()

    @OutputFile
    abstract RegularFileProperty getDescriptor()

    @TaskAction
    void merge() {
        Set<String> providers = new TreeSet<>()
        for (File input : descriptors.files) {
            input.eachLine('UTF-8') { String line ->
                String name = line.split('#', 2)[0].trim()
                if (name) providers.add(name)
            }
        }
        File output = descriptor.get().asFile
        if (providers) {
            output.parentFile.mkdirs()
            output.setText(providers.join('\n') + '\n', 'UTF-8')
        } else {
            Files.deleteIfExists(output.toPath())
        }
    }

    static void register(Project project) {
        project.tasks.named('compileGroovy', GroovyCompile).configure { GroovyCompile task ->
            task.doLast { GroovyCompile compile ->
                File classes = compile.destinationDirectory.get().asFile
                File index = new File(classes, PATH)
                if (index.isFile()) {
                    List<String> names = index.readLines('UTF-8')
                    List<String> kept = names.findAll { String name -> exposed(new File(classes, name.replace('.', '/') + '.class')) }
                    if (names != kept) {
                        if (kept) index.setText(kept.join('\n') + '\n', 'UTF-8')
                        else Files.delete(index.toPath())
                    }
                }
            }
        }
        def main = project.extensions.getByType(JavaPluginExtension).sourceSets.named('main')
        def merged = project.tasks.register('mergeGormServiceDescriptor', GormServiceDescriptor) { task ->
            task.descriptors.from(main.get().output.asFileTree.matching { include(PATH) })
            task.descriptor.set(project.layout.buildDirectory.file('generated/gorm-services/' + PATH))
        }
        def output = merged.flatMap { GormServiceDescriptor task -> task.descriptor }
        project.tasks.named('jar', Jar).configure { Jar jar ->
            jar.from(output) { org.gradle.api.file.CopySpec spec -> spec.into('META-INF/services') }
            jar.filesMatching(PATH) { FileCopyDetails entry ->
                if (entry.file != output.get().asFile) entry.exclude()
            }
        }
    }

    private static boolean exposed(File type) {
        if (!type.isFile()) return false
        boolean[] found = [false]
        new ClassReader(type.bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor == 'Lgrails/gorm/services/Service;') found[0] = true
                null
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
        found[0]
    }
}
