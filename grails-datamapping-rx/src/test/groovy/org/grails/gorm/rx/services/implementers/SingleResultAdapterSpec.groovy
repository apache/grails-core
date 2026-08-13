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

package org.grails.gorm.rx.services.implementers

import grails.gorm.rx.RxEntity
import grails.gorm.rx.services.RxSchedule
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.tools.GenericsUtils
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.services.ServiceImplementer
import org.grails.datastore.gorm.services.implementers.AnnotatedServiceImplementer
import org.grails.datastore.gorm.services.implementers.SingleResultInterfaceProjectionBuilder
import org.grails.datastore.gorm.services.implementers.SingleResultProjectionServiceImplementer
import org.grails.datastore.gorm.services.implementers.SingleResultServiceImplementer
import org.grails.datastore.mapping.core.Ordered
import rx.Single
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.Modifier

class SingleResultAdapterSpec extends Specification {

    def "doesImplement returns false when the method is already marked as implemented"() {
        given:
        SingleResultAdapter adapter = new SingleResultAdapter(new DomainSingleImplementer(prefix: 'find'))
        MethodNode methodNode = methodNode('find', singleOf(ClassHelper.make(RxSingleDomainFixture)))
        methodNode.putNodeMetaData(ServiceImplementer.IMPLEMENTED, true)

        expect:
        !adapter.doesImplement(ClassHelper.make(RxSingleDomainFixture), methodNode)
    }

    def "doesImplement resolves the domain return type from the adapted implementer's generic type"() {
        given:
        SingleResultAdapter adapter = new SingleResultAdapter(new DomainSingleImplementer(prefix: 'find'))
        MethodNode methodNode = methodNode('find', singleOf(ClassHelper.make(RxSingleDomainFixture)))

        expect:
        adapter.doesImplement(ClassHelper.make(RxSingleDomainFixture), methodNode)
    }

    def "doesImplement returns false when the adapted implementer does not resolve a prefix"() {
        given:
        SingleResultAdapter adapter = new SingleResultAdapter(new DomainSingleImplementer(prefix: null))
        MethodNode methodNode = methodNode('find', singleOf(ClassHelper.make(RxSingleDomainFixture)))

        expect:
        !adapter.doesImplement(ClassHelper.make(RxSingleDomainFixture), methodNode)
    }

    def "doesImplement uses the adapted implementer's bound generic type when it is not a domain class"() {
        given:
        SingleResultAdapter adapter = new SingleResultAdapter(new NonDomainSingleImplementer(prefix: 'count'))
        MethodNode methodNode = methodNode('count', singleOf(ClassHelper.make(Long)))

        expect:
        adapter.doesImplement(ClassHelper.make(Object), methodNode)
    }

    def "doesImplement falls back to Object when the adapted implementer's generic type cannot be resolved"() {
        given:
        SingleResultAdapter adapter = new SingleResultAdapter(new UnboundSingleImplementer(prefix: 'get'))
        MethodNode methodNode = methodNode('get', singleOf(ClassHelper.make(String)))

        expect:
        adapter.doesImplement(ClassHelper.make(Object), methodNode)
    }

    @Unroll
    def "doesImplement delegates to the annotated implementer when isAnnotated is #annotated"() {
        given:
        SingleResultAdapter adapter = new SingleResultAdapter(new AnnotatedSingleImplementer(annotated: annotated))
        MethodNode methodNode = methodNode('find', singleOf(ClassHelper.make(RxSingleDomainFixture)))

        expect:
        adapter.doesImplement(ClassHelper.make(RxSingleDomainFixture), methodNode) == annotated

        where:
        annotated << [true, false]
    }

    @Unroll
    def "doesImplement delegates to isCompatibleReturnType for a single result projection implementer when compatible is #compatible"() {
        given:
        SingleResultAdapter adapter = new SingleResultAdapter(new ProjectionSingleImplementer(prefix: 'find', compatible: compatible))
        MethodNode methodNode = methodNode('find', singleOf(ClassHelper.make(RxSingleDomainFixture)))

        expect:
        adapter.doesImplement(ClassHelper.make(RxSingleDomainFixture), methodNode) == compatible

        where:
        compatible << [true, false]
    }

    def "doesImplement falls back to isInterfaceProjection when the Single's generic type does not match the adapted domain type"() {
        given:
        SingleResultAdapter adapter = new SingleResultAdapter(new InterfaceProjectionSingleImplementer(prefix: 'find', interfaceProjection: true))
        MethodNode methodNode = methodNode('find', singleOf(ClassHelper.make(String)))

        expect:
        adapter.doesImplement(ClassHelper.make(RxSingleDomainFixture), methodNode)
    }

    def "implement delegates to the adapted implementer with the resolved arguments and sets the resolved return type metadata"() {
        given:
        DomainSingleImplementer implementer = new DomainSingleImplementer(prefix: 'find')
        SingleResultAdapter adapter = new SingleResultAdapter(implementer)
        MethodNode abstractMethodNode = methodNode('find', singleOf(ClassHelper.make(RxSingleDomainFixture)))
        MethodNode newMethod = methodNode('find', ClassHelper.OBJECT_TYPE)
        ClassNode targetClassNode = ClassHelper.make(Object)
        ClassNode domainClassNode = ClassHelper.make(String)

        when:
        adapter.implement(domainClassNode, abstractMethodNode, newMethod, targetClassNode)

        then:
        implementer.implementDomainClassNode.is(domainClassNode)
        implementer.implementAbstractMethodNode.is(abstractMethodNode)
        implementer.implementNewMethodNode.is(newMethod)
        implementer.implementTargetClassNode.is(targetClassNode)
        ((ClassNode) newMethod.getNodeMetaData(ServiceImplementer.RETURN_TYPE)).name == RxSingleDomainFixture.name
    }

    def "implement adds an RxSchedule annotation marking a single result when the resolved return type is not a reactive entity"() {
        given:
        DomainSingleImplementer implementer = new DomainSingleImplementer(prefix: 'find')
        SingleResultAdapter adapter = new SingleResultAdapter(implementer)
        MethodNode abstractMethodNode = methodNode('find', singleOf(ClassHelper.make(RxSingleDomainFixture)))
        MethodNode newMethod = methodNode('find', ClassHelper.OBJECT_TYPE)

        when:
        adapter.implement(ClassHelper.make(String), abstractMethodNode, newMethod, ClassHelper.make(Object))

        then:
        List annotations = newMethod.getAnnotations(ClassHelper.make(RxSchedule))
        annotations.size() == 1
        annotations[0].getMember('singleResult') == ConstantExpression.TRUE
    }

    def "implement does not add an RxSchedule annotation when the resolved return type is itself a reactive entity"() {
        given:
        DomainSingleImplementer implementer = new DomainSingleImplementer(prefix: 'find')
        SingleResultAdapter adapter = new SingleResultAdapter(implementer)
        MethodNode abstractMethodNode = methodNode('find', singleOf(ClassHelper.make(RxEntity)))
        MethodNode newMethod = methodNode('find', ClassHelper.OBJECT_TYPE)

        when:
        adapter.implement(ClassHelper.make(String), abstractMethodNode, newMethod, ClassHelper.make(Object))

        then:
        newMethod.getAnnotations(ClassHelper.make(RxSchedule)).empty
    }

    def "getOrder delegates to the adapted implementer when it implements Ordered"() {
        given:
        SingleResultAdapter adapter = new SingleResultAdapter(new DomainSingleImplementer(order: 42))

        expect:
        adapter.order == 42
    }

    def "getOrder returns zero when the adapted implementer does not implement Ordered"() {
        given:
        SingleResultAdapter adapter = new SingleResultAdapter(new NonDomainSingleImplementer())

        expect:
        adapter.order == 0
    }

    def "getAdapted returns the wrapped implementer"() {
        given:
        DomainSingleImplementer implementer = new DomainSingleImplementer()
        SingleResultAdapter adapter = new SingleResultAdapter(implementer)

        expect:
        adapter.adapted.is(implementer)
    }

    private static MethodNode methodNode(String name, ClassNode returnType) {
        new MethodNode(name, Modifier.PUBLIC, returnType, [] as Parameter[], ClassNode.EMPTY_ARRAY, null)
    }

    private static ClassNode singleOf(ClassNode genericType) {
        GenericsUtils.makeClassSafeWithGenerics(Single, genericType)
    }
}

interface RxSingleDomainFixture extends GormEntity<RxSingleDomainFixture> {
}

class DomainSingleImplementer implements SingleResultServiceImplementer<GormEntity>, Ordered {
    String prefix
    int order = 0
    ClassNode implementDomainClassNode
    MethodNode implementAbstractMethodNode
    MethodNode implementNewMethodNode
    ClassNode implementTargetClassNode

    Iterable<String> getHandledPrefixes() { ['find'] }

    String resolvePrefix(MethodNode mn) { prefix }

    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) { false }

    void implement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        implementDomainClassNode = domainClassNode
        implementAbstractMethodNode = abstractMethodNode
        implementNewMethodNode = newMethodNode
        implementTargetClassNode = targetClassNode
    }

    int getOrder() { order }
}

class NonDomainSingleImplementer implements SingleResultServiceImplementer<Long> {
    String prefix

    Iterable<String> getHandledPrefixes() { ['count'] }

    String resolvePrefix(MethodNode mn) { prefix }

    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) { false }

    void implement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
    }
}

class UnboundSingleImplementer<T> implements SingleResultServiceImplementer<T> {
    String prefix

    Iterable<String> getHandledPrefixes() { ['get'] }

    String resolvePrefix(MethodNode mn) { prefix }

    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) { false }

    void implement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
    }
}

class AnnotatedSingleImplementer implements SingleResultServiceImplementer<GormEntity>, AnnotatedServiceImplementer {
    boolean annotated

    Iterable<String> getHandledPrefixes() { ['find'] }

    String resolvePrefix(MethodNode mn) { 'find' }

    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) { false }

    void implement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
    }

    boolean isAnnotated(ClassNode domainClass, MethodNode methodNode) { annotated }
}

class ProjectionSingleImplementer implements SingleResultProjectionServiceImplementer {
    String prefix
    boolean compatible

    Iterable<String> getHandledPrefixes() { ['find'] }

    String resolvePrefix(MethodNode mn) { prefix }

    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) { false }

    void implement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
    }

    boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) { compatible }
}

class InterfaceProjectionSingleImplementer implements SingleResultProjectionServiceImplementer, SingleResultInterfaceProjectionBuilder {
    String prefix
    boolean interfaceProjection

    Iterable<String> getHandledPrefixes() { ['find'] }

    String resolvePrefix(MethodNode mn) { prefix }

    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) { false }

    void implement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
    }

    boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) { true }

    @Override
    boolean isInterfaceProjection(ClassNode domainClass, MethodNode methodNode, ClassNode returnType) { interfaceProjection }
}
