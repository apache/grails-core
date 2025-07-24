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

package org.grails.datastore.gorm.validation.jakarta.services.implementers

import java.lang.reflect.Method
import java.lang.reflect.Modifier

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ArrayExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.Statement

import jakarta.validation.Constraint
import jakarta.validation.ConstraintViolationException
import jakarta.validation.ParameterNameProvider

import org.grails.datastore.gorm.services.ServiceEnhancer
import org.grails.datastore.gorm.transform.AbstractTraitApplyingGormASTTransformation
import org.grails.datastore.gorm.validation.jakarta.ConfigurableParameterNameProvider
import org.grails.datastore.gorm.validation.jakarta.services.ValidatedService
import org.grails.datastore.mapping.reflect.ClassUtils

import static org.codehaus.groovy.ast.ClassHelper.CLASS_Type
import static org.codehaus.groovy.ast.ClassHelper.OBJECT_TYPE
import static org.codehaus.groovy.ast.ClassHelper.make
import static org.codehaus.groovy.ast.tools.GeneralUtils.args
import static org.codehaus.groovy.ast.tools.GeneralUtils.assignS
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.codehaus.groovy.ast.tools.GeneralUtils.ctorX
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX
import static org.grails.datastore.gorm.transform.AstMethodDispatchUtils.callThisD
import static org.grails.datastore.mapping.reflect.AstUtils.ZERO_ARGUMENTS
import static org.grails.datastore.mapping.reflect.AstUtils.ZERO_PARAMETERS
import static org.grails.datastore.mapping.reflect.AstUtils.findAnnotation
import static org.grails.datastore.mapping.reflect.AstUtils.varThis

/**
 * Adds method parameter validation to {@link grails.gorm.services.Service} instances
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class MethodValidationImplementer implements ServiceEnhancer {

    private static final String VALIDATED_METHOD = '$validatedMethod'

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        return false
    }

    @Override
    void implement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        // no-op
    }

    @Override
    boolean doesEnhance(ClassNode domainClass, MethodNode methodNode) {
        if(ClassUtils.isPresent("jakarta.validation.Validation")) {
            for(Parameter p in methodNode.parameters) {
                if( p.annotations.any() { AnnotationNode ann ->
                    def constraintAnn = findAnnotation(ann.classNode, Constraint)
                    constraintAnn != null
                } ) {
                    return true
                }

            }
        }
        return false
    }

    @Override
    void enhance(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        Statement body = (Statement)newMethodNode.code

        // add parameter name data for the service
        weaveParameterNameData(domainClassNode, newMethodNode, abstractMethodNode)

        // weave the ValidatedService trait
        AbstractTraitApplyingGormASTTransformation.weaveTraitWithGenerics(
                targetClassNode,
                ValidatedService,
                domainClassNode
        )

        Integer validatedMethodCount = (Integer)targetClassNode.getNodeMetaData(VALIDATED_METHOD)
        if(validatedMethodCount == null) {
            validatedMethodCount = 0
        }
        else{
            validatedMethodCount++
        }

        targetClassNode.putNodeMetaData(VALIDATED_METHOD, validatedMethodCount)

        // add a field that holds a reference to the java.lang.reflect.Method to be validated
        String methodFieldName = VALIDATED_METHOD + validatedMethodCount
        MethodCallExpression getClassCall = callThisD(targetClassNode, "getClass", ZERO_ARGUMENTS)
        List<Expression> validateArgsList = []
        List<Expression> parameterTypesList = []
        for(Parameter p in newMethodNode.parameters) {
            validateArgsList.add(varX(p))
            parameterTypesList.add(classX(p.type.plainNodeReference))
        }
        ArrayExpression parameterTypes = new ArrayExpression(CLASS_Type.plainNodeReference, parameterTypesList)
        MethodCallExpression getMethodCall = callX(getClassCall, "getMethod", args( constX(newMethodNode.name), parameterTypes))
        FieldNode methodField = targetClassNode.addField(methodFieldName, Modifier.PRIVATE, make(Method).plainNodeReference, getMethodCall)

        // add a first line to the method body that validates the method
        ArrayExpression argArray = new ArrayExpression(OBJECT_TYPE, validateArgsList)
        String validateMethodName = abstractMethodNode.exceptions?.contains( make(ConstraintViolationException) ) ? "jakartaValidate" : "validate"
        MethodCallExpression validateCall = callThisD(ValidatedService, validateMethodName, args(varThis(), varX(methodField),argArray))
        if(body instanceof BlockStatement) {
            ((BlockStatement)body).statements.add(0, stmt( validateCall ))
        }
        else {
            body = new BlockStatement([
               stmt( validateCall ),
               body
            ], newMethodNode.variableScope)
            newMethodNode.setCode(body)
        }

    }

    protected void weaveParameterNameData(ClassNode domainClassNode, MethodNode newMethodNode, MethodNode abstractMethodNode) {
        ClassNode newClass = newMethodNode.declaringClass
        ModuleNode module = abstractMethodNode.declaringClass.module
        String innerClassName = "${newClass.name}\$${ParameterNameProvider.simpleName}"
        InnerClassNode innerClassNode = (InnerClassNode) newClass.innerClasses.find() { InnerClassNode inner -> inner.name == innerClassName }

        MethodNode addParameterNamesMethodNode
        if (innerClassNode == null) {
            innerClassNode = new InnerClassNode(newClass, innerClassName, Modifier.STATIC | Modifier.PRIVATE, make(ConfigurableParameterNameProvider), [] as ClassNode[], null)

            innerClassNode.addAnnotation(new AnnotationNode(make(CompileStatic)))
            addParameterNamesMethodNode = innerClassNode.getMethods("addParameterNames")[0]

            module.addClass(innerClassNode)
            newClass.addObjectInitializerStatements(
                assignS(varX('parameterNameProvider'), ctorX(innerClassNode))
            )
        }
        else {
            addParameterNamesMethodNode = innerClassNode.getMethods("addParameterNames")[0]
        }


        ArgumentListExpression addParameterNamesArguments = args(constX(newMethodNode.name))
        ListExpression parameterNames = new ListExpression()
        List<Expression> parameterTypes = []
        for (Parameter p in newMethodNode.parameters) {
            parameterNames.addExpression(constX(p.name))
            parameterTypes.add(classX(p.type.plainNodeReference))
        }
        ArrayExpression parameterTypesArray = new ArrayExpression(CLASS_Type.plainNodeReference, parameterTypes)
        addParameterNamesArguments.addExpression(parameterTypesArray)
        addParameterNamesArguments.addExpression(parameterNames)


        def callExpression = callThisD(innerClassNode, addParameterNamesMethodNode.name, addParameterNamesArguments)
        callExpression.setMethodTarget(addParameterNamesMethodNode)
        ConstructorNode constructorNode = innerClassNode.getDeclaredConstructor(ZERO_PARAMETERS)
        if(constructorNode == null) {
            constructorNode = new ConstructorNode(Modifier.PUBLIC, ZERO_PARAMETERS, null, new BlockStatement())
            innerClassNode.addConstructor(constructorNode)
        }
        BlockStatement constructorBody = (BlockStatement)constructorNode.code
        constructorBody.addStatement(
            stmt(callExpression)
        )
    }
}
