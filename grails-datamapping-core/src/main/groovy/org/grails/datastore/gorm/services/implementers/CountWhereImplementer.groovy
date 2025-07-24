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
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.stmt.Statement

import grails.gorm.services.Where
import org.grails.datastore.mapping.reflect.AstUtils

import static org.codehaus.groovy.ast.tools.GeneralUtils.castX
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS

/**
 * Implements support for the {@link Where} annotation on {@link grails.gorm.services.Service} instances that return a multiple results
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class CountWhereImplementer extends AbstractWhereImplementer implements AnnotatedServiceImplementer<Where>, SingleResultServiceImplementer<Number> {

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        if( isAnnotated(domainClass, methodNode) ) {
            return isCompatibleReturnType(domainClass, methodNode, methodNode.returnType, methodNode.name)
        }
        return false
    }

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        return AstUtils.isNumberType(returnType)
    }

    @Override
    protected String getQueryMethodToExecute(ClassNode domainClass, MethodNode newMethodNode) {
        return "count"
    }

    @Override
    protected Statement buildReturnStatement(ClassNode domainClass, MethodNode abstractMethodNode, MethodNode newMethodNode, Expression queryExpression) {
        return returnS(castX(newMethodNode.returnType, queryExpression))
    }

}
