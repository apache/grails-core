/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the 'License'); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an 'AS IS' BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.compiler.injection

import java.lang.reflect.Modifier

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.groovy.ast.tools.AnnotatedNodeUtils
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.SourceUnit

import org.springframework.util.ClassUtils

import grails.compiler.ast.AstTransformer
import grails.compiler.ast.GrailsArtefactClassInjector
import grails.io.ResourceUtils
import grails.util.BuildSettings
import org.grails.core.artefact.ApplicationArtefactHandler
import org.grails.io.support.GrailsResourceUtils
import org.grails.io.support.UrlResource

import static org.codehaus.groovy.ast.tools.GeneralUtils.args
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt

/**
 * Injector for the 'Application' class
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@AstTransformer
class ApplicationClassInjector implements GrailsArtefactClassInjector {

    public static final String EXCLUDE_MEMBER = 'exclude'
    public static final List<String> EXCLUDED_AUTO_CONFIGURE_CLASSES = [
            'org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration',
            'org.springframework.boot.reactor.autoconfigure.ReactorAutoConfiguration',
            'org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration'
    ]

    /**
     * Auto-configuration classes that are conditionally excluded when
     * a specific plugin is detected on the classpath. Each entry maps a
     * plugin class (checked via {@link ClassUtils#isPresent}) to the
     * auto-configuration class name to exclude, with an optional system
     * property that can disable the exclusion.
     *
     * <p>The system property defaults to {@code 'true'} (exclusion enabled).
     * Set it to {@code 'false'} in {@code gradle.properties} to opt out:</p>
     * <pre>
     * systemProp.grails.autoconfigure.exclude.liquibase=false
     * </pre>
     */
    static final List<Map<String, String>> CONDITIONAL_EXCLUSIONS = [
            [
                    pluginClass: 'org.grails.plugins.databasemigration.DatabaseMigrationGrailsPlugin',
                    excludeClass: 'org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration',
                    systemProperty: 'grails.autoconfigure.exclude.liquibase'
            ]
    ]

    ApplicationArtefactHandler applicationArtefactHandler = new ApplicationArtefactHandler()

    private static final List<Integer> transformedInstances = []

    @Override
    String[] getArtefactTypes() {
        return [ApplicationArtefactHandler.TYPE] as String[]
    }

    @Override
    void performInjection(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        performInjection(source, classNode)
    }

    @Override
    void performInjection(SourceUnit source, ClassNode classNode) {
        performInjectionOnAnnotatedClass(source, classNode)
    }

    @Override
    @CompileDynamic
    void performInjectionOnAnnotatedClass(SourceUnit source, ClassNode classNode) {
        if (applicationArtefactHandler.isArtefact(classNode)) {
            def objectId = Integer.valueOf(System.identityHashCode(classNode))
            if (!transformedInstances.contains(objectId)) {
                transformedInstances << objectId

                List<Statement> statements = [
                        stmt(callX(classX(System), 'setProperty', args(propX(classX(BuildSettings), 'MAIN_CLASS_NAME'), constX(classNode.name))))
                ]
                classNode.addStaticInitializerStatements(statements, true)

                def packageNamesMethod = classNode.getMethod('packageNames', GrailsASTUtils.ZERO_PARAMETERS)

                if (packageNamesMethod == null || packageNamesMethod.declaringClass != classNode) {
                    def collectionClassNode = GrailsASTUtils.replaceGenericsPlaceholders(ClassHelper.make(Collection), [E: ClassHelper.make(String)])

                    def packageNamesBody = new BlockStatement()
                    def grailsAppDir = GrailsResourceUtils.getAppDir(new UrlResource(GrailsASTUtils.getSourceUrl(source)))
                    if (grailsAppDir.exists()) {

                        def packageNames = ResourceUtils.getProjectPackageNames(grailsAppDir.file.parentFile)
                                                        .collect() { String str -> new ConstantExpression(str) }
                        if (packageNames.any() { ConstantExpression packageName -> ['org', 'com', 'io', 'net'].contains(packageName.text) }) {
                            GrailsASTUtils.error(source, classNode, "Do not place Groovy sources in common package names such as 'org', 'com', 'io' or 'net' as this can result in performance degradation of classpath scanning")
                        }
                        packageNamesBody.addStatement(new ReturnStatement(new ExpressionStatement(new ListExpression(packageNames.toList()))))
                        AnnotatedNodeUtils.markAsGenerated(classNode, classNode.addMethod('packageNames', Modifier.PUBLIC, collectionClassNode, ZERO_PARAMETERS, null, packageNamesBody))
                    }
                }

                // Add @SpringBootConfiguration so that the Application class is picked up by @SpringBootTest
                addAnnotation('org.springframework.boot.SpringBootConfiguration', classNode)?.with {

                    GrailsASTUtils.addExpressionToAnnotationMember(it, 'proxyBeanMethods', constX(false))
                }
                addAnnotation('org.springframework.web.servlet.config.annotation.EnableWebMvc', classNode, 'jakarta.servlet.ServletContext')
                addAnnotation('org.springframework.boot.autoconfigure.EnableAutoConfiguration', classNode)?.with { annotation ->
                    EXCLUDED_AUTO_CONFIGURE_CLASSES.each {
                        GrailsASTUtils.addExpressionToAnnotationMember(
                                annotation,
                                'excludeName',
                                constX(it)
                        )
                    }
                    CONDITIONAL_EXCLUSIONS.each {
                        if (shouldExcludeConditionalAutoConfiguration(it)) {
                            GrailsASTUtils.addExpressionToAnnotationMember(
                                    annotation,
                                    'excludeName',
                                    constX(it.excludeClass)
                            )
                        }
                    }
                }
            }
        }
    }

    @Override
    boolean shouldInject(URL url) {
        if (url == null) return false
        def res = new UrlResource(url)
        return GrailsResourceUtils.isGrailsResource(res) && res.filename == 'Application.groovy'
    }

    private static boolean shouldExcludeConditionalAutoConfiguration(Map<String, String> target) {
        def classLoader = ApplicationClassInjector.classLoader
        Boolean.parseBoolean(System.getProperty(target.systemProperty, 'true'))
                && ClassUtils.isPresent(target.pluginClass, classLoader)
    }

    private AnnotationNode addAnnotation(String annotationClassName, ClassNode classNode, String conditionalClass = null) {
        def classLoader = getClass().classLoader
        if (ClassUtils.isPresent(conditionalClass ?: annotationClassName, classLoader)) {
            return GrailsASTUtils.addAnnotationOrGetExisting(classNode, ClassHelper.make(
                    classLoader.loadClass(annotationClassName)
            ))
        }
        return null
    }
}
