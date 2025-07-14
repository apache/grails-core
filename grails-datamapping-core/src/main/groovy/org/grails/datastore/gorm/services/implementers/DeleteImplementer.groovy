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
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.grails.datastore.gorm.transactions.transform.TransactionalTransform
import org.grails.datastore.mapping.reflect.AstUtils

import static org.codehaus.groovy.ast.tools.GeneralUtils.block
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.codehaus.groovy.ast.tools.GeneralUtils.declS
import static org.codehaus.groovy.ast.tools.GeneralUtils.ifS
import static org.codehaus.groovy.ast.tools.GeneralUtils.notNullX
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX

/**
 * Implements "void delete(..)"
 *
 * @author Graeme Rocher
 */
@CompileStatic
class DeleteImplementer extends AbstractDetachedCriteriaServiceImplementor implements SingleResultServiceImplementer<Number> {

    static final List<String> HANDLED_PREFIXES = ['delete', 'remove']

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        if (methodNode.parameters.length == 0) return false
        else {
            return AstUtils.isDomainClass(domainClass) && super.doesImplement(domainClass, methodNode)
        }
    }

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        return ClassHelper.VOID_TYPE.equals(returnType) || AstUtils.isSubclassOfOrImplementsInterface(returnType, Number.name)
    }

    @Override
    Iterable<String> getHandledPrefixes() {
        return HANDLED_PREFIXES
    }

    @Override
    protected void applyDefaultTransactionHandling(MethodNode newMethodNode) {
        newMethodNode.addAnnotation(new AnnotationNode(TransactionalTransform.MY_TYPE))
    }

    @Override
    void implementById(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode, BlockStatement body, Expression byIdLookup) {
        boolean isVoidReturnType = ClassHelper.VOID_TYPE.equals(newMethodNode.returnType)
        VariableExpression obj = varX('$obj')
        Statement deleteStatement = stmt(callX(obj, "delete"))
        if (!isVoidReturnType) {
            deleteStatement = block(
                    deleteStatement,
                    returnS(constX(1))
            )
        }

        body.addStatements([
                declS(obj, byIdLookup),
                ifS(
                        notNullX(obj),
                        deleteStatement
                )
        ])
        if (!isVoidReturnType) {
            body.addStatement(
                    returnS(constX(0))
            )
        }
    }

    @Override
    void implementWithQuery(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode, BlockStatement body, VariableExpression detachedCriteriaVar, Expression queryArgs) {

        MethodCallExpression deleteCall = callX(detachedCriteriaVar, "deleteAll")
        boolean isVoidReturnType = ClassHelper.VOID_TYPE.equals(newMethodNode.returnType)

        body.addStatements([
                // return query.deleteAll()
                isVoidReturnType ? stmt(deleteCall) : returnS(deleteCall)
        ])
    }
}
