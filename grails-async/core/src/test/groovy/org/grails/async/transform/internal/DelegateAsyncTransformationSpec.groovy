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
package org.grails.async.transform.internal

import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType

import org.codehaus.groovy.GroovyBugError
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ListExpression
import spock.lang.Specification

import grails.async.Promise
import org.apache.grails.common.compiler.GroovyTransformOrder

class DelegateAsyncTransformationSpec extends Specification {

    void 'the transformation validates its input nodes and declares its priority'() {
        given:
        DelegateAsyncTransformation transformation = new DelegateAsyncTransformation()

        when:
        transformation.visit([new ClassNode(String)] as ASTNode[], null)

        then:
        GroovyBugError e = thrown()
        e.message.contains('Internal error: expecting [AnnotationNode, AnnotatedNode] but got: [java.lang.String]')
        transformation.priority() == GroovyTransformOrder.DELEGATE_ASYNC_ORDER
        DelegateAsyncTransformation.OBJECT_CLASS_NODE.name == 'java.lang.Object'
        DelegateAsyncTransformation.GROOVY_OBJECT_CLASS_NODE.name == 'groovy.lang.GroovyObjectSupport'
    }

    void 'the transactional method transformer contract is a single callback'() {
        expect:
        DelegateAsyncTransactionalMethodTransformer.isInterface()
        DelegateAsyncTransactionalMethodTransformer.getMethod('transformTransactionalMethod', ClassNode, ClassNode, MethodNode, ListExpression).returnType == void
    }

    void 'a class level annotation creates the delegate field and wraps every public method'() {
        given:
        GroovyClassLoader gcl = new GroovyClassLoader()
        gcl.parseClass('''
            import grails.async.DelegateAsync

            class CalcApi {
                int add(int a, int b) { a + b }
                Object anything() { 'x' }
                void fire() { }
                static int ignored() { 1 }
            }
            @DelegateAsync(CalcApi)
            class AsyncCalc {
                String anything() { 'declared' }
            }
        ''')
        Class cls = gcl.loadClass('AsyncCalc')
        Object instance = cls.getDeclaredConstructor().newInstance()

        when:
        Method add = cls.getMethod('add', int, int)
        Method fire = cls.getMethod('fire')

        then:
        cls.getDeclaredField('$calcApi').type.name == 'CalcApi'
        add.returnType == Promise
        ((ParameterizedType) add.genericReturnType).actualTypeArguments == [Integer] as Class[]
        fire.returnType == Promise
        !(fire.genericReturnType instanceof ParameterizedType)
        cls.getMethod('anything').returnType == String
        cls.getMethod('ignored').returnType == Promise
        !java.lang.reflect.Modifier.isStatic(cls.getMethod('ignored').modifiers)
        ((Promise) instance.add(2, 3)).get() == 5
        ((Promise) instance.fire()).get() == null
    }

}
