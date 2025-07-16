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

import grails.neo4j.Path
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.grails.datastore.gorm.services.implementers.AbstractReadOperationImplementer
import org.grails.datastore.gorm.services.implementers.FindOneByImplementer
import org.grails.datastore.gorm.services.implementers.SingleResultServiceImplementer
import org.grails.datastore.mapping.reflect.AstUtils

import static org.codehaus.groovy.ast.tools.GeneralUtils.*

/**
 * Service implementer for findShortestPath
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class FindShortestPathImplementer extends AbstractReadOperationImplementer implements SingleResultServiceImplementer<Path> {
    @Override
    int getOrder() {
        return FindOneByImplementer.POSITION - 100
    }

    @Override
    void doImplement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        ClassNode returnType = newMethodNode.returnType

        Expression methodCall = callX(domainClassNode, "findShortestPath", args(newMethodNode.parameters))
        methodCall = castX(returnType.plainNodeReference, methodCall)
        BlockStatement bs = (BlockStatement)newMethodNode.code
        bs.addStatement(
            returnS(methodCall)
        )
    }

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        def alreadyImplemented = methodNode.getNodeMetaData(IMPLEMENTED)
        Parameter[] parameters = methodNode.parameters
        int paramCount = parameters.length
        if(!alreadyImplemented && AstUtils.implementsInterface(methodNode.returnType, Path.name) && paramCount > 1 && paramCount < 4) {
            if(AstUtils.isDomainClass(parameters[0].type) && AstUtils.isDomainClass(parameters[1].type)) {
                if(paramCount == 3) {
                    if(AstUtils.implementsInterface(parameters[2].type, "java.lang.Number")) {
                        return true
                    }
                }
                else {
                    return true
                }
            }
        }
        return false
    }

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        return AstUtils.implementsInterface(returnType, Path.name)
    }

    @Override
    Iterable<String> getHandledPrefixes() {
        return [] // all
    }
}
