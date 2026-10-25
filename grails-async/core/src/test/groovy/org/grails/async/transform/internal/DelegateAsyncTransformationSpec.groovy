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
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import spock.lang.Specification

import grails.async.DelegateAsync
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

    void 'input that is not an annotation on an annotated node is rejected'() {
        given:
        DelegateAsyncTransformation transformation = new DelegateAsyncTransformation()

        when:
        transformation.visit(nodes as ASTNode[], null)

        then:
        thrown(GroovyBugError)

        where:
        nodes << [
            [new AnnotationNode(ClassHelper.make(DelegateAsync))],
            [new AnnotationNode(ClassHelper.make(DelegateAsync)), new BlockStatement()],
            [new AnnotationNode(ClassHelper.make(DelegateAsync)), new ClassNode(String), new ClassNode(String)]
        ]
    }

    void 'a class level annotation without a delegate type adds nothing'() {
        given:
        GroovyClassLoader gcl = new GroovyClassLoader()
        gcl.parseClass('''
            import grails.async.DelegateAsync

            @DelegateAsync
            class Untouched {
                String name() { 'plain' }
            }
        ''')
        Class cls = gcl.loadClass('Untouched')

        expect:
        cls.declaredFields.findAll { it.name.startsWith('$') && !it.synthetic } == []
        cls.declaredMethods.every { it.returnType != Promise }
    }

    void 'a delegate field the class already declares is reused rather than replaced'() {
        given:
        GroovyClassLoader gcl = new GroovyClassLoader()
        gcl.parseClass('''
            import grails.async.DelegateAsync

            class GreeterApi {
                String greet(String who) { "hello ${who}" }
            }
            @DelegateAsync(GreeterApi)
            class AsyncGreeter {
                private GreeterApi $greeterApi = new GreeterApi() {
                    String greet(String who) { "custom ${who}" }
                }
            }
        ''')
        Class cls = gcl.loadClass('AsyncGreeter')
        Object instance = cls.getDeclaredConstructor().newInstance()

        expect:
        cls.declaredFields.findAll { it.name == '$greeterApi' }.size() == 1
        ((Promise) instance.greet('you')).get() == 'custom you'
    }

    void 'an annotated field becomes the delegate of its owning class'() {
        given:
        GroovyClassLoader gcl = new GroovyClassLoader()
        gcl.parseClass('''
            import grails.async.DelegateAsync

            class CounterApi {
                int count(String text) { text.length() }
            }
            class AsyncCounter {
                @DelegateAsync
                private CounterApi counter = new CounterApi()
            }
        ''')
        Class cls = gcl.loadClass('AsyncCounter')
        Object instance = cls.getDeclaredConstructor().newInstance()

        expect:
        cls.getMethod('count', String).returnType == Promise
        ((Promise) instance.count('four')).get() == 4
    }

    void 'only public methods the class does not declare are wrapped'() {
        given:
        GroovyClassLoader gcl = new GroovyClassLoader()
        gcl.parseClass('''
            import grails.async.DelegateAsync

            class MixedApi {
                String visible() { 'visible' }
                protected String guarded() { 'guarded' }
                private String hidden() { 'hidden' }
            }
            @DelegateAsync(MixedApi)
            class AsyncMixed {
            }
        ''')
        Class cls = gcl.loadClass('AsyncMixed')
        Set<String> wrapped = cls.declaredMethods.findAll { it.returnType == Promise }*.name as Set

        expect:
        wrapped == ['visible'] as Set
    }

    void 'generic return and parameter types are carried over to the wrapper'() {
        given:
        GroovyClassLoader gcl = new GroovyClassLoader()
        gcl.parseClass('''
            import grails.async.DelegateAsync

            class RepoApi {
                List<String> names() { ['a', 'b'] }
                int total(List<Integer> numbers) { numbers.sum() as int }
            }
            @DelegateAsync(RepoApi)
            class AsyncRepo {
            }
        ''')
        Class cls = gcl.loadClass('AsyncRepo')
        Object instance = cls.getDeclaredConstructor().newInstance()
        Method names = cls.getMethod('names')
        Method total = cls.getMethod('total', List)

        expect:
        ((ParameterizedType) names.genericReturnType).actualTypeArguments[0].typeName == 'java.util.List<java.lang.String>'
        ((ParameterizedType) total.genericParameterTypes[0]).actualTypeArguments == [Integer] as Class[]
        ((Promise) instance.names()).get() == ['a', 'b']
        ((Promise) instance.total([1, 2, 3])).get() == 6
    }

    void 'a generic delegate type resolves its type variables against the annotated class'() {
        given:
        GroovyClassLoader gcl = new GroovyClassLoader()
        gcl.parseClass('''
            import grails.async.DelegateAsync

            class BoxApi<T> {
                T open() { null }
            }
            @DelegateAsync(BoxApi)
            class AsyncBox<T> {
            }
        ''')
        Class cls = gcl.loadClass('AsyncBox')

        expect:
        cls.getMethod('open').returnType == Promise
    }

    void 'the transactional transformer is consulted once for every wrapped method'() {
        given:
        DefaultDelegateAsyncTransactionalMethodTransformer.TRANSFORMED.clear()
        GroovyClassLoader gcl = new GroovyClassLoader()

        when:
        gcl.parseClass('''
            import grails.async.DelegateAsync

            class AuditApi {
                void first() { }
                int second() { 2 }
            }
            @DelegateAsync(AuditApi)
            class AsyncAudit {
            }
        ''')

        then:
        DefaultDelegateAsyncTransactionalMethodTransformer.TRANSFORMED.containsAll(['AuditApi.first', 'AuditApi.second'])
        !DefaultDelegateAsyncTransactionalMethodTransformer.TRANSFORMED.contains('AuditApi.hashCode')
    }

    void 'without the plugin transformer on the classpath the methods are wrapped with no transactional handling'() {
        given: 'a loader that defines the transformation itself and cannot see the plugin transformer'
        String pluginTransformer = 'org.grails.async.transform.internal.DefaultDelegateAsyncTransactionalMethodTransformer'
        URL mainClasses = DelegateAsyncTransformation.protectionDomain.codeSource.location
        ClassLoader isolated = new URLClassLoader([mainClasses] as URL[], getClass().classLoader) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name == pluginTransformer) {
                    throw new ClassNotFoundException(name)
                }
                if (name.startsWith('org.grails.async.transform.internal.')) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> loaded = findLoadedClass(name) ?: findClass(name)
                        return loaded
                    }
                }
                return super.loadClass(name, resolve)
            }
        }
        DefaultDelegateAsyncTransactionalMethodTransformer.TRANSFORMED.clear()

        when:
        GroovyClassLoader gcl = new GroovyClassLoader(isolated)
        gcl.parseClass('''
            import grails.async.DelegateAsync

            class PlainApi {
                int answer() { 42 }
            }
            @DelegateAsync(PlainApi)
            class AsyncPlain {
            }
        ''')
        Class cls = gcl.loadClass('AsyncPlain')

        then:
        cls.getMethod('answer').returnType.name == 'grails.async.Promise'
        DefaultDelegateAsyncTransactionalMethodTransformer.TRANSFORMED.isEmpty()
    }

    void 'an annotation on a node that is neither a class nor a field is ignored'() {
        given:
        DelegateAsyncTransformation transformation = new DelegateAsyncTransformation()
        MethodNode method = new MethodNode('run', 1, ClassHelper.VOID_TYPE, [] as Parameter[], ClassNode.EMPTY_ARRAY, null)

        when:
        transformation.visit([new AnnotationNode(ClassHelper.make(DelegateAsync)), method] as ASTNode[], null)

        then:
        noExceptionThrown()
    }

}
