/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.services.implementers

import grails.gorm.services.Join
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.gorm.finders.MatchSpec
import org.grails.datastore.gorm.services.transform.ServiceTransformation
import org.grails.datastore.mapping.core.Ordered
import org.grails.datastore.mapping.reflect.AstUtils

import static org.codehaus.groovy.ast.tools.GeneralUtils.varX
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS
import static org.codehaus.groovy.ast.tools.GeneralUtils.args
import static org.codehaus.groovy.ast.tools.GeneralUtils.castX
import static org.codehaus.groovy.ast.ClassHelper.MAP_TYPE
import static org.grails.datastore.mapping.reflect.AstUtils.hasProperty
import static org.grails.datastore.mapping.reflect.AstUtils.error
import static org.grails.datastore.mapping.reflect.AstUtils.mapX
import static org.grails.datastore.mapping.reflect.AstUtils.findAnnotation

/**
 * Automatically implement services that find objects based an arguments
 *
 * @since 6.1
 * @author Graeme Rocher
 */
@CompileStatic
class FindAllByImplementer extends AbstractArrayOrIterableResultImplementer implements Ordered, IterableServiceImplementer<GormEntity> {
    static final List<String> HANDLED_PREFIXES = ['listBy','findBy', 'findAllBy']
    public static final int POSITION = -100

    // position before FindAllImplementer
    @Override
    int getOrder() {
        return POSITION
    }

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        if(super.doesImplement(domainClass, methodNode)) {
            String methodName = methodNode.name
            int parameterCount = methodNode.parameters.length
            for(String prefix in getHandledPrefixes()) {
                if(methodName.startsWith(prefix) && buildMatchSpec(prefix, methodName, parameterCount) != null) {
                    return true
                }
            }
        }
        return false
    }

    @Memoized(maxCacheSize = 100)
    protected MatchSpec buildMatchSpec(String prefix, String methodName, int parameterCount) {
        DynamicFinder.buildMatchSpec(prefix, methodName, parameterCount)
    }

    @Override
    Iterable<String> getHandledPrefixes() {
        return HANDLED_PREFIXES
    }

    @Override
    void doImplement(ClassNode domainClassNode, ClassNode targetClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, boolean isArray) {
        BlockStatement body = (BlockStatement)newMethodNode.getCode()
        ClassNode returnType = newMethodNode.returnType
        String methodName = newMethodNode.name
        Parameter[] parameters = newMethodNode.parameters
        int parameterCount = parameters.length
        MatchSpec matchSpec = null
        for(String prefix in getHandledPrefixes()) {
            matchSpec = buildMatchSpec(prefix, methodName, parameterCount)
            if(methodName.startsWith(prefix) &&  matchSpec != null) {
                break
            }
        }

        if(matchSpec == null) {
            AstUtils.error(abstractMethodNode.declaringClass.module.context, abstractMethodNode, ServiceTransformation.NO_IMPLEMENTATIONS_MESSAGE)
        }
        else {
            // validate the properties
            for(String propertyName in matchSpec.propertyNames) {
                if(!hasProperty(domainClassNode, propertyName)) {
                    error(abstractMethodNode.declaringClass.module.context, abstractMethodNode, "Cannot implement finder for non-existent property [$propertyName] of class [$domainClassNode.name]")
                }
            }

            // add a method that invokes list()
            String methodPrefix = getDynamicFinderPrefix()
            String finderCallName = "${methodPrefix}${matchSpec.queryExpression}"
            ArgumentListExpression argList = buildArgs(parameters, abstractMethodNode, body)
            Expression findCall = callX(findStaticApiForConnectionId(domainClassNode, newMethodNode), finderCallName, argList)
            if(isArray || ClassHelper.isNumberType(returnType)) {
                // handle array cast
                findCall = castX( returnType.plainNodeReference, findCall)
            }
            body.addStatement(
                    buildReturnStatement(domainClassNode, abstractMethodNode, newMethodNode, findCall)
            )
        }

    }

    protected ArgumentListExpression buildArgs(Parameter[] parameters, MethodNode abstractMethodNode, BlockStatement body) {
        ArgumentListExpression argList = args(parameters)
        AnnotationNode joinAnn = findAnnotation(abstractMethodNode, Join)
        if (joinAnn != null) {
            def joinMap = mapX((joinAnn.getMember('value').text): constX('join'))
            if (parameters.length > 0) {
                if (parameters[-1].name == 'args' && parameters[-1].type == MAP_TYPE) {
                    body.addStatement(
                            stmt(callX(varX(parameters[-1]), "put", args(constX("fetch"), joinMap)))
                    )
                }
                else {
                    argList.addExpression(mapX(fetch: joinMap))
                }
            } else {

                argList.addExpression(mapX(fetch: joinMap))
            }
        }
        argList
    }

    protected Statement buildReturnStatement(ClassNode domainClass, MethodNode abstractMethodNode, MethodNode newMethodNode, Expression queryExpression) {
        returnS(
            queryExpression
        )
    }


    protected String getDynamicFinderPrefix() {
        return "findAllBy"
    }
}
