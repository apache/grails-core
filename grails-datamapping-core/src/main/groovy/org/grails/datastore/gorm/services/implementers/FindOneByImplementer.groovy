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

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.reflect.AstUtils

import static org.codehaus.groovy.ast.tools.GeneralUtils.*
/**
 * Handles implementation for a finder that returns a single result
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class FindOneByImplementer extends FindAllByImplementer implements SingleResultServiceImplementer<GormEntity> {
    static final List<String> HANDLED_PREFIXES = ['findBy','getBy', 'findOneBy']

    @Override
    void doImplement(ClassNode domainClassNode, ClassNode targetClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, boolean isArray) {
        BlockStatement body = (BlockStatement)newMethodNode.getCode()
        Parameter[] parameters = newMethodNode.parameters
        if(parameters.length == 1 && parameters[0].name == GormProperties.IDENTITY) {
            // add a method that invokes get(id)
            ArgumentListExpression argList = buildArgs(parameters, abstractMethodNode, body)
            Expression queryMethodCall = callX(findStaticApiForConnectionId(domainClassNode, newMethodNode), "get", argList)
            body.addStatement(
                returnS(
                    queryMethodCall
                )
            )
        }
        else {
            super.doImplement(domainClassNode, targetClassNode, abstractMethodNode, newMethodNode, isArray)
        }
    }

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        return AstUtils.isDomainClass(returnType)
    }

    @Override
    Iterable<String> getHandledPrefixes() {
        return HANDLED_PREFIXES
    }

    @Override
    protected ClassNode resolveDomainClassForReturnType(ClassNode currentDomainClass, boolean isArray, ClassNode returnType) {
        return returnType
    }

    @Override
    protected String getDynamicFinderPrefix() {
        return "findBy"
    }
}
