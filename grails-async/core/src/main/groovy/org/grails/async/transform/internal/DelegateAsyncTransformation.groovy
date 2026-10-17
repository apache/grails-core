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
package org.grails.async.transform.internal

import java.beans.Introspector
import java.lang.reflect.Modifier

import groovy.transform.CompileStatic
import org.codehaus.groovy.GroovyBugError
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.VariableScope
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.tools.GenericsUtils
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.TransformWithPriority
import org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport

import grails.async.Promise
import grails.async.Promises
import org.apache.grails.common.compiler.GroovyTransformOrder

/**
 * Implementation of {@link grails.async.DelegateAsync} transformation
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@GroovyASTTransformation
@CompileStatic
class DelegateAsyncTransformation implements ASTTransformation, TransformWithPriority {

    private static final ArgumentListExpression NO_ARGS = new ArgumentListExpression()
    private static final String VOID = 'void'
    public static final ClassNode GROOVY_OBJECT_CLASS_NODE = new ClassNode(GroovyObjectSupport)
    public static final ClassNode OBJECT_CLASS_NODE = new ClassNode(Object)

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        if (nodes.length != 2 || !(nodes[0] instanceof AnnotationNode) || !(nodes[1] instanceof AnnotatedNode)) {
            throw new GroovyBugError('Internal error: expecting [AnnotationNode, AnnotatedNode] but got: ' + Arrays.asList(nodes))
        }

        AnnotatedNode parent = (AnnotatedNode) nodes[1]
        AnnotationNode annotationNode = (AnnotationNode) nodes[0]

        if (parent instanceof ClassNode) {
            Expression value = annotationNode.getMember('value')
            if (value instanceof ClassExpression) {
                ClassNode targetApi = value.getType().getPlainNodeReference()
                ClassNode classNode = (ClassNode) parent

                final String fieldName = '$' + Introspector.decapitalize(targetApi.getNameWithoutPackage())
                FieldNode fieldNode = classNode.getField(fieldName)
                if (fieldNode == null) {
                    fieldNode = new FieldNode(fieldName, Modifier.PRIVATE, targetApi, classNode, new ConstructorCallExpression(targetApi, NO_ARGS))
                    classNode.addField(fieldNode)
                }

                applyDelegateAsyncTransform(classNode, targetApi, fieldName)
            }
        }
        else if (parent instanceof FieldNode) {
            FieldNode fieldNode = (FieldNode) parent
            ClassNode targetApi = fieldNode.getType().getPlainNodeReference()
            ClassNode classNode = fieldNode.getOwner()
            applyDelegateAsyncTransform(classNode, targetApi, fieldNode.getName())
        }
    }

    private void applyDelegateAsyncTransform(ClassNode classNode, ClassNode targetApi, String fieldName) {

        List<MethodNode> methods = targetApi.getAllDeclaredMethods()

        ClassNode promisesClass = ClassHelper.make(Promises).getPlainNodeReference()
        MethodNode createPromiseMethodTargetWithDecorators = promisesClass.getDeclaredMethod('createPromise', [new Parameter(new ClassNode(Closure), 'c'), new Parameter(new ClassNode(List), 'c')] as Parameter[])

        DelegateAsyncTransactionalMethodTransformer delegateAsyncTransactionalMethodTransformer = lookupAsyncTransactionalMethodTransformer()
        for (MethodNode m in methods) {
            if (isCandidateMethod(m)) {
                MethodNode existingMethod = classNode.getMethod(m.getName(), m.getParameters())
                if (existingMethod == null) {
                    ClassNode promiseNode = ClassHelper.make(Promise).getPlainNodeReference()
                    ClassNode originalReturnType = m.getReturnType()
                    if (!VOID.equals(originalReturnType.getNameWithoutPackage())) {
                        ClassNode returnType
                        if (ClassHelper.isPrimitiveType(originalReturnType.redirect())) {
                            returnType = ClassHelper.getWrapper(originalReturnType.redirect())
                        } else {
                            returnType = alignReturnType(classNode, originalReturnType)
                        }
                        if (!OBJECT_CLASS_NODE.equals(returnType)) {
                            promiseNode.setGenericsTypes([new GenericsType(returnType)] as GenericsType[])
                        }
                    }
                    final BlockStatement methodBody = new BlockStatement()
                    final BlockStatement promiseBody = new BlockStatement()

                    final ClosureExpression closureExpression = new ClosureExpression(new Parameter[0], promiseBody)
                    VariableScope variableScope = new VariableScope()
                    closureExpression.setVariableScope(variableScope)
                    VariableExpression thisObject = new VariableExpression('this')
                    ClassNode delegateAsyncUtilsClassNode = new ClassNode(DelegateAsyncUtils)

                    MethodNode getPromiseDecoratorsMethodNode = delegateAsyncUtilsClassNode.getDeclaredMethods('getPromiseDecorators').get(0)
                    ListExpression promiseDecorators = new ListExpression()
                    ArgumentListExpression getPromiseDecoratorsArguments = new ArgumentListExpression(thisObject, promiseDecorators)
                    delegateAsyncTransactionalMethodTransformer.transformTransactionalMethod(classNode, targetApi, m, promiseDecorators)

                    MethodCallExpression getDecoratorsMethodCall = new MethodCallExpression(new ClassExpression(delegateAsyncUtilsClassNode), 'getPromiseDecorators', getPromiseDecoratorsArguments)
                    getDecoratorsMethodCall.setMethodTarget(getPromiseDecoratorsMethodNode)

                    MethodCallExpression createPromiseWithDecorators = new MethodCallExpression(new ClassExpression(promisesClass), 'createPromise', new ArgumentListExpression(closureExpression, getDecoratorsMethodCall))
                    if (createPromiseMethodTargetWithDecorators != null) {
                        createPromiseWithDecorators.setMethodTarget(createPromiseMethodTargetWithDecorators)
                    }
                    methodBody.addStatement(new ExpressionStatement(createPromiseWithDecorators))

                    final ArgumentListExpression arguments = new ArgumentListExpression()

                    Parameter[] parameters = copyParameters(StaticTypeCheckingSupport.parameterizeArguments(classNode, m))
                    for (Parameter p in parameters) {
                        p.setClosureSharedVariable(true)
                        variableScope.putReferencedLocalVariable(p)
                        VariableExpression ve = new VariableExpression(p)
                        ve.setClosureSharedVariable(true)
                        arguments.addExpression(ve)
                    }
                    MethodCallExpression delegateMethodCall = new MethodCallExpression(new VariableExpression(fieldName), m.getName(), arguments)
                    promiseBody.addStatement(new ExpressionStatement(delegateMethodCall))
                    MethodNode newMethodNode = new MethodNode(m.getName(), Modifier.PUBLIC, promiseNode, parameters, ClassNode.EMPTY_ARRAY, methodBody)
                    classNode.addMethod(newMethodNode)
                }
            }
        }
    }

    private static ClassNode alignReturnType(final ClassNode receiver, final ClassNode originalReturnType) {
        if (!originalReturnType.isUsingGenerics()) {
            return originalReturnType.getPlainNodeReference()
        }
        Map<String, ClassNode> genericsSpec = GenericsUtils.createGenericsSpec(receiver)
        ClassNode correctedReturnType = GenericsUtils.correctToGenericsSpecRecurse(genericsSpec, originalReturnType)
        return correctedReturnType == null ?
            originalReturnType.getPlainNodeReference() :
            correctedReturnType
    }

    protected DelegateAsyncTransactionalMethodTransformer lookupAsyncTransactionalMethodTransformer() {
        try {
            Class<?> transformerClass = getClass().getClassLoader().loadClass('org.grails.async.transform.internal.DefaultDelegateAsyncTransactionalMethodTransformer')
            return (DelegateAsyncTransactionalMethodTransformer) transformerClass.getDeclaredConstructor().newInstance()
        } catch (Exception ignored) {
            // ignore
        }
        return new NoopDelegateAsyncTransactionalMethodTransformer()
    }

    private static boolean isCandidateMethod(MethodNode declaredMethod) {
        String methodName = declaredMethod.getName()
        return !declaredMethod.isSynthetic() &&
            !methodName.contains('\$') &&
            Modifier.isPublic(declaredMethod.getModifiers()) &&
            !GROOVY_OBJECT_CLASS_NODE.hasMethod(declaredMethod.getName(), declaredMethod.getParameters())
    }

    private static Parameter[] copyParameters(Parameter... parameterTypes) {
        Parameter[] newParameterTypes = new Parameter[parameterTypes.length]
        for (int i = 0; i < parameterTypes.length; i++) {
            Parameter parameterType = parameterTypes[i]
            ClassNode parameterTypeCN = parameterType.getType()
            ClassNode newParameterTypeCN = parameterTypeCN.getPlainNodeReference()
            if (parameterTypeCN.isUsingGenerics() && !parameterTypeCN.isGenericsPlaceHolder()) {
                newParameterTypeCN.setGenericsTypes(parameterTypeCN.getGenericsTypes())
            }
            Parameter newParameter = new Parameter(newParameterTypeCN, parameterType.getName(), parameterType.getInitialExpression())
            newParameter.addAnnotations(parameterType.getAnnotations())
            newParameterTypes[i] = newParameter
        }
        return newParameterTypes
    }

    @Override
    int priority() {
        return GroovyTransformOrder.DELEGATE_ASYNC_ORDER
    }

    private static class NoopDelegateAsyncTransactionalMethodTransformer implements DelegateAsyncTransactionalMethodTransformer {
        @Override
        void transformTransactionalMethod(ClassNode classNode, ClassNode delegateClassNode, MethodNode methodNode, ListExpression promiseDecoratorLookupArguments) {
            // noop
        }
    }

}
