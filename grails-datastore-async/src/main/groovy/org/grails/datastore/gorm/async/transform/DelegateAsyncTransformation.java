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

package org.grails.datastore.gorm.async.transform;

import grails.async.Promise;
import grails.async.Promises;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import org.codehaus.groovy.GroovyBugError;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.tools.GenericsUtils;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport;
import org.grails.async.transform.internal.DelegateAsyncUtils;

import java.beans.Introspector;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.codehaus.groovy.ast.tools.GenericsUtils.correctToGenericsSpecRecurse;
import static org.codehaus.groovy.ast.tools.GenericsUtils.createGenericsSpec;

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class DelegateAsyncTransformation implements ASTTransformation {
    private static final ArgumentListExpression NO_ARGS = new ArgumentListExpression();
    private static final String VOID = "void";
    public static final ClassNode GROOVY_OBJECT_CLASS_NODE = new ClassNode(GroovyObjectSupport.class);
    public static final ClassNode OBJECT_CLASS_NODE = new ClassNode(Object.class);

    public void visit(ASTNode[] nodes, SourceUnit source) {
        if (nodes.length != 2 || !(nodes[0] instanceof AnnotationNode) || !(nodes[1] instanceof AnnotatedNode)) {
            throw new GroovyBugError("Internal error: expecting [AnnotationNode, AnnotatedNode] but got: " + Arrays.asList(nodes));
        }

        AnnotatedNode parent = (AnnotatedNode) nodes[1];
        AnnotationNode annotationNode = (AnnotationNode) nodes[0];

        if (parent instanceof ClassNode) {
            Expression value = annotationNode.getMember("value");
            if (value instanceof ClassExpression) {
                ClassNode targetApi = value.getType().getPlainNodeReference();
                ClassNode classNode = (ClassNode)parent;

                final String fieldName = '$' + Introspector.decapitalize(targetApi.getNameWithoutPackage());
                FieldNode fieldNode = classNode.getField(fieldName);
                if (fieldNode == null) {
                    fieldNode = new FieldNode(fieldName, Modifier.PRIVATE, targetApi, classNode, new ConstructorCallExpression(targetApi, NO_ARGS));
                    classNode.addField(fieldNode);
                }

                applyDelegateAsyncTransform(classNode, targetApi, fieldName);
            }
        }
        else if(parent instanceof FieldNode) {
            FieldNode fieldNode = (FieldNode)parent;
            ClassNode targetApi = fieldNode.getType().getPlainNodeReference();
            ClassNode classNode = fieldNode.getOwner();
            applyDelegateAsyncTransform(classNode, targetApi, fieldNode.getName());
        }
    }

    private void applyDelegateAsyncTransform(ClassNode classNode, ClassNode targetApi, String fieldName) {

        List<MethodNode> methods = targetApi.getAllDeclaredMethods();

        ClassNode promisesClass = ClassHelper.make(Promises.class).getPlainNodeReference();
        MethodNode createPromiseMethodTargetWithDecorators = promisesClass.getDeclaredMethod("createPromise", new Parameter[]{new Parameter(new ClassNode(Closure.class), "c"), new Parameter(new ClassNode(List.class), "c")});
        Map<String,ClassNode> genericsSpec = createGenericsSpec(classNode);
        for(MethodNode candidate : methods) {
            if (isCandidateMethod(candidate)) {
                MethodNode existingMethod = classNode.getMethod(candidate.getName(), candidate.getParameters());
                if (existingMethod == null) {
                    List<String> currentMethodGenPlaceholders = genericPlaceholderNames(candidate);
                    ClassNode promiseNode = ClassHelper.make(Promise.class).getPlainNodeReference();
                    ClassNode originalReturnType = correctToGenericsSpecRecurse(genericsSpec, candidate.getReturnType(), currentMethodGenPlaceholders);
                    if(!originalReturnType.getNameWithoutPackage().equals(VOID)) {
                        ClassNode returnType = originalReturnType;
                        if(ClassHelper.isPrimitiveType(originalReturnType.redirect())) {
                            returnType = ClassHelper.getWrapper(originalReturnType.redirect());
                        }
                        if(!OBJECT_CLASS_NODE.equals(returnType)) {
                            promiseNode.setGenericsTypes(new GenericsType[]{new GenericsType(returnType)});
                        }
                    }
                    final BlockStatement methodBody = new BlockStatement();
                    final BlockStatement promiseBody = new BlockStatement();


                    final ClosureExpression closureExpression = new ClosureExpression(new Parameter[0], promiseBody);
                    VariableScope variableScope = new VariableScope();
                    closureExpression.setVariableScope(variableScope);
                    VariableExpression thisObject = new VariableExpression("this");
                    ClassNode delegateAsyncUtilsClassNode = new ClassNode(DelegateAsyncUtils.class);

                    MethodNode getPromiseDecoratorsMethodNode = delegateAsyncUtilsClassNode.getDeclaredMethods("getPromiseDecorators").get(0);
                    ListExpression promiseDecorators = new ListExpression();
                    ArgumentListExpression getPromiseDecoratorsArguments = new ArgumentListExpression(thisObject, promiseDecorators);

                    MethodCallExpression getDecoratorsMethodCall = new MethodCallExpression(new ClassExpression(delegateAsyncUtilsClassNode), "getPromiseDecorators", getPromiseDecoratorsArguments);
                    getDecoratorsMethodCall.setMethodTarget(getPromiseDecoratorsMethodNode);

                    MethodCallExpression createPromiseWithDecorators = new MethodCallExpression(new ClassExpression(promisesClass), "createPromise",new ArgumentListExpression( closureExpression, getDecoratorsMethodCall));
                    if(createPromiseMethodTargetWithDecorators != null) {
                        createPromiseWithDecorators.setMethodTarget(createPromiseMethodTargetWithDecorators);
                    }
                    methodBody.addStatement(new ExpressionStatement(createPromiseWithDecorators));

                    final ArgumentListExpression arguments = new ArgumentListExpression();

                    Parameter[] parameters = copyParameters(genericsSpec, StaticTypeCheckingSupport.parameterizeArguments(classNode, candidate), currentMethodGenPlaceholders);
                    for(Parameter p : parameters) {
                        p.setClosureSharedVariable(true);
                        variableScope.putReferencedLocalVariable(p);
                        VariableExpression ve = new VariableExpression(p);
                        ve.setClosureSharedVariable(true);
                        arguments.addExpression(ve);
                    }
                    MethodCallExpression delegateMethodCall = new MethodCallExpression(new VariableExpression(fieldName), candidate.getName(), arguments);
                    promiseBody.addStatement(new ExpressionStatement(delegateMethodCall));
                    MethodNode newMethodNode = new MethodNode(candidate.getName(), Modifier.PUBLIC,promiseNode, parameters,null, methodBody);
                    classNode.addMethod(newMethodNode);
                }
            }
        }
    }


    private List<String> genericPlaceholderNames(MethodNode candidate) {
        GenericsType[] candidateGenericsTypes = candidate.getGenericsTypes();
        List<String> names = new ArrayList<String>();
        if (candidateGenericsTypes != null) {
            for (GenericsType gt : candidateGenericsTypes) {
                names.add(gt.getName());
            }
        }
        return names;
    }

    private static ClassNode alignReturnType(final ClassNode receiver, final ClassNode originalReturnType) {
        ClassNode copiedReturnType = originalReturnType.getPlainNodeReference();

        ClassNode actualReceiver = receiver;
        List<GenericsType> redirectTypes = new ArrayList<GenericsType>();
        if (actualReceiver.redirect().getGenericsTypes()!=null) {
            Collections.addAll(redirectTypes,actualReceiver.redirect().getGenericsTypes());
        }
        if (!redirectTypes.isEmpty()) {
            GenericsType[] redirectReceiverTypes = redirectTypes.toArray(new GenericsType[redirectTypes.size()]);

            GenericsType[] receiverParameterizedTypes = actualReceiver.getGenericsTypes();
            if (receiverParameterizedTypes==null) {
                receiverParameterizedTypes = redirectReceiverTypes;
            }

            if (originalReturnType.isUsingGenerics()) {
                GenericsType[] alignmentTypes = originalReturnType.getGenericsTypes();
                GenericsType[] genericsTypes = GenericsUtils.alignGenericTypes(redirectReceiverTypes, receiverParameterizedTypes, alignmentTypes);
                copiedReturnType.setGenericsTypes(genericsTypes);
            }
        }

        return copiedReturnType;
    }


    private static boolean isCandidateMethod(MethodNode declaredMethod) {
        ClassNode groovyMethods = GROOVY_OBJECT_CLASS_NODE;
        String methodName = declaredMethod.getName();
        return !declaredMethod.isSynthetic() &&
                !methodName.contains("$") &&
                Modifier.isPublic(declaredMethod.getModifiers()) &&
                !groovyMethods.hasMethod(declaredMethod.getName(), declaredMethod.getParameters());
    }

    private static Parameter[] copyParameters(Map<String, ClassNode> genericsSpec, Parameter[] parameterTypes, List<String> currentMethodGenPlaceholders) {
        Parameter[] newParameterTypes = new Parameter[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Parameter parameterType = parameterTypes[i];
            ClassNode newParamType = correctToGenericsSpecRecurse(genericsSpec, parameterType.getType(), currentMethodGenPlaceholders);
            Parameter newParameter = new Parameter(newParamType, parameterType.getName(), parameterType.getInitialExpression());
            newParameter.addAnnotations(parameterType.getAnnotations());
            newParameterTypes[i] = newParameter;
        }
        return newParameterTypes;
    }

}
