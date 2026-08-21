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
package org.grails.compiler.injection

import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode

import org.springframework.core.CollectionFactory

import org.apache.grails.gradle.common.PropertyFileUtils

/**
 * Writes factory registrations for compiled classes into a factories file in the compilation
 * target directory, merging any existing entries from previous compilation runs and from
 * hand-authored source registrations. The factories file location is supplied by the caller,
 * so the writer is shared by transformations targeting different registration files
 * (e.g. {@code META-INF/grails.factories} and {@code META-INF/grails-cli.factories}).
 *
 * @since 8.0
 */
@CompileStatic
class FactoriesFileWriter {

    /**
     * Registers the class as an implementation of the given super type in the factories file
     * when the class is a non-abstract subtype.
     *
     * @param classNode the compiled class
     * @param superType the factory type to register the class under
     * @param compilationTargetDirectory the compilation output directory
     * @param factoriesLocation the factories file path relative to the target directory
     * @param sourceFactoriesLocations project-relative paths of hand-authored factories files to merge
     * @return {@code true} when the class was a subtype of the factory type
     */
    static boolean updateFactoriesWithType(ClassNode classNode, ClassNode superType, File compilationTargetDirectory,
                                           String factoriesLocation, List<String> sourceFactoriesLocations) {
        if (GrailsASTUtils.isSubclassOfOrImplementsInterface(classNode, superType)) {
            if (Modifier.isAbstract(classNode.getModifiers())) {
                return false
            }

            def classNodeName = classNode.name
            // Use SortedProperties to ensure a consistent order of entries for reproducible builds
            def props = CollectionFactory.createSortedProperties(false)
            def superTypeName = superType.getName()

            File factoriesFile = new File(compilationTargetDirectory, factoriesLocation)
            if (!factoriesFile.parentFile.exists()) {
                factoriesFile.parentFile.mkdirs()
            }
            loadFromFile(props, factoriesFile)

            File sourceDirectory = findSourceDirectory(compilationTargetDirectory)
            if (sourceDirectory != null) {
                for (String sourceFactoriesLocation : sourceFactoriesLocations) {
                    File sourceFactoriesFile = new File(sourceDirectory, sourceFactoriesLocation)
                    loadFromFile(props, sourceFactoriesFile)
                }
            }

            addToProps(props, superTypeName, classNodeName)

            // ISO-8859-1 to stay consistent with Properties.load(InputStream) and the
            // ISO-8859-1 rewrite performed by makePropertiesFileReproducible below
            factoriesFile.withWriter(StandardCharsets.ISO_8859_1.name()) { Writer writer ->
                props.store(writer, 'Grails Factories File')
            }

            PropertyFileUtils.makePropertiesFileReproducible(factoriesFile)

            return true
        }
        return false
    }

    private static void loadFromFile(Properties props, File factoriesFile) {
        if (factoriesFile.exists()) {
            Properties fileProps = new Properties()
            factoriesFile.withInputStream { InputStream input ->
                fileProps.load(input)
                fileProps.each { Map.Entry prop ->
                    addToProps(props, (String) prop.key, (String) prop.value)
                }
            }
        }
    }

    private static Properties addToProps(Properties props, String superTypeName, String classNodeNames) {
        // Exact-membership dedup: a substring test would collide distinct FQCNs where one is a
        // prefix of another (e.g. com.example.Foo and com.example.FooCommand, or MyCommand and
        // MyCommand2) and silently drop the second registration.
        Set<String> names = new LinkedHashSet<>()
        String existing = props.getProperty(superTypeName)
        if (existing) {
            names.addAll(existing.tokenize(',')*.trim())
        }
        names.addAll(classNodeNames.tokenize(',')*.trim())
        props.put(superTypeName, names.join(','))
        props
    }

    static File findSourceDirectory(File compilationTargetDirectory) {
        // Prefer the project base directory supplied by the build tool — more reliable than
        // walking up from the compile target, which may live under a non-standard output
        // directory (e.g. when project.buildDir is renamed). The Grails Gradle plugin
        // publishes this via GrailsAppBaseDirProvider on the compiler's forkOptions.
        String baseDirProp = System.getProperty('base.dir')
        if (baseDirProp) {
            File baseDir = new File(baseDirProp)
            if (baseDir.exists() && baseDir.isDirectory()) {
                return baseDir
            }
        }

        File sourceDirectory = compilationTargetDirectory
        while (sourceDirectory != null && !(sourceDirectory.name in ['build', 'target'])) {
            sourceDirectory = sourceDirectory.parentFile
        }
        sourceDirectory?.parentFile
    }
}
