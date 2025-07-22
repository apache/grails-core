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

package org.grails.datastore.gorm.services.implementers

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.transform.DelegateASTTransformation
import org.grails.datastore.gorm.services.ServiceImplementer
import org.grails.datastore.gorm.transform.AstPropertyResolveUtils
import org.grails.datastore.mapping.reflect.AstUtils
import org.grails.datastore.mapping.reflect.NameUtils

import java.lang.reflect.Modifier

import static org.codehaus.groovy.ast.tools.GeneralUtils.*

/**
 * Support trait for building interface projections
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
trait SingleResultInterfaceProjectionBuilder extends InterfaceProjectionBuilder {

    Statement buildInterfaceProjection(ClassNode targetDomainClass, MethodNode abstractMethodNode, Expression queryMethodCall, Expression args, MethodNode newMethodNode) {
        ClassNode declaringClass = newMethodNode.declaringClass
        ClassNode interfaceNode = (ClassNode)newMethodNode.getNodeMetaData(ServiceImplementer.RETURN_TYPE) ?: abstractMethodNode.returnType
        MethodNode methodTarget = buildInterfaceImpl(interfaceNode, declaringClass, targetDomainClass, abstractMethodNode)
        ClassNode innerClassNode = methodTarget.getDeclaringClass()

        VariableExpression delegateVar = varX('$delegate', innerClassNode)
        VariableExpression targetVar = varX('$target', targetDomainClass)
        MethodCallExpression setTargetCall = callX(delegateVar, '$setTarget', targetVar )
        setTargetCall.setMethodTarget(methodTarget)
        block(
            declS(delegateVar, ctorX(innerClassNode)),
            declS(targetVar, queryMethodCall),
            ifS(notNullX(targetVar), block(
                stmt(setTargetCall),
                returnS(delegateVar)
            ))
        )
    }


}
