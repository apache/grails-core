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
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.reflect.AstUtils
import static org.codehaus.groovy.ast.tools.GeneralUtils.*
/**
 * Implements an update operation that returns the updated domain class
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class UpdateOneImplementer extends AbstractSaveImplementer implements SingleResultServiceImplementer<GormEntity> {
    static final List<String> HANDLED_PREFIXES = ['update']

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        Parameter[] parameters = methodNode.parameters
        if( parameters.length < 2 ) {
            return false
        }
        // first parameter should be the id
        else if(parameters[0].name != GormProperties.IDENTITY) {
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
    Iterable<String> getHandledPrefixes() {
        return HANDLED_PREFIXES
    }

    @Override
    void doImplement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        Parameter[] parameters = newMethodNode.parameters
        StaticMethodCallExpression lookupCall = callX(domainClassNode, "get", varX(parameters[0]))
        VariableExpression entityVar = varX('$entity', domainClassNode)

        BlockStatement body = (BlockStatement)newMethodNode.code
        // def $entity = Foo.get(id)
        // if($entity != null) {
        //    ... bind parameters
        //    $entity.save()
        // }
        body.addStatement(
            declS( entityVar, lookupCall)
        )
        BlockStatement ifBody = block()
        Statement saveStmt = bindParametersAndSave(domainClassNode, abstractMethodNode, parameters[1..-1] as Parameter[], ifBody, entityVar)
        ifBody.addStatement(saveStmt)
        body.addStatement(
            ifS( notNullX(entityVar),
                ifBody
            )
        )
    }
}
