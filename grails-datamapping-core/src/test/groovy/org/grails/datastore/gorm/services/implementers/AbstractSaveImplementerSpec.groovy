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

import java.lang.reflect.Modifier

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.Statement
import spock.lang.Specification

import static org.codehaus.groovy.ast.tools.GeneralUtils.varX

/**
 * The five-parameter {@code bindParametersAndSave} implementation from 7.0.x must coexist with
 * the six-parameter descriptor shipped by 8.1.x so custom data-service implementers compiled
 * against either signature keep working after the merge-up.
 */
class AbstractSaveImplementerSpec extends Specification {

    void 'AbstractSaveImplementer exposes both the five- and six-parameter bindParametersAndSave overloads'() {
        given:
        def overloads = AbstractSaveImplementer.declaredMethods.findAll { it.name == 'bindParametersAndSave' }

        expect:
        overloads.size() == 2
        overloads.any { it.parameterTypes.length == 5 }
        overloads.any { method ->
            method.parameterTypes.length == 6 && method.parameterTypes[2] == MethodNode
        }
        overloads.every { Modifier.isProtected(it.modifiers) }
    }

    void 'the six-parameter overload delegates to the five-parameter implementation'() {
        given:
        int fiveParamCalls = 0
        SaveImplementer implementer = new SaveImplementer() {
            @Override
            protected Statement bindParametersAndSave(ClassNode domainClassNode, MethodNode abstractMethodNode, Parameter[] parameters, BlockStatement body, VariableExpression entityVar) {
                fiveParamCalls++
                return new BlockStatement()
            }
        }
        ClassNode domainClassNode = ClassHelper.make('Foo')
        Parameter[] parameters = [new Parameter(ClassHelper.make(Serializable), 'id')] as Parameter[]
        MethodNode abstractMethodNode = methodNode('saveFoo', domainClassNode, parameters, null)
        MethodNode newMethodNode = methodNode('saveFoo', domainClassNode, parameters, new BlockStatement())
        BlockStatement body = (BlockStatement) newMethodNode.code
        VariableExpression entityVar = varX('$entity', domainClassNode)

        when:
        Statement stmt = implementer.bindParametersAndSave(domainClassNode, abstractMethodNode, newMethodNode, parameters, body, entityVar)

        then:
        fiveParamCalls == 1
        stmt instanceof BlockStatement
    }

    void 'SaveImplementer routes through the six-parameter bindParametersAndSave overload'() {
        given:
        int sixParamCalls = 0
        SaveImplementer implementer = new SaveImplementer() {
            @Override
            protected Statement bindParametersAndSave(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, Parameter[] parameters, BlockStatement body, VariableExpression entityVar) {
                sixParamCalls++
                return new BlockStatement()
            }
        }
        ClassNode domainClassNode = ClassHelper.make('Foo')
        Parameter[] parameters = [new Parameter(ClassHelper.make(Serializable), 'id')] as Parameter[]
        MethodNode abstractMethodNode = methodNode('saveFoo', domainClassNode, parameters, null)
        MethodNode newMethodNode = methodNode('saveFoo', domainClassNode, parameters, new BlockStatement())
        ClassNode targetClassNode = new ClassNode('FooServiceImpl', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)

        when:
        implementer.doImplement(domainClassNode, abstractMethodNode, newMethodNode, targetClassNode)

        then:
        sixParamCalls == 1
    }

    void 'UpdateOneImplementer routes through the six-parameter bindParametersAndSave overload'() {
        given:
        int sixParamCalls = 0
        UpdateOneImplementer implementer = new UpdateOneImplementer() {
            @Override
            protected Statement bindParametersAndSave(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, Parameter[] parameters, BlockStatement body, VariableExpression entityVar) {
                sixParamCalls++
                return new BlockStatement()
            }
        }
        ClassNode domainClassNode = ClassHelper.make('Foo')
        Parameter[] parameters = [new Parameter(ClassHelper.make(Serializable), 'id')] as Parameter[]
        MethodNode abstractMethodNode = methodNode('updateFoo', domainClassNode, parameters, null)
        MethodNode newMethodNode = methodNode('updateFoo', domainClassNode, parameters, new BlockStatement())
        ClassNode targetClassNode = new ClassNode('FooServiceImpl', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)

        when:
        implementer.doImplement(domainClassNode, abstractMethodNode, newMethodNode, targetClassNode)

        then:
        sixParamCalls == 1
    }

    private static MethodNode methodNode(String name, ClassNode returnType, Parameter[] parameters, BlockStatement body) {
        new MethodNode(name, Modifier.PUBLIC, returnType, parameters, ClassNode.EMPTY_ARRAY, body)
    }
}
