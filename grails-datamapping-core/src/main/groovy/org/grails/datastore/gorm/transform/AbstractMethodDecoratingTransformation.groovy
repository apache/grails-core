/*
 * Copyright 2017 the original author or authors.
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
package org.grails.datastore.gorm.transform

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.tools.GenericsUtils
import org.codehaus.groovy.classgen.VariableScopeVisitor
import org.codehaus.groovy.control.ErrorCollector
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.sc.StaticCompileTransformation
import org.codehaus.groovy.transform.trait.Traits
import org.grails.datastore.mapping.reflect.NameUtils

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.beans.Introspector
import java.lang.reflect.Modifier

import static org.codehaus.groovy.ast.ClassHelper.*
import static org.grails.datastore.gorm.transform.AstMethodDispatchUtils.*
import static org.grails.datastore.mapping.reflect.AstUtils.*

/**
 * An abstract implementation for transformations that decorate a method invocation such that
 * the method invocation is wrapped in the execution of a closure that delegates to the original logic.
 * Examples of such transformations are {@link grails.gorm.transactions.Transactional} and {@link grails.gorm.multitenancy.CurrentTenant}
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
abstract class AbstractMethodDecoratingTransformation extends AbstractGormASTTransformation {

    private static final Set<String> METHOD_NAME_EXCLUDES = new HashSet<String>(Arrays.asList("afterPropertiesSet", "destroy"))
    private static final Set<String> ANNOTATION_NAME_EXCLUDES = new HashSet<String>(Arrays.asList(PostConstruct.class.getName(), PreDestroy.class.getName(), "grails.web.controllers.ControllerMethod"))
    /**
     * Key used to store within the original method node metadata, all previous decorated methods
     */
    public static final String DECORATED_METHODS = '$DECORATED'

    @Override
    void visit(SourceUnit source, AnnotationNode annotationNode, AnnotatedNode annotatedNode) {

        if(annotatedNode instanceof MethodNode) {
            MethodNode methodNode = (MethodNode)annotatedNode
            Map<String, ClassNode> genericsSpec = GenericsUtils.createGenericsSpec(methodNode.declaringClass)
            weaveNewMethod(source, annotationNode, methodNode.getDeclaringClass(), methodNode, genericsSpec)
        }
        else if(annotatedNode instanceof ClassNode) {
            ClassNode classNode = (ClassNode) annotatedNode
            if(!classNode.isInterface()) {
                weaveClassNode( source, annotationNode, classNode)
            }
        }
    }


    protected void weaveClassNode(SourceUnit source, AnnotationNode annotationNode, ClassNode classNode) {
        enhanceClassNode(source, annotationNode, classNode)
        Map<String, ClassNode> genericsSpec = GenericsUtils.createGenericsSpec(classNode)
        List<MethodNode> methods = new ArrayList<MethodNode>(classNode.getMethods())

        List<String> setterMethodNames = []
        Iterator<MethodNode> methodNodeIterator = methods.iterator()
        while (methodNodeIterator.hasNext()) {
            MethodNode md = methodNodeIterator.next()
            if (isSetter(md)) {
                setterMethodNames.add(md.name)
                methodNodeIterator.remove()
            }
        }

        for (MethodNode md in methods) {
            String methodName = md.name
            int modifiers = md.modifiers
            if (!md.isSynthetic() && Modifier.isPublic(modifiers) && !Modifier.isAbstract(modifiers) &&
                    !Modifier.isStatic(modifiers) && !hasJunitAnnotation(md)) {
                if (hasExcludedAnnotation(md)) continue

                def startsWithSpock = methodName.startsWith('$spock')
                if (methodName.contains('$') && !startsWithSpock) continue

                if (startsWithSpock && methodName.endsWith('proc')) continue

                if (md.getAnnotations().any { AnnotationNode an -> an.classNode.name == "org.spockframework.runtime.model.DataProviderMetadata" }) {
                    continue
                }

                if (METHOD_NAME_EXCLUDES.contains(methodName)) continue

                if (isGetter(md)) {
                    final String propertyName = NameUtils.getPropertyNameForGetterOrSetter(md.name)
                    final String setterName = NameUtils.getSetterName(propertyName)

                    //If a setter exists for the getter, don't apply the transformation
                    if (setterMethodNames.contains(setterName)) continue
                }

                // don't apply to methods added by traits
                if (hasAnnotation(md, Traits.TraitBridge.class)) continue
                // ignore methods that delegate to each other
                if (hasAnnotation(md, "grails.compiler.DelegatingMethod")) continue

                weaveNewMethod(source, annotationNode, classNode, md, genericsSpec)
            } else if (isTestSetupOrCleanup(classNode, md)) {
                weaveTestSetupMethod(source, annotationNode, classNode, md, genericsSpec)
            }
        }
    }

    /**
     * Add any additional logic to enhance the class node
     *
     * @param sourceUnit The source unit
     * @param annotationNode The annotation node
     * @param classNode The class node
     */
    protected void enhanceClassNode(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode) {
        // no-op
    }

    protected boolean isTestSetupOrCleanup(ClassNode classNode, MethodNode md) {
        String methodName = md.getName()
        return (("setup".equals(methodName) || "cleanup".equals(methodName)) && isSpockTest(classNode)) ||
                hasJunitAnnotation(md)
    }

    protected abstract String getRenamedMethodPrefix()

    protected void weaveTestSetupMethod(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode, MethodNode methodNode, Map<String, ClassNode> genericsSpec) {
        // no-op
    }

    /**
     * Weaves a new method
     *
     * @param sourceUnit The source unit
     * @param annotationNode The annotation node
     * @param classNode The class node
     * @param methodNode The original method that will delete to the new method
     * @return The new method's body
     */
    protected MethodNode weaveNewMethod(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode, MethodNode methodNode, Map<String, ClassNode> genericsSpec) {
        Object appliedMarker = getAppliedMarker()
        if ( methodNode.getNodeMetaData(appliedMarker) == appliedMarker ) {
            return methodNode
        }
        if( methodNode.isAbstract() ) {
            return methodNode
        }

        methodNode.putNodeMetaData(appliedMarker, appliedMarker)

        enhanceClassNode(sourceUnit, annotationNode, classNode)


        // Move the existing logic into a new method called "$tt_methodName()"
        String renamedMethodName
        boolean superMethod = findAnnotation(methodNode, Override) || classNode.getSuperClass()?.getMethod(methodNode.name, methodNode.parameters) != null
        if(superMethod) {
            renamedMethodName = getRenamedMethodPrefix() + Introspector.decapitalize(classNode.nameWithoutPackage) + '_' + methodNode.getName()
        }
        else {
            renamedMethodName = getRenamedMethodPrefix() + methodNode.getName()
        }

        Parameter[] newParameters = prepareNewMethodParameters(methodNode, GenericsUtils.addMethodGenerics(methodNode, genericsSpec), classNode)
        MethodNode renamedMethod = moveOriginalCodeToNewMethod(methodNode, renamedMethodName, newParameters, classNode, sourceUnit, genericsSpec)
        MethodCallExpression originalMethodCall = buildCallToOriginalMethod(classNode, renamedMethod)

        // Start constructing new method body
        BlockStatement methodBody = block()


        Expression executeMethodCallExpression = buildDelegatingMethodCall(
                sourceUnit,
                annotationNode,
                classNode,
                methodNode,
                originalMethodCall,
                methodBody
        )

        if(methodNode.getReturnType() != VOID_TYPE) {
            methodBody.addStatement(
                    returnS(
                            castX(methodNode.getReturnType(), executeMethodCallExpression)
                    )
            )
        } else {
            methodBody.addStatement(
                    stmt(executeMethodCallExpression)
            )
        }

        methodNode.setCode(methodBody)
        processVariableScopes(sourceUnit, classNode, methodNode)
        if(!isSpockTest(classNode)) {
            compileMethodStatically(sourceUnit, methodNode)
        }
        return renamedMethod
    }

    protected void compileMethodStatically(SourceUnit sourceUnit, MethodNode methodNode) {
        if (compilationUnit != null) {
            addAnnotationIfNecessary(methodNode, CompileStatic)
            def staticCompileTransformation = new StaticCompileTransformation(compilationUnit: compilationUnit)
            staticCompileTransformation.visit([new AnnotationNode(COMPILE_STATIC_TYPE), methodNode] as ASTNode[], sourceUnit)
        }
    }

    protected Parameter[] prepareNewMethodParameters(MethodNode methodNode, Map<String, ClassNode> genericsSpec, ClassNode classNode = null) {
        return copyParameters(methodNode.getParameters(), genericsSpec)
    }

    /**
     * Builds the delegating method call for the given class node
     *
     * @param sourceUnit The source unit
     * @param annotationNode The annotation node
     * @param classNode The class node
     * @param methodNode The original method node
     * @param originalMethodCallExpr The method call expression that invokes the original logic
     * @param newMethodBody The new method body
     *
     * @return The expression that will make up the body of the new method
     */
    protected abstract Expression buildDelegatingMethodCall(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode, MethodNode methodNode, MethodCallExpression originalMethodCallExpr, BlockStatement newMethodBody)

    /**
     * Construct a method call that wraps an original call with a closure invocation
     *
     * @param targetObject The target object
     * @param executeMethodName The method that accepts a closure
     * @param closureParameters The parameters for the closure
     * @param originalMethodCall The original method call to delegate to
     * @return The MethodCallExpression
     */
    protected MethodCallExpression makeDelegatingClosureCall(Expression targetObject, String executeMethodName,  Parameter[] closureParameters, MethodCallExpression originalMethodCall, VariableScope variableScope ) {
        return makeDelegatingClosureCall(targetObject, executeMethodName, new ArgumentListExpression(), closureParameters, originalMethodCall, variableScope)
    }

    /**
     * Construct a method call that wraps an original call with a closure invocation
     *
     * @param targetObject The target object
     * @param executeMethodName The method that accepts a closure
     * @param closureParameters The parameters for the closure
     * @param originalMethodCall The original method call to delegate to
     * @return The MethodCallExpression
     */
    protected MethodCallExpression makeDelegatingClosureCall(Expression targetObject, String executeMethodName, ArgumentListExpression arguments, Parameter[] closureParameters, MethodCallExpression originalMethodCall, VariableScope variableScope ) {
        final ClosureExpression closureExpression = closureX(closureParameters, createDelegingMethodBody(closureParameters, originalMethodCall))
        closureExpression.setVariableScope(
                variableScope
        )
        arguments.addExpression(closureExpression)
        final MethodCallExpression executeMethodCallExpression = callX(
                targetObject,
                executeMethodName,
                arguments)

        final MethodNode executeMethodNode = targetObject.getType().getMethod(executeMethodName, paramsForArgs(arguments))
        if (executeMethodNode != null) {
            executeMethodCallExpression.setMethodTarget(executeMethodNode)
        }
        return executeMethodCallExpression
    }

    protected Statement createDelegingMethodBody(Parameter[] parameters, MethodCallExpression originalMethodCall) {
        return stmt(originalMethodCall)
    }


    protected MethodNode moveOriginalCodeToNewMethod(MethodNode methodNode, String renamedMethodName, Parameter[] newParameters, ClassNode classNode, SourceUnit source, Map<String, ClassNode> genericsSpec) {
        Statement body = methodNode.code

        MethodNode renamedMethodNode = new MethodNode(
                renamedMethodName,
                Modifier.PROTECTED, resolveReturnTypeForNewMethod(methodNode),
                newParameters,
                EMPTY_CLASS_ARRAY,
                body
        )

        List<MethodNode> decoratedMethods = (List<MethodNode>)methodNode.getNodeMetaData(DECORATED_METHODS)
        if(decoratedMethods == null) {
            decoratedMethods = []
            methodNode.putNodeMetaData(DECORATED_METHODS, decoratedMethods)
        }
        decoratedMethods.add(renamedMethodNode)


        def newVariableScope = new VariableScope()
        for (p in newParameters) {
            newVariableScope.putDeclaredVariable(p)
        }

        renamedMethodNode.setVariableScope(
                newVariableScope
        )

        // GrailsCompileStatic and GrailsTypeChecked are not explicitly addressed
        // here but they will be picked up because they are @AnnotationCollector annotations
        // which use CompileStatic and TypeChecked...
        renamedMethodNode.addAnnotations(methodNode.getAnnotations(COMPILE_STATIC_TYPE))
        renamedMethodNode.addAnnotations(methodNode.getAnnotations(TYPE_CHECKED_TYPE))

        methodNode.setCode(null)
        classNode.addMethod(renamedMethodNode)

        // Use a dummy source unit to process the variable scopes to avoid the issue where this is run twice producing an error
        VariableScopeVisitor scopeVisitor = new VariableScopeVisitor(new SourceUnit("dummy", "dummy", source.getConfiguration(), source.getClassLoader(), new ErrorCollector(source.getConfiguration())))
        if (methodNode == null) {
            scopeVisitor.visitClass(classNode)
        } else {
            scopeVisitor.prepareVisit(classNode)
            scopeVisitor.visitMethod(renamedMethodNode)
        }

        return renamedMethodNode
    }

    protected MethodCallExpression buildCallToOriginalMethod(ClassNode classNode, MethodNode renamedMethodNode) {
        final MethodCallExpression originalMethodCall = callX(varX("this", classNode), renamedMethodNode.name, args(renamedMethodNode.parameters))
        originalMethodCall.setImplicitThis(false)
        originalMethodCall.setMethodTarget(renamedMethodNode)

        return originalMethodCall
    }

    protected ClassNode resolveReturnTypeForNewMethod(MethodNode methodNode) {
        methodNode.getReturnType().getPlainNodeReference()
    }

    protected boolean hasExcludedAnnotation(MethodNode md) {
        def excludes = ANNOTATION_NAME_EXCLUDES
        return hasExcludedAnnotation(md, excludes)
    }

    protected boolean hasExcludedAnnotation(MethodNode md, Set<String> excludes) {
        boolean excludedAnnotation = false
        for (AnnotationNode annotation : md.getAnnotations()) {
            AnnotationNode gormTransform = findAnnotation( annotation.classNode, GormASTTransformationClass)
            if (gormTransform != null || excludes.contains(annotation.getClassNode().getName())) {
                excludedAnnotation = true
                break
            }
        }
        return excludedAnnotation
    }

}
