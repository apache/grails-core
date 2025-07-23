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

package org.grails.events.transform

import grails.events.annotation.Subscriber
import grails.events.annotation.gorm.Listener
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import org.apache.grails.common.compiler.GroovyTransformOrder
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.tools.GenericsUtils
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.trait.Traits
import org.grails.datastore.gorm.transform.AbstractTraitApplyingGormASTTransformation
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.reflect.AstUtils
import org.grails.events.gorm.GormAnnotatedListener
import org.grails.events.gorm.GormAnnotatedSubscriber

import java.lang.reflect.Method
import java.lang.reflect.Modifier

import static org.codehaus.groovy.ast.tools.GeneralUtils.args
import static org.codehaus.groovy.ast.tools.GeneralUtils.callThisX
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.grails.datastore.mapping.reflect.AstUtils.ZERO_PARAMETERS

/**
 * An AST transformation that adds the {@link AnnotatedSubscriber}
 */
@AutoFinal
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class SubscriberTransform extends AbstractTraitApplyingGormASTTransformation {

    public static final Object APPLIED_MARKER = new Object()


    @Override
    protected Class getTraitClass() {
        return AnnotatedSubscriber
    }

    @Override
    protected ClassNode getAnnotationType() {
        return ClassHelper.make(Subscriber)
    }

    @Override
    protected boolean isValidAnnotation(AnnotationNode annotationNode, AnnotatedNode classNode) {
        return super.isValidAnnotation(annotationNode, classNode) || annotationNode.classNode.name == Listener.name
    }

    @Override
    void visit(SourceUnit source, AnnotationNode annotationNode, AnnotatedNode annotatedNode) {
        if(annotatedNode instanceof MethodNode && !Modifier.isAbstract(((MethodNode)annotatedNode).getModifiers())) {
            MethodNode methodNode = (MethodNode)annotatedNode
            ClassNode declaringClass = methodNode.getDeclaringClass()
            if ( shouldWeave(annotationNode, declaringClass) ) {
                if(declaringClass.getField("lazyInit") == null) {
                    declaringClass.addField("lazyInit", Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL, ClassHelper.Boolean_TYPE, ConstantExpression.FALSE)
                }
                Parameter[] parameters = methodNode.parameters
                boolean isGormEvent = parameters.length == 1 && AstUtils.isSubclassOf(parameters[0].type, AbstractPersistenceEvent.name)
                boolean isGormListener = annotationNode.classNode.name == Listener.name
                if(!isGormEvent && isGormListener) {
                    addError("A GORM @Listener must accept a GORM event as an argument", annotationNode)
                    return
                }
                if(isGormEvent) {
                    ClassNode eventType = parameters[0].type
                    if(isGormListener) {
                        weaveTrait(declaringClass, source, GormAnnotatedListener)
                    }
                    else {
                        weaveTrait(declaringClass, source, GormAnnotatedSubscriber)
                    }
                    MethodNode getSubscribersMethod = declaringClass.getDeclaredMethod("getSubscribedEvents")
                    ListExpression listExpression
                    if(getSubscribersMethod.getAnnotations(ClassHelper.make(Traits.TraitBridge))) {
                        def currentCode = getSubscribersMethod.code
                        if(currentCode instanceof ExpressionStatement) {
                            ExpressionStatement body = (ExpressionStatement) currentCode

                            def expression = body.getExpression()
                            if(expression instanceof ListExpression) {
                                listExpression  = (ListExpression) expression
                            }
                            else {
                                listExpression = new ListExpression()
                                body.setExpression(listExpression)
                            }
                        }
                        else {
                            listExpression = new ListExpression()
                            ExpressionStatement body = new ExpressionStatement(listExpression)
                            getSubscribersMethod.setCode(body)
                        }
                    }
                    else {
                        ExpressionStatement body = (ExpressionStatement) getSubscribersMethod.getCode()
                        listExpression  = (ListExpression) body.getExpression()
                    }
                    listExpression.addExpression(classX(eventType))

                }
                else {
                    weaveTrait(declaringClass, source, traitClass)
                }
            }

            MethodNode getSubscribersMethod = declaringClass.getDeclaredMethod("getSubscribedMethods")
            ListExpression listExpression
            if(getSubscribersMethod == null) {
                def listOfMethodType = GenericsUtils.makeClassSafeWithGenerics(List, ClassHelper.make(Method))
                listExpression = new ListExpression()
                ExpressionStatement body = new ExpressionStatement(listExpression)
                declaringClass.addMethod("getSubscribedMethods", Modifier.PUBLIC, listOfMethodType, ZERO_PARAMETERS, null, body)
            }
            else if(getSubscribersMethod.getAnnotations(ClassHelper.make(Traits.TraitBridge))) {
                def currentCode = getSubscribersMethod.code
                if(currentCode instanceof ExpressionStatement) {
                    ExpressionStatement body = (ExpressionStatement) currentCode

                    def expression = body.getExpression()
                    if(expression instanceof ListExpression) {
                        listExpression  = (ListExpression) expression
                    }
                    else {
                        listExpression = new ListExpression()
                        body.setExpression(listExpression)
                    }
                }
                else {
                    listExpression = new ListExpression()
                    ExpressionStatement body = new ExpressionStatement(listExpression)
                    getSubscribersMethod.setCode(body)
                }
            }
            else {
                ExpressionStatement body = (ExpressionStatement) getSubscribersMethod.getCode()
                listExpression  = (ListExpression) body.getExpression()
            }
            ArgumentListExpression methodArgs = args(
                    constX(methodNode.getName())
            )
            for(param in methodNode.parameters) {
                methodArgs.addExpression( classX(param.type) )
            }
            listExpression.addExpression(callX(
                    callThisX("getClass"), "getMethod", methodArgs)
            )
        }
    }

    @Override
    protected Object getAppliedMarker() {
        return APPLIED_MARKER
    }

    @Override
    int priority() {
        GroovyTransformOrder.SUBSCRIBER_ORDER
    }
}
