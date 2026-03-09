/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.grails.compiler.injection.testing

import java.lang.annotation.Annotation
import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.control.SourceUnit

import org.springframework.context.annotation.Import

import grails.testing.spring.IntegrationTestAutoConfiguration
import org.grails.compiler.injection.GrailsASTUtils

/**
 * Utility for applying integration-test auto-configurations during AST transformation.
 *
 * <p>The support scans {@code META-INF/grails/IntegrationTestAutoConfiguration.imports}
 * resources on the compilation classpath, loads candidate configuration classes, and
 * conditionally adds {@link Import} annotations to the target integration test class.</p>
 *
 * <p>Each candidate is filtered by the {@link IntegrationTestAutoConfiguration} marker:
 * required classes must be present and optional skip annotations on the target test class
 * are honored.</p>
 *
 * @since 7.1
 */
@CompileStatic
class IntegrationTestAutoConfigurationSupport {

    private static final ClassNode IMPORT_ANNOTATION = ClassHelper.make(Import)

    private static final String AUTO_TEST_CONFIGURATION_RESOURCE = 'META-INF/grails/IntegrationTestAutoConfiguration.imports'
    private static final String INTEGRATION_TEST_AUTO_CONFIGURATION = 'grails.testing.spring.IntegrationTestAutoConfiguration'

    /**
     * Resolves and applies registered integration-test auto-configurations to the target class.
     *
     * <p>Configurations are loaded from the resource registry and imported only when:</p>
     * <ul>
     *   <li>the class is loadable,</li>
     *   <li>it is annotated with {@link IntegrationTestAutoConfiguration},</li>
     *   <li>its {@code requiredClasses} are present,</li>
     *   <li>its {@code skipIfAnnotatedWith} constraints do not match, and</li>
     *   <li>the target class does not already import it explicitly.</li>
     * </ul>
     *
     * @param classNode the integration test class being transformed
     * @param source the current source unit used for class/resource resolution
     */
    static void addIntegrationTestAutoConfigurations(ClassNode classNode, SourceUnit source) {
        def registeredConfigClassNames = loadRegisteredAutoTestConfigurations(source)
        for (def configClassName in registeredConfigClassNames) {
            if (hasImportFor(classNode, configClassName)) {
                // Class already explicitly imports the config
                continue
            }
            def configClass = loadClass(configClassName, source)
            if (configClass == null || !hasAutoIntegrationMarker(configClass)) {
                continue
            }
            if (!requiredClassesPresent(configClass, source)) {
                continue
            }
            if (shouldSkipForTargetClass(classNode, configClass)) {
                continue
            }

            def importAnnotation = new AnnotationNode(IMPORT_ANNOTATION)
            importAnnotation.setMember('value', new ClassExpression(ClassHelper.make(configClassName)))
            classNode.addAnnotation(importAnnotation)
        }
    }

    /**
     * Loads auto-configuration class names from all matching registry resources.
     *
     * @param source the current source unit
     * @return ordered unique configuration class names
     */
    private static List<String> loadRegisteredAutoTestConfigurations(SourceUnit source) {
        def classLoader = source?.classLoader ?: getClass().classLoader
        def names = new LinkedHashSet<String>()
        def resources = classLoader.getResources(AUTO_TEST_CONFIGURATION_RESOURCE)
        while (resources.hasMoreElements()) {
            def url = resources.nextElement()
            url.openStream().withCloseable { stream ->
                new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).withCloseable { reader ->
                    String line
                    while ((line = reader.readLine()) != null) {
                        String parsed = parseAutoTestConfigurationLine(line)
                        if (parsed != null) {
                            names.add(parsed)
                        }
                    }
                }
            }
        }
        return new ArrayList<String>(names)
    }

    /**
     * Parses a single registry line into a class name.
     *
     * @param line raw resource line
     * @return trimmed class name, or {@code null} for blank/comment lines
     */
    private static String parseAutoTestConfigurationLine(String line) {
        def trimmed = line?.trim()
        if (!trimmed || trimmed.startsWith('#')) {
            return null
        }
        if (trimmed.contains('#')) {
            trimmed = trimmed.substring(0, trimmed.indexOf('#')).trim()
        }
        trimmed ?: null
    }

    /**
     * Attempts to load a class using the source class loader first.
     *
     * @param className fully qualified class name
     * @param source current source unit; may be {@code null}
     * @return resolved class, or {@code null} when not loadable
     */
    private static Class loadClass(String className, SourceUnit source) {
        def classLoader = source?.classLoader ?: getClass().classLoader
        try {
            return Class.forName(className, false, classLoader)
        }
        catch (Throwable ignored) {
            return null
        }
    }

    /**
     * Checks whether a class is present on the compilation classpath.
     *
     * @param className fully qualified class name
     * @param source current source unit
     * @return {@code true} if the class can be resolved
     */
    private static boolean isClassPresent(String className, SourceUnit source) {
        loadClass(className, source) != null
    }

    /**
     * Verifies whether the candidate config is marked for integration-test auto import.
     *
     * @param configClass candidate configuration class
     * @return {@code true} when the marker annotation is present
     */
    private static boolean hasAutoIntegrationMarker(Class configClass) {
        GrailsASTUtils.findAnnotation(new ClassNode(configClass), IntegrationTestAutoConfiguration) != null
    }

    /**
     * Evaluates {@code requiredClasses} declared on the marker annotation.
     *
     * @param configClass candidate configuration class
     * @param source current source unit
     * @return {@code true} if all required classes are available
     */
    private static boolean requiredClassesPresent(Class configClass, SourceUnit source) {
        def required = getMarkerStringArray(configClass, 'requiredClasses')
        for (def className in required) {
            if (!isClassPresent(className, source)) {
                return false
            }
        }
        return true
    }

    /**
     * Evaluates {@code skipIfAnnotatedWith} against the target test class.
     *
     * @param classNode test class receiving generated imports
     * @param configClass candidate configuration class
     * @return {@code true} if the import should be skipped
     */
    private static boolean shouldSkipForTargetClass(ClassNode classNode, Class configClass) {
        def annotationNames = getMarkerStringArray(configClass, 'skipIfAnnotatedWith')
        for (def annotationName in annotationNames) {
            if (hasAnnotation(classNode, annotationName)) {
                return true
            }
        }
        return false
    }

    /**
     * Reads a string-array attribute from the marker annotation reflectively.
     *
     * @param configClass candidate configuration class
     * @param methodName annotation attribute accessor name
     * @return attribute values, or an empty list if unavailable
     */
    private static List<String> getMarkerStringArray(Class configClass, String methodName) {
        def markerClass = loadClass(INTEGRATION_TEST_AUTO_CONFIGURATION, null)
        if (markerClass == null) {
            return Collections.emptyList()
        }
        def marker = configClass.getAnnotation(markerClass as Class<? extends Annotation>)
        if (marker == null) {
            return Collections.emptyList()
        }
        try {
            def values = markerClass.getMethod(methodName).invoke(marker) as String[]
            return values ? Arrays.asList(values) : Collections.emptyList() as List<String>
        }
        catch (Throwable ignored) {
            return Collections.emptyList()
        }
    }

    /**
     * Checks whether a class node has a specific annotation by fully qualified name.
     *
     * @param classNode class node to inspect
     * @param annotationClassName fully qualified annotation class name
     * @return {@code true} if present
     */
    private static boolean hasAnnotation(ClassNode classNode, String annotationClassName) {
        for (def annotationNode in classNode.annotations) {
            if (annotationNode.classNode?.name == annotationClassName) {
                return true
            }
        }
        return false
    }

    /**
     * Checks whether the class already declares an explicit {@link Import} for the target class.
     *
     * @param classNode class node to inspect
     * @param importedClassName fully qualified imported class name
     * @return {@code true} if already imported
     */
    private static boolean hasImportFor(ClassNode classNode, String importedClassName) {
        for (def annotationNode in classNode.annotations) {
            if (annotationNode.classNode?.name != IMPORT_ANNOTATION.name) {
                continue
            }
            def value = annotationNode.getMember('value')
            if (value instanceof ClassExpression && ((ClassExpression) value).type?.name == importedClassName) {
                return true
            }
            if (value instanceof ListExpression) {
                for (def expression in ((ListExpression) value).expressions) {
                    if (expression instanceof ClassExpression && ((ClassExpression) expression).type?.name == importedClassName) {
                        return true
                    }
                }
            }
        }
        return false
    }
}
