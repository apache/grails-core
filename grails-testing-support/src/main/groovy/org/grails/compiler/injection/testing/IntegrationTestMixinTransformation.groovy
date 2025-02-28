package org.grails.compiler.injection.testing
import grails.boot.test.GrailsApplicationContextLoader
import grails.boot.config.GrailsAutoConfiguration
import grails.testing.mixin.integration.Integration
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import static org.codehaus.groovy.ast.tools.GeneralUtils.*
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.syntax.Token
import org.codehaus.groovy.syntax.Types
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.grails.compiler.injection.GrailsASTUtils
import org.grails.io.support.MainClassFinder
import org.grails.testing.context.junit4.GrailsJunit4ClassRunner
import org.grails.testing.context.junit4.GrailsTestConfiguration
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.util.ClassUtils

import java.lang.reflect.Modifier
/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class IntegrationTestMixinTransformation implements ASTTransformation {

    static final ClassNode MY_TYPE = new ClassNode(Integration.class)
    public static final ClassNode CONTEXT_CONFIG_ANNOTATION = ClassHelper.make(ContextConfiguration)
    public static final ClassNode GRAILS_APPLICATION_CONTEXT_LOADER = ClassHelper.make(GrailsApplicationContextLoader)
    public static final ClassNode WEB_APP_CONFIGURATION = ClassHelper.make(WebAppConfiguration)
    public static final ClassNode INTEGRATION_TEST_CLASS_NODE = ClassHelper.make(SpringBootTest)
    public static final ClassNode SPRING_APPLICATION_CONFIGURATION_CLASS_NODE = ClassHelper.make(GrailsTestConfiguration)
    public static final ClassNode RUN_WITH_ANNOTATION_NODE = ClassHelper.make(RunWith)
    public static final ClassNode SPRING_JUNIT4_CLASS_RUNNER = ClassHelper.make(GrailsJunit4ClassRunner)
    public static final String SPEC_CLASS = "spock.lang.Specification"

    @Override
    void visit(ASTNode[] astNodes, SourceUnit source) {
        if (!(astNodes[0] instanceof AnnotationNode) || !(astNodes[1] instanceof AnnotatedNode)) {
            throw new RuntimeException("Internal error: wrong types: ${astNodes[0].getClass()} / ${astNodes[1].getClass()}")
        }

        AnnotatedNode parent = (AnnotatedNode) astNodes[1]
        AnnotationNode annotationNode = (AnnotationNode) astNodes[0]
        if (!MY_TYPE.equals(annotationNode.classNode) || !(parent instanceof ClassNode)) {
            return
        }

        ClassExpression applicationClassExpression = (ClassExpression)annotationNode.getMember('applicationClass')

        final applicationClassNode
        if(applicationClassExpression) {
            applicationClassNode = applicationClassExpression.getType()
            if(!applicationClassNode.isDerivedFrom(ClassHelper.make(GrailsAutoConfiguration))) {
                GrailsASTUtils.error(source, applicationClassExpression, "Invalid applicationClass attribute value [${applicationClassNode.getName()}].  The applicationClass attribute must specify a class which extends grails.boot.config.GrailsAutoConfiguration.", true)
            }
        } else {
            String mainClass = MainClassFinder.searchMainClass(source.source.URI)
            if(mainClass) {
                applicationClassNode = ClassHelper.make(mainClass)
            } else {
                applicationClassNode = null
            }
        }

        if(applicationClassNode) {
            ClassNode classNode = (ClassNode) parent

            weaveIntegrationTestMixin(classNode, applicationClassNode)

        }

    }

    public void weaveIntegrationTestMixin(ClassNode classNode, ClassNode applicationClassNode) {
        if(applicationClassNode == null) return


        enableAutowireByName(classNode)

        if (GrailsASTUtils.isSubclassOf(classNode, SPEC_CLASS)) {
            // first add context configuration
            // Example: @ContextConfiguration(loader = GrailsApplicationContextLoader, classes = Application)
            def contextConfigAnn = new AnnotationNode(CONTEXT_CONFIG_ANNOTATION)
            contextConfigAnn.addMember("loader", new ClassExpression(GRAILS_APPLICATION_CONTEXT_LOADER))
            contextConfigAnn.addMember("classes", new ClassExpression(applicationClassNode))
            classNode.addAnnotation(contextConfigAnn)

            enhanceGebSpecWithPort(classNode)

        } else {
            // Must be a JUnit 4 test so add JUnit spring annotations
            // @RunWith(SpringJUnit4ClassRunner)
            def runWithAnnotation = new AnnotationNode(RUN_WITH_ANNOTATION_NODE)
            runWithAnnotation.addMember("value", new ClassExpression(SPRING_JUNIT4_CLASS_RUNNER))
            classNode.addAnnotation(runWithAnnotation)

            // @SpringApplicationConfiguration(classes = Application)
            def contextConfigAnn = new AnnotationNode(SPRING_APPLICATION_CONFIGURATION_CLASS_NODE)
            contextConfigAnn.addMember("classes", new ClassExpression(applicationClassNode))
            classNode.addAnnotation(contextConfigAnn)
        }

        // now add integration test annotations
        // @SpringBootTest
        def servletApi = null
        try {
            servletApi = Class.forName("jakarta.servlet.ServletContext", false, getClass().classLoader)
        }
        catch(Exception e) {
            // ignore
        }
        
        
        if (servletApi != null) {

            if( GrailsASTUtils.findAnnotation(classNode, SpringBootTest) == null) {
                AnnotationNode webIntegrationTestAnnotation = GrailsASTUtils.addAnnotationOrGetExisting(classNode, SpringBootTest)
                webIntegrationTestAnnotation.addMember("webEnvironment", propX(classX(SpringBootTest.WebEnvironment.class), "RANDOM_PORT"))
                if(classNode.getProperty("serverPort") == null) {

                    def serverPortField = new FieldNode("serverPort", Modifier.PROTECTED, ClassHelper.Integer_TYPE, classNode, new ConstantExpression(8080))
                    def valueAnnotation = new AnnotationNode(ClassHelper.make(Value))
                    valueAnnotation.setMember("value", new ConstantExpression('${local.server.port}'))
                    serverPortField.addAnnotation(valueAnnotation)

                    classNode.addProperty(new PropertyNode(serverPortField, Modifier.PUBLIC, null, null ))
                }
            }
        } else {
            classNode.addAnnotation(new AnnotationNode(INTEGRATION_TEST_CLASS_NODE))
        }
    }

    protected void enableAutowireByName(ClassNode classNode) {
        classNode.addInterface(ClassHelper.make(ApplicationContextAware))

        def body = new BlockStatement()

        def ctxClass = ClassHelper.make(ApplicationContext)
        def p = new Parameter(ctxClass, "ctx")

        def getBeanFactoryMethodCall = new MethodCallExpression(new VariableExpression(p), "getAutowireCapableBeanFactory", GrailsASTUtils.ZERO_ARGUMENTS)

        def args = new ArgumentListExpression(new VariableExpression("this"), new ConstantExpression(AutowireCapableBeanFactory.AUTOWIRE_BY_NAME), new ConstantExpression(Boolean.FALSE))
        def autoMethodCall = new MethodCallExpression(getBeanFactoryMethodCall, "autowireBeanProperties", args)
        body.addStatement(new ExpressionStatement(autoMethodCall))
        classNode.addMethod("setApplicationContext", Modifier.PUBLIC, ClassHelper.VOID_TYPE, [p] as Parameter[], null, body)
    }

    protected void enhanceGebSpecWithPort(ClassNode classNode) {
        if (GrailsASTUtils.isSubclassOf(classNode, "geb.spock.GebSpec")) {
            def contextPathParameter = new Parameter(ClassHelper.make(String), 'serverContextPath')
            def cpValueAnnotation = new AnnotationNode(ClassHelper.make(Value))
            cpValueAnnotation.setMember('value', new ConstantExpression('${server.servlet.context-path:/}'))
            contextPathParameter.addAnnotation(cpValueAnnotation)

            def serverPortParameter = new Parameter(ClassHelper.make(Integer.TYPE), 'serverPort')
            def spValueAnnotation = new AnnotationNode(ClassHelper.make(Value))
            spValueAnnotation.setMember('value', new ConstantExpression('${local.server.port}'))
            serverPortParameter.addAnnotation(spValueAnnotation)

            Expression urlExpression = new GStringExpression('http://localhost:${serverPort}${serverContextPath}', [new ConstantExpression('http://localhost:'), new ConstantExpression(""), new ConstantExpression("")], [new VariableExpression('serverPort'), new VariableExpression('serverContextPath')] as List<Expression>)

            Expression baseUrlVariableExpression = new VariableExpression('$baseUrl', ClassHelper.make(String))
            Expression declareBaseUrlExpression = new DeclarationExpression(baseUrlVariableExpression, Token.newSymbol(Types.EQUALS, 0, 0), new MethodCallExpression(urlExpression, 'toString', new ArgumentListExpression()))

            Expression constantSlashExpression = new ConstantExpression('/')
            Expression endsWithMethodCall = new MethodCallExpression(baseUrlVariableExpression, 'endsWith', new ArgumentListExpression(constantSlashExpression))
            Expression appendSlashExpression = new BinaryExpression(baseUrlVariableExpression, Token.newSymbol(Types.PLUS_EQUAL, 0, 0), constantSlashExpression)
            Statement ifUrlEndsWithSlashStatement = new IfStatement(new BooleanExpression(endsWithMethodCall), new EmptyStatement(), new ExpressionStatement(appendSlashExpression))
            def methodBody = new BlockStatement()
            methodBody.addStatement(new ExpressionStatement(declareBaseUrlExpression))
            methodBody.addStatement(ifUrlEndsWithSlashStatement)
            def systemClassExpression = new ClassExpression(ClassHelper.make(System))
            def args = new ArgumentListExpression()
            args.addExpression(new ConstantExpression("geb.build.baseUrl"))
            args.addExpression(baseUrlVariableExpression)
            methodBody.addStatement(new ExpressionStatement(new MethodCallExpression(systemClassExpression, "setProperty", args)))
            def method = new MethodNode("configureGebBaseUrl", Modifier.PUBLIC, ClassHelper.VOID_TYPE, [contextPathParameter, serverPortParameter] as Parameter[], null, methodBody)
            method.addAnnotation(new AnnotationNode(ClassHelper.make(Autowired)))
            classNode.addMethod(method)
        }
    }
}
