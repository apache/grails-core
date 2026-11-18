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
package org.apache.grails.buildsrc

import groovy.io.FileType

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

/**
 * Scans this project's own compiled main classes for {@code @AutoConfiguration} and writes
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, so the
 * file never needs to be hand-maintained. Mirrors how {@code META-INF/grails-plugin.xml} is already
 * generated from scanned {@code *GrailsPlugin} classes elsewhere in this build.
 *
 * <p>Class files are read directly with ASM rather than loaded. Loading required every supertype and
 * annotation of every candidate to be resolvable, and a candidate that failed to load could only be
 * warned about and skipped - so a genuine {@code @AutoConfiguration} could drop out of the generated
 * file while the build stayed green, which is the outcome this task exists to prevent. Reading the
 * annotation table needs nothing beyond the class file itself, so that failure mode is gone and
 * there is no scan classpath to get wrong.
 */
abstract class GenerateAutoConfigurationImportsTask extends DefaultTask {

    static final String IMPORTS_RESOURCE_PATH =
            'META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports'

    static final String AUTO_CONFIGURATION_ANNOTATION = 'org.springframework.boot.autoconfigure.AutoConfiguration'

    private static final String AUTO_CONFIGURATION_DESCRIPTOR =
            'L' + AUTO_CONFIGURATION_ANNOTATION.replace('.' as char, '/' as char) + ';'

    private static final String CLASS_FILE_EXTENSION = '.class'

    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getClassesDirs()

    /**
     * The module's own resource directories, checked for a hand-maintained copy of the imports file.
     * This task's output lands at the same archive path, so a module keeping both would feed the
     * resource into the jar twice.
     */
    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getResourcesDirs()

    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory()

    @TaskAction
    void generate() {
        List<File> handMaintained = handMaintainedImportsFiles(resourcesDirs.files)
        if (handMaintained) {
            throw new GradleException("${path} generates ${IMPORTS_RESOURCE_PATH}, but this module also " +
                    "keeps one by hand at ${handMaintained*.path.join(', ')}. Both land at the same path in " +
                    'the jar, so the resource would be contributed twice. Delete the hand-maintained file - ' +
                    'the generated one lists every @AutoConfiguration in this module.')
        }

        SortedSet<String> discovered = scan(classesDirs.files)
        File importsFile = outputDirectory.file(IMPORTS_RESOURCE_PATH).get().asFile
        importsFile.parentFile.mkdirs()
        importsFile.text = discovered.isEmpty() ? '' : discovered.join('\n') + '\n'
    }

    /**
     * The hand-maintained copies of the imports file found among {@code resourcesDirs}. Static so it
     * is directly unit-testable without running a real Gradle task.
     *
     * @param resourcesDirs the module's resource source directories
     * @return every existing {@code META-INF/spring/...AutoConfiguration.imports} among them
     */
    static List<File> handMaintainedImportsFiles(Set<File> resourcesDirs) {
        resourcesDirs.collect { new File(it, IMPORTS_RESOURCE_PATH) }.findAll { it.isFile() }
    }

    /**
     * Static so it is directly unit-testable without running a real Gradle task.
     *
     * @param classesDirs directories of compiled {@code .class} files to inspect - a project's own
     * output only, never a dependency jar
     * @return the fully-qualified names of every top-level class annotated {@code @AutoConfiguration},
     * sorted for a deterministic, diff-friendly output file
     */
    static SortedSet<String> scan(Set<File> classesDirs) {
        SortedSet<String> discovered = new TreeSet<>()
        classesDirs.each { File dir -> scanDirectory(dir, discovered) }
        discovered
    }

    private static void scanDirectory(File dir, SortedSet<String> discovered) {
        if (!dir.exists()) {
            return
        }
        dir.eachFileRecurse(FileType.FILES) { File file ->
            // A Groovy closure compiles to a $-named class, which is never a candidate
            if (!file.name.endsWith(CLASS_FILE_EXTENSION) || file.name.contains('$')) {
                return
            }
            String name = autoConfigurationClassName(file)
            if (name) {
                discovered << name
            }
        }
    }

    /**
     * The class's binary name when its class file carries {@code @AutoConfiguration}, otherwise
     * {@code null}. The name comes from the class file rather than from its path, so a package
     * segment that happens to contain the file extension cannot mangle it.
     */
    private static String autoConfigurationClassName(File classFile) {
        ClassReader reader
        try {
            reader = new ClassReader(classFile.bytes)
        }
        catch (IOException | IllegalArgumentException notAClassFile) {
            // Unreadable or malformed: a real problem with the module's own output, not something to
            // skip quietly the way an unresolvable class used to be
            throw new GradleException("Could not read ${classFile} while generating ${IMPORTS_RESOURCE_PATH}",
                    notAClassFile)
        }
        AutoConfigurationDetector detector = new AutoConfigurationDetector()
        reader.accept(detector, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
        detector.present ? reader.className.replace('/' as char, '.' as char) : null
    }

    /**
     * Records whether the visited class declares {@code @AutoConfiguration} directly. Deliberately a
     * direct check and not a meta-annotation search, matching what {@code isAnnotationPresent} did.
     */
    private static class AutoConfigurationDetector extends ClassVisitor {

        boolean present

        AutoConfigurationDetector() {
            super(Opcodes.ASM9)
        }

        @Override
        AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (AUTO_CONFIGURATION_DESCRIPTOR == descriptor) {
                present = true
            }
            null
        }

    }

}
