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
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.reflect.AstUtils

import static org.codehaus.groovy.ast.tools.GeneralUtils.*
import static org.grails.datastore.gorm.transform.AstMethodDispatchUtils.namedArgs

/**
 * Implementations saving new entities
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class SaveImplementer extends AbstractSaveImplementer implements SingleResultServiceImplementer<GormEntity> {
    static final List<String> HANDLED_PREFIXES = ['save', 'store', 'persist']

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        if( methodNode.parameters.length == 0 ) {
            return false
        }
        else {
            return super.doesImplement(domainClass, methodNode)
        }
    }

    @Override
    void doImplement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        BlockStatement body = (BlockStatement) newMethodNode.getCode()
        Parameter[] parameters = newMethodNode.parameters
        int parameterCount = parameters.length
        if(parameterCount == 1 && AstUtils.isDomainClass(parameters[0].type)) {
            body.addStatement(
                returnS( callX( varX( parameters[0] ), "save", namedArgs(failOnError: ConstantExpression.TRUE) ) )
            )
        }
        else {
            VariableExpression entityVar = varX('$entity')
            body.addStatement(
                declS(entityVar, ctorX(domainClassNode))
            )
            body.addStatement(
                bindParametersAndSave(domainClassNode, abstractMethodNode, parameters, body, entityVar)
            )

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
}
