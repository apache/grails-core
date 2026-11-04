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
 * hand-authored source registrations, and drops the registration of a class compiled again
 * once it no longer qualifies. The factories file location is supplied by the caller,
 * so the writer is shared by transformations targeting different registration files
 * (e.g. {@code META-INF/grails.factories} and {@code META-INF/grails-cli.factories}).
 *
 * @since 8.0
 */
@CompileStatic
class FactoriesFileWriter {

    /**
     * Registers the class as an implementation of the given super type in the factories file
     * when the class is a non-abstract subtype, and otherwise drops it from that type's entry
     * (see {@link #removeFromFactories}).
     *
     * @param classNode the compiled class
     * @param superType the factory type to register the class under
     * @param compilationTargetDirectory the compilation output directory
     * @param factoriesLocation the factories file path relative to the target directory
     * @param sourceFactoriesLocations project-relative paths of hand-authored factories files to merge
     * @return {@code true} when the class was registered
     */
    static boolean updateFactoriesWithType(ClassNode classNode, ClassNode superType, File compilationTargetDirectory,
                                           String factoriesLocation, List<String> sourceFactoriesLocations) {
        if (!GrailsASTUtils.isSubclassOfOrImplementsInterface(classNode, superType) || Modifier.isAbstract(classNode.getModifiers())) {
            removeFromFactories(classNode.name, superType, compilationTargetDirectory, factoriesLocation, sourceFactoriesLocations)
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
        loadSourceFactories(props, compilationTargetDirectory, sourceFactoriesLocations)

        addToProps(props, superTypeName, classNodeName)

        store(props, factoriesFile)
        return true
    }

    /**
     * Drops the class from the given super type's entry in the factories file, unless a
     * hand-authored factories file registers it, and deletes the file once no entry is left.
     *
     * <p>Incremental compilation recompiles a changed class without the others, so a class that
     * is no longer a concrete subtype, or one its transformation no longer registers, would
     * otherwise keep the entry written by the compilation that registered it.</p>
     *
     * @param className the binary name of the compiled class
     * @param superType the factory type the class may have been registered under
     * @param compilationTargetDirectory the compilation output directory
     * @param factoriesLocation the factories file path relative to the target directory
     * @param sourceFactoriesLocations project-relative paths of hand-authored factories files
     * @return {@code true} when an entry was dropped
     */
    static boolean removeFromFactories(String className, ClassNode superType, File compilationTargetDirectory,
                                       String factoriesLocation, List<String> sourceFactoriesLocations) {
        File factoriesFile = new File(compilationTargetDirectory, factoriesLocation)
        if (!factoriesFile.isFile()) {
            return false
        }
        def superTypeName = superType.getName()
        def props = CollectionFactory.createSortedProperties(false)
        loadFromFile(props, factoriesFile)
        Set<String> names = namesOf(props, superTypeName)
        if (!names.contains(className)) {
            return false
        }
        Properties handAuthored = new Properties()
        loadSourceFactories(handAuthored, compilationTargetDirectory, sourceFactoriesLocations)
        if (namesOf(handAuthored, superTypeName).contains(className)) {
            return false
        }

        names.remove(className)
        if (names) {
            props.put(superTypeName, names.join(','))
        } else {
            props.remove(superTypeName)
        }
        if (props.isEmpty()) {
            factoriesFile.delete()
        } else {
            store(props, factoriesFile)
        }
        return true
    }

    private static void loadSourceFactories(Properties props, File compilationTargetDirectory, List<String> sourceFactoriesLocations) {
        File sourceDirectory = findSourceDirectory(compilationTargetDirectory)
        if (sourceDirectory != null) {
            for (String sourceFactoriesLocation : sourceFactoriesLocations) {
                File sourceFactoriesFile = new File(sourceDirectory, sourceFactoriesLocation)
                loadFromFile(props, sourceFactoriesFile)
            }
        }
    }

    private static void store(Properties props, File factoriesFile) {
        // ISO-8859-1 to stay consistent with Properties.load(InputStream) and the
        // ISO-8859-1 rewrite performed by makePropertiesFileReproducible below
        factoriesFile.withWriter(StandardCharsets.ISO_8859_1.name()) { Writer writer ->
            props.store(writer, 'Grails Factories File')
        }

        PropertyFileUtils.makePropertiesFileReproducible(factoriesFile)
    }

    private static Set<String> namesOf(Properties props, String superTypeName) {
        String names = props.getProperty(superTypeName)
        names ? new LinkedHashSet<String>(names.tokenize(',')*.trim()) : new LinkedHashSet<String>()
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
