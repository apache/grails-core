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
package grails.boot.config

import groovy.transform.CompileStatic

import grails.boot.config.tools.ClassPathScanner
import org.grails.compiler.injection.AbstractGrailsArtefactTransformer

/**
 * Discovers the artefact classes that make up a Grails application by scanning the classpath relative
 * to an application class. This is the single implementation of the default scanning performed by
 * {@link GrailsAutoConfiguration#classes()}, also used by
 * {@link GrailsEarlyPluginRegistrationPostProcessor} to perform artefact discovery before Spring
 * Boot auto-configuration is processed.
 *
 * @since 8.0
 */
@CompileStatic
final class ApplicationArtefactScanner {

    private ApplicationArtefactScanner() {
    }

    /**
     * Scans for application classes in the package of the given application class, including any
     * classes registered by Grails artefact transformations.
     *
     * @param applicationClass The application class to scan relative to
     * @return The classes that constitute the Grails application
     */
    static Collection<Class> scanApplicationClasses(Class<?> applicationClass) {
        Package applicationPackage = applicationClass.package
        Collection<String> packageNames = applicationPackage != null ? [applicationPackage.name] : new ArrayList<String>()
        return scanApplicationClasses(applicationClass, packageNames)
    }

    /**
     * Scans for application classes in the given packages relative to the given application class,
     * including any classes registered by Grails artefact transformations.
     *
     * @param applicationClass The application class to scan relative to
     * @param packageNames The package names to scan
     * @return The classes that constitute the Grails application
     */
    static Collection<Class> scanApplicationClasses(Class<?> applicationClass, Collection<String> packageNames) {
        Collection<Class> classes = new HashSet<>()
        classes.addAll(new ClassPathScanner().scan(applicationClass, packageNames))
        classes.addAll(loadTransformedClasses(applicationClass.classLoader))
        return classes
    }

    /**
     * Loads the classes registered by Grails artefact transformations at compile time.
     *
     * @param classLoader The class loader to load the classes with
     * @return The transformed classes resolvable by the given class loader
     */
    static Collection<Class> loadTransformedClasses(ClassLoader classLoader) {
        Collection<Class> classes = []
        for (String className in AbstractGrailsArtefactTransformer.transformedClassNames) {
            try {
                classes << classLoader.loadClass(className)
            } catch (ClassNotFoundException ignored) {
            }
        }
        return classes
    }
}
