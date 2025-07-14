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
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.stmt.Statement
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.reflect.AstUtils

import static org.codehaus.groovy.ast.tools.GeneralUtils.args
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS

/**
 * Implements support for String-based queries that return a domain class
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class FindOneStringQueryImplementer extends AbstractStringQueryImplementer implements SingleResultServiceImplementer<GormEntity> {

    @Override
    protected Statement buildQueryReturnStatement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, Expression queryArg) {
        ClassNode returnType = (ClassNode) newMethodNode.getNodeMetaData(RETURN_TYPE) ?: abstractMethodNode.returnType
        String methodToExecute = getFindMethodToInvoke(domainClassNode, newMethodNode, returnType)

        if (methodToExecute != "find") {
            queryArg = args(queryArg, AstUtils.mapX(max: constX(1)))
        }

        Expression queryCall = callX(findStaticApiForConnectionId(domainClassNode, newMethodNode),
                methodToExecute,
                queryArg)

        if (!AstUtils.isDomainClass(returnType)) {
            queryCall = callX(queryCall, "first")
        }
        returnS(
                queryCall
        )
    }

    protected String getFindMethodToInvoke(ClassNode classNode, MethodNode methodNode, ClassNode returnType) {
        if (AstUtils.isDomainClass(returnType)) {
            return "find"
        } else {
            return "executeQuery"
        }
    }

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        if (AstUtils.isDomainClass(returnType)) {
            return true
        } else if (!AstUtils.isSubclassOfOrImplementsInterface(returnType, Iterable.name) && !returnType.isArray() && !returnType.packageName?.startsWith("rx.")) {
            def queryAnnotation = AstUtils.findAnnotation(methodNode, getAnnotationType())
            def query = queryAnnotation.getMember("value")
            if (query instanceof GStringExpression) {
                GStringExpression gstring = (GStringExpression) query
                List<ConstantExpression> strings = gstring.strings
                ConstantExpression stem = strings.first()
                if (stem.text.toLowerCase(Locale.ENGLISH).contains("select")) {
                    return returnType != ClassHelper.VOID_TYPE
                }
            }
        }
        return false
    }
}
