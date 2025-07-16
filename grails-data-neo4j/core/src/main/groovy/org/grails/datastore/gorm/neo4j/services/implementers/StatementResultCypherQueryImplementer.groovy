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

package org.grails.datastore.gorm.neo4j.services.implementers

import grails.neo4j.services.Cypher
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.stmt.Statement
import org.grails.datastore.gorm.services.implementers.AnnotatedServiceImplementer
import org.grails.datastore.mapping.reflect.AstUtils
import org.neo4j.driver.Result

import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.castX
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS

/**
 * An implementer that returns a statement result
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class StatementResultCypherQueryImplementer extends FindAllCypherQueryImplementer  implements AnnotatedServiceImplementer<Cypher> {

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        return AstUtils.implementsInterface(returnType, Result.name)
    }

    @Override
    protected Statement buildQueryReturnStatement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, Expression args) {
        ClassNode returnType = newMethodNode.returnType
        Expression methodCall = callX(domainClassNode, "cypherStatic", args)
        methodCall = castX(returnType.plainNodeReference, methodCall)
        return returnS(methodCall)
    }
}
