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

package org.grails.compiler.boot

import grails.boot.GrailsPluginApplication
import grails.boot.config.GrailsAutoConfiguration
import grails.compiler.ast.AstTransformer
import grails.compiler.ast.GlobalClassInjectorAdapter
import grails.plugins.metadata.PluginSource
import grails.util.Environment
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.control.SourceUnit
import org.grails.boot.context.web.GrailsAppServletInitializer
import org.grails.compiler.injection.GrailsASTUtils
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.web.WebApplicationInitializer

import java.lang.reflect.Modifier

/**
 * A transformation that automatically produces a Spring servlet initializer for a class that extends GrailsConfiguration. Given a class "Application" that
 * extends {@link GrailsAutoConfiguration}, it produces:
 *
 * <pre>
 * <code>
 *
 * class ApplicationLoader extends SpringBootServletInitializer {
 *
 *     protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
 *         return application.sources(Application)
 *     }
 * }
 * </code>
 * </pre>
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@AstTransformer
class BootInitializerClassInjector extends GlobalClassInjectorAdapter {

    public static final ClassNode GRAILS_CONFIGURATION_CLASS_NODE = ClassHelper.make(GrailsAutoConfiguration)
    public static final ClassNode PLUGIN_SOURCE_ANNOTATION = ClassHelper.make(PluginSource)

    @Override
    void performInjectionInternal(SourceUnit source, ClassNode classNode) {
        // if this is a plugin source, then exit
        if( classNode.getAnnotations(PLUGIN_SOURCE_ANNOTATION) ) {
            return
        }
        // don't generate for plugins
        if( classNode.getNodeMetaData('isPlugin') ) return


        if(GrailsASTUtils.isAssignableFrom(GRAILS_CONFIGURATION_CLASS_NODE, classNode) && !GrailsASTUtils.isSubclassOfOrImplementsInterface(classNode, GrailsPluginApplication.name)) {
            def methods = classNode.getMethods("main")
            for(MethodNode mn in methods) {
                if(Modifier.isStatic(mn.modifiers) && Modifier.isPublic(mn.modifiers)) {

                    def mainMethodBody = mn.code
                    if(mainMethodBody instanceof BlockStatement) {
                        BlockStatement bs = (BlockStatement)mainMethodBody
                        if( !bs.statements.isEmpty() ) {

                            def methodCallExpression = new MethodCallExpression(
                                    new ClassExpression(ClassHelper.make(System)),
                                    "setProperty",
                                    new ArgumentListExpression(
                                            new ConstantExpression(Environment.STANDALONE),
                                            new ConstantExpression(Boolean.TRUE.toString())
                                    )
                            )
                            ExpressionStatement statement = new ExpressionStatement(methodCallExpression)
                            bs.statements.add(0, statement)
                        }
                    }

                    def loaderClassNode = new ClassNode("${classNode.name}Loader", Modifier.PUBLIC, ClassHelper.make(GrailsAppServletInitializer))

                    loaderClassNode.addInterface(ClassHelper.make(WebApplicationInitializer))

                    def springApplicationBuilder = ClassHelper.make(SpringApplicationBuilder)

                    def parameter = new Parameter(springApplicationBuilder, "application")
                    def methodBody = new BlockStatement()

                    methodBody.addStatement( new ExpressionStatement( new MethodCallExpression( new VariableExpression(parameter), "sources", new ClassExpression(classNode))))
                    loaderClassNode.addMethod( new MethodNode("configure", Modifier.PROTECTED, springApplicationBuilder, [parameter] as Parameter[], [] as ClassNode[], methodBody))
                    source.getAST().addClass(
                            loaderClassNode
                    )

                    break
                }
            }
        }
    }
}
