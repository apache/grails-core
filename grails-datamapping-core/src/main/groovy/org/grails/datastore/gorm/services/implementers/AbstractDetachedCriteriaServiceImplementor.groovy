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

import grails.gorm.DetachedCriteria
import grails.gorm.services.Join
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.reflect.AstUtils

import static org.codehaus.groovy.ast.tools.GeneralUtils.args
import static org.codehaus.groovy.ast.tools.GeneralUtils.assignS
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.codehaus.groovy.ast.tools.GeneralUtils.ctorX
import static org.codehaus.groovy.ast.tools.GeneralUtils.declS
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX

/**
 * An abstract implementer that builds a detached criteria query from the method arguments
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
abstract class AbstractDetachedCriteriaServiceImplementor extends AbstractReadOperationImplementer {

    public static final ClassNode DETACHED_CRITERIA = ClassHelper.make(DetachedCriteria).plainNodeReference

    @Override
    void doImplement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        BlockStatement body = (BlockStatement) newMethodNode.getCode()
        Parameter[] parameters = newMethodNode.parameters
        int parameterCount = parameters.length
        AnnotationNode joinAnnotation = AstUtils.findAnnotation(abstractMethodNode, Join)
        if (lookupById() && joinAnnotation == null && parameterCount == 1 && parameters[0].name == GormProperties.IDENTITY) {
            // optimize query by id
            Expression byId = callX(classX(domainClassNode), "get", varX(parameters[0]))
            implementById(domainClassNode, abstractMethodNode, newMethodNode, targetClassNode, body, byId)
        } else {
            Expression argsExpression = AstUtils.ZERO_ARGUMENTS
            VariableExpression queryVar = varX('$query')
            // def query = new DetachedCriteria(Foo)
            body.addStatement(
                    declS(queryVar, ctorX(getDetachedCriteriaType(domainClassNode), args(classX(domainClassNode.plainNodeReference))))
            )
            Expression connectionId = findConnectionId(newMethodNode)

            if (connectionId != null) {
                body.addStatement(
                        assignS(queryVar, callX(queryVar, "withConnection", connectionId))
                )
            }
            handleJoinAnnotation(joinAnnotation, body, queryVar)

            if (parameterCount > 0) {
                for (Parameter parameter in parameters) {
                    String parameterName = parameter.name
                    if (parameterName == GormProperties.IDENTITY) {
                        body.addStatement(
                                stmt(
                                        callX(queryVar, "idEq", varX(parameter))
                                )
                        )
                    } else if (isValidParameter(domainClassNode, parameter, parameterName)) {
                        body.addStatement(
                                stmt(
                                        callX(queryVar, "eq", args(constX(parameterName), varX(parameter)))
                                )
                        )
                    } else if (parameter.type == ClassHelper.MAP_TYPE && parameterName == 'args') {
                        argsExpression = varX(parameter)
                    } else {
                        AstUtils.error(
                                abstractMethodNode.declaringClass.module.context,
                                abstractMethodNode,
                                "Cannot implement method for argument [${parameterName}]. No property exists on domain class [$domainClassNode.name]"
                        )
                    }
                }

            }
            implementWithQuery(domainClassNode, abstractMethodNode, newMethodNode, targetClassNode, body, queryVar, argsExpression)
        }
    }

    protected ClassNode getDetachedCriteriaType(ClassNode domainClassNode) {
        DETACHED_CRITERIA
    }

    @PackageScope
    static void handleJoinAnnotation(AnnotationNode joinAnnotation, BlockStatement body, VariableExpression queryVar) {
        if (joinAnnotation != null) {
            Expression joinValue = joinAnnotation.getMember("value")
            if (joinValue != null) {
                Expression joinType = joinAnnotation.getMember("type")
                if (joinType instanceof PropertyExpression) {
                    body.addStatement(
                            stmt(callX(queryVar, "join", args(joinValue, joinType)))
                    )
                } else {
                    body.addStatement(
                            stmt(callX(queryVar, "join", joinValue))
                    )
                }
            }
        }
    }

    /**
     * Whether lookup by id is allowed by this implementation
     * @return True if it is
     */
    protected boolean lookupById() {
        return true
    }
    /**
     * Provide an implementation in the case querying for a single instance by id
     *
     * @param domainClassNode The domain class
     * @param abstractMethodNode the abstract method
     * @param newMethodNode The newly added method
     * @param targetClassNode The target class
     * @param body The body
     * @param byIdLookup The expression that looks up the object by id
     */
    abstract void implementById(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode, BlockStatement body, Expression byIdLookup)

    /**
     * Provide an implementation in the case of a query
     *
     * @param domainClassNode The domain class
     * @param abstractMethodNode the abstract method
     * @param newMethodNode The newly added method
     * @param targetClassNode The target class
     * @param body The body
     * @param detachedCriteriaVar The detached criteria query
     * @param queryArgs Any arguments to the query
     */
    abstract void implementWithQuery(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode, BlockStatement body, VariableExpression detachedCriteriaVar, Expression queryArgs)
}
