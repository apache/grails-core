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
import org.grails.datastore.gorm.services.implementers.IterableInterfaceProjectionBuilder
import org.grails.datastore.gorm.services.implementers.IterableProjectionServiceImplementer
import org.grails.datastore.gorm.services.implementers.IterableServiceImplementer
import org.grails.datastore.mapping.core.Ordered
import rx.Observable
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.Modifier

class ObservableResultAdapterSpec extends Specification {

    def "doesImplement returns false when the method is already marked as implemented"() {
        given:
        ObservableResultAdapter adapter = new ObservableResultAdapter(new DomainIterableImplementer(prefix: 'find'))
        MethodNode methodNode = methodNode('find', observableOf(ClassHelper.make(RxDomainFixture)))
        methodNode.putNodeMetaData(ServiceImplementer.IMPLEMENTED, true)

        expect:
        !adapter.doesImplement(ClassHelper.make(RxDomainFixture), methodNode)
    }

    def "doesImplement resolves the domain return type from the adapted implementer's generic type"() {
        given:
        ObservableResultAdapter adapter = new ObservableResultAdapter(new DomainIterableImplementer(prefix: 'find'))
        MethodNode methodNode = methodNode('find', observableOf(ClassHelper.make(RxDomainFixture)))

        expect:
        adapter.doesImplement(ClassHelper.make(RxDomainFixture), methodNode)
    }

    def "doesImplement returns false when the adapted implementer does not resolve a prefix"() {
        given:
        ObservableResultAdapter adapter = new ObservableResultAdapter(new DomainIterableImplementer(prefix: null))
        MethodNode methodNode = methodNode('find', observableOf(ClassHelper.make(RxDomainFixture)))

        expect:
        !adapter.doesImplement(ClassHelper.make(RxDomainFixture), methodNode)
    }

    def "doesImplement uses the adapted implementer's bound generic type when it is not a domain class"() {
        given:
        ObservableResultAdapter adapter = new ObservableResultAdapter(new NonDomainIterableImplementer(prefix: 'count'))
        MethodNode methodNode = methodNode('count', observableOf(ClassHelper.make(Long)))

        expect:
        adapter.doesImplement(ClassHelper.make(Object), methodNode)
    }

    def "doesImplement falls back to Object when the adapted implementer's generic type cannot be resolved"() {
        given:
        ObservableResultAdapter adapter = new ObservableResultAdapter(new UnboundIterableImplementer(prefix: 'get'))
        MethodNode methodNode = methodNode('get', observableOf(ClassHelper.make(String)))

        expect:
        adapter.doesImplement(ClassHelper.make(Object), methodNode)
    }

    @Unroll
    def "doesImplement delegates to the annotated implementer when isAnnotated is #annotated"() {
        given:
        ObservableResultAdapter adapter = new ObservableResultAdapter(new AnnotatedIterableImplementer(annotated: annotated))
        MethodNode methodNode = methodNode('find', observableOf(ClassHelper.make(RxDomainFixture)))

        expect:
        adapter.doesImplement(ClassHelper.make(RxDomainFixture), methodNode) == annotated

        where:
        annotated << [true, false]
    }

    @Unroll
    def "doesImplement delegates to isCompatibleReturnType for a plain iterable projection implementer when compatible is #compatible"() {
        given:
        ObservableResultAdapter adapter = new ObservableResultAdapter(new ProjectionIterableImplementer(prefix: 'find', compatible: compatible))
        MethodNode methodNode = methodNode('find', observableOf(ClassHelper.make(RxDomainFixture)))

        expect:
        adapter.doesImplement(ClassHelper.make(RxDomainFixture), methodNode) == compatible

        where:
        compatible << [true, false]
    }

    @Unroll
    def "doesImplement delegates to isInterfaceProjection for an interface projection builder when the result is #projects"() {
        given:
        ObservableResultAdapter adapter = new ObservableResultAdapter(new InterfaceProjectionIterableImplementer(prefix: 'find', interfaceProjection: projects))
        MethodNode methodNode = methodNode('find', observableOf(ClassHelper.make(RxDomainFixture)))

        expect:
        adapter.doesImplement(ClassHelper.make(RxDomainFixture), methodNode) == projects

        where:
        projects << [true, false]
    }

    def "doesImplement falls back to isInterfaceProjection when the Observable's generic type does not match the adapted domain type"() {
        given:
        ObservableResultAdapter adapter = new ObservableResultAdapter(new InterfaceProjectionIterableImplementer(prefix: 'find', interfaceProjection: true))
        MethodNode methodNode = methodNode('find', observableOf(ClassHelper.make(String)))

        expect:
        adapter.doesImplement(ClassHelper.make(RxDomainFixture), methodNode)
    }

    def "implement reassigns the domain class node to the resolved return type when the adapted implementer is not an interface projection builder"() {
        given:
        DomainIterableImplementer implementer = new DomainIterableImplementer(prefix: 'find')
        ObservableResultAdapter adapter = new ObservableResultAdapter(implementer)
        MethodNode abstractMethodNode = methodNode('find', observableOf(ClassHelper.make(RxDomainFixture)))
        MethodNode newMethod = methodNode('find', ClassHelper.OBJECT_TYPE)

        when:
        adapter.implement(ClassHelper.make(String), abstractMethodNode, newMethod, ClassHelper.make(Object))

        then:
        implementer.implementDomainClassNode.name == RxDomainFixture.name
    }

    def "implement leaves the domain class node untouched for an interface projection builder"() {
        given:
        InterfaceProjectionIterableImplementer implementer = new InterfaceProjectionIterableImplementer(prefix: 'find')
        ObservableResultAdapter adapter = new ObservableResultAdapter(implementer)
        MethodNode abstractMethodNode = methodNode('find', observableOf(ClassHelper.make(RxDomainFixture)))
        MethodNode newMethod = methodNode('find', ClassHelper.OBJECT_TYPE)

        when:
        adapter.implement(ClassHelper.make(String), abstractMethodNode, newMethod, ClassHelper.make(Object))

        then:
        implementer.implementDomainClassNode.name == String.name
    }

    def "implement leaves the domain class node untouched when the adapted implementer does not target a domain class"() {
        given:
        NonDomainIterableImplementer implementer = new NonDomainIterableImplementer(prefix: 'count')
        ObservableResultAdapter adapter = new ObservableResultAdapter(implementer)
        MethodNode abstractMethodNode = methodNode('count', observableOf(ClassHelper.make(Long)))
        MethodNode newMethod = methodNode('count', ClassHelper.OBJECT_TYPE)

        when:
        adapter.implement(ClassHelper.make(String), abstractMethodNode, newMethod, ClassHelper.make(Object))

        then:
        implementer.implementDomainClassNode.name == String.name
    }

    def "implement adds an RxSchedule annotation marking a single result when the resolved domain class is not a reactive entity"() {
        given:
        DomainIterableImplementer implementer = new DomainIterableImplementer(prefix: 'find')
        ObservableResultAdapter adapter = new ObservableResultAdapter(implementer)
        MethodNode abstractMethodNode = methodNode('find', observableOf(ClassHelper.make(RxDomainFixture)))
        MethodNode newMethod = methodNode('find', ClassHelper.OBJECT_TYPE)

        when:
        adapter.implement(ClassHelper.make(String), abstractMethodNode, newMethod, ClassHelper.make(Object))

        then:
        List annotations = newMethod.getAnnotations(ClassHelper.make(RxSchedule))
        annotations.size() == 1
        annotations[0].getMember('singleResult') == ConstantExpression.TRUE
    }

    def "implement does not add an RxSchedule annotation when the resolved domain class is itself a reactive entity"() {
        given:
        DomainIterableImplementer implementer = new DomainIterableImplementer(prefix: 'find')
        ObservableResultAdapter adapter = new ObservableResultAdapter(implementer)
        MethodNode abstractMethodNode = methodNode('find', observableOf(ClassHelper.make(RxEntity)))
        MethodNode newMethod = methodNode('find', ClassHelper.OBJECT_TYPE)

        when:
        adapter.implement(ClassHelper.make(String), abstractMethodNode, newMethod, ClassHelper.make(Object))

        then:
        newMethod.getAnnotations(ClassHelper.make(RxSchedule)).empty
    }

    def "implement delegates to the adapted implementer with the resolved arguments and sets the Iterable return type metadata"() {
        given:
        DomainIterableImplementer implementer = new DomainIterableImplementer(prefix: 'find')
        ObservableResultAdapter adapter = new ObservableResultAdapter(implementer)
        MethodNode abstractMethodNode = methodNode('find', observableOf(ClassHelper.make(RxDomainFixture)))
        MethodNode newMethod = methodNode('find', ClassHelper.OBJECT_TYPE)
        ClassNode targetClassNode = ClassHelper.make(Object)
        ClassNode domainClassNode = ClassHelper.make(String)

        when:
        adapter.implement(domainClassNode, abstractMethodNode, newMethod, targetClassNode)

        then:
        implementer.implementAbstractMethodNode.is(abstractMethodNode)
        implementer.implementNewMethodNode.is(newMethod)
        implementer.implementTargetClassNode.is(targetClassNode)
        ((ClassNode) newMethod.getNodeMetaData(ServiceImplementer.RETURN_TYPE)).name == Iterable.name
    }

    def "getOrder delegates to the adapted implementer when it implements Ordered"() {
        given:
        ObservableResultAdapter adapter = new ObservableResultAdapter(new DomainIterableImplementer(order: 42))

        expect:
        adapter.order == 42
    }

    def "getOrder returns zero when the adapted implementer does not implement Ordered"() {
        given:
        ObservableResultAdapter adapter = new ObservableResultAdapter(new NonDomainIterableImplementer())

        expect:
        adapter.order == 0
    }

    def "getAdapted returns the wrapped implementer"() {
        given:
        DomainIterableImplementer implementer = new DomainIterableImplementer()
        ObservableResultAdapter adapter = new ObservableResultAdapter(implementer)

        expect:
        adapter.adapted.is(implementer)
    }

    private static MethodNode methodNode(String name, ClassNode returnType) {
        new MethodNode(name, Modifier.PUBLIC, returnType, [] as Parameter[], ClassNode.EMPTY_ARRAY, null)
    }

    private static ClassNode observableOf(ClassNode genericType) {
        GenericsUtils.makeClassSafeWithGenerics(Observable, genericType)
    }
}

interface RxDomainFixture extends GormEntity<RxDomainFixture> {
}

class DomainIterableImplementer implements IterableServiceImplementer<GormEntity>, Ordered {
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

class NonDomainIterableImplementer implements IterableServiceImplementer<Long> {
    String prefix
    ClassNode implementDomainClassNode

    Iterable<String> getHandledPrefixes() { ['count'] }

    String resolvePrefix(MethodNode mn) { prefix }

    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) { false }

    void implement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        implementDomainClassNode = domainClassNode
    }
}

class UnboundIterableImplementer<T> implements IterableServiceImplementer<T> {
    String prefix

    Iterable<String> getHandledPrefixes() { ['get'] }

    String resolvePrefix(MethodNode mn) { prefix }

    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) { false }

    void implement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
    }
}

class AnnotatedIterableImplementer implements IterableServiceImplementer<GormEntity>, AnnotatedServiceImplementer {
    boolean annotated

    Iterable<String> getHandledPrefixes() { ['find'] }

    String resolvePrefix(MethodNode mn) { 'find' }

    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) { false }

    void implement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
    }

    boolean isAnnotated(ClassNode domainClass, MethodNode methodNode) { annotated }
}

class ProjectionIterableImplementer implements IterableProjectionServiceImplementer {
    String prefix
    boolean compatible

    Iterable<String> getHandledPrefixes() { ['find'] }

    String resolvePrefix(MethodNode mn) { prefix }

    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) { false }

    void implement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
    }

    boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) { compatible }
}

class InterfaceProjectionIterableImplementer implements IterableProjectionServiceImplementer, IterableInterfaceProjectionBuilder {
    String prefix
    boolean interfaceProjection
    ClassNode implementDomainClassNode

    Iterable<String> getHandledPrefixes() { ['find'] }

    String resolvePrefix(MethodNode mn) { prefix }

    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) { false }

    void implement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        implementDomainClassNode = domainClassNode
    }

    boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) { true }

    @Override
    boolean isInterfaceProjection(ClassNode domainClass, MethodNode methodNode, ClassNode returnType) { interfaceProjection }
}
