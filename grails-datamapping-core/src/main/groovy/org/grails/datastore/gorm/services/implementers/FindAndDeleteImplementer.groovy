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
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.Statement

import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.transactions.transform.TransactionalTransform
import org.grails.datastore.mapping.reflect.AstUtils

import static org.codehaus.groovy.ast.tools.GeneralUtils.block
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.declS
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX

/**
 * An implementer that handles delete methods
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class FindAndDeleteImplementer extends FindOneImplementer implements SingleResultServiceImplementer<GormEntity> {

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        if(methodNode.parameters.length == 0) {
            return false
        }
        else {
            return super.doesImplement(domainClass, methodNode)
        }
    }

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        return AstUtils.isDomainClass(returnType)
    }

    @Override
    protected ClassNode resolveDomainClassFromSignature(ClassNode currentDomainClassNode, MethodNode methodNode) {
        return methodNode.returnType
    }

    @Override
    protected Statement buildReturnStatement(ClassNode targetDomainClass, MethodNode abstractMethodNode, Expression queryMethodCall, Expression args, MethodNode newMethodNode) {
        VariableExpression var = varX('$obj', targetDomainClass)
        MethodCallExpression deleteCall = args != null ? callX(var, "delete", args) : callX(var, "delete")

        deleteCall.setSafe(true) // null safe
        block(
            declS(var, queryMethodCall),
            stmt(deleteCall),
            returnS(var)
        )
    }

    @Override
    protected void applyDefaultTransactionHandling(MethodNode newMethodNode) {
        newMethodNode.addAnnotation(new AnnotationNode(TransactionalTransform.MY_TYPE))
    }

    @Override
    Iterable<String> getHandledPrefixes() {
        return DeleteImplementer.HANDLED_PREFIXES
    }
}
