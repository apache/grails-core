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

import grails.gorm.services.Where
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement

/**
 * Interface projection implementer for {@link grails.gorm.services.Where} queries
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class FindOneInterfaceProjectionWhereImplementer extends FindOneWhereImplementer implements SingleResultInterfaceProjectionBuilder, AnnotatedServiceImplementer<Where> {

    @Override
    protected ClassNode resolveDomainClassFromSignature(ClassNode currentDomainClassNode, MethodNode methodNode) {
        return currentDomainClassNode
    }

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode newMethodNode, ClassNode returnType, String prefix) {
        return isInterfaceProjection(domainClass, newMethodNode, returnType)
    }

    @Override
    protected Statement buildReturnStatement(ClassNode domainClass, MethodNode abstractMethodNode, MethodNode newMethodNode, Expression queryExpression) {
        ReturnStatement rs = (ReturnStatement)super.buildReturnStatement(domainClass, abstractMethodNode, newMethodNode, queryExpression)
        return buildInterfaceProjection(domainClass, abstractMethodNode, rs.expression, queryExpression, newMethodNode)
    }
}
