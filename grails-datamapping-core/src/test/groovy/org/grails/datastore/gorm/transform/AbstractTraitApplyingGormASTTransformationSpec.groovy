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
package org.grails.datastore.gorm.transform

import java.lang.reflect.Modifier

import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode

import spock.lang.Specification

/**
 * {@code AbstractTraitApplyingGormASTTransformation} is only ever exercised in this module through
 * its one concrete subclass, {@code ServiceTransformation}, which overrides {@code shouldWeave}
 * with its own logic (never delegating to the default) and, for the interface/abstract-class case it
 * actually tests, calls the static {@code weaveTraitWithGenerics} directly rather than going through
 * the instance {@code weaveTrait} method. That leaves this base class's own default behavior -
 * {@code shouldWeave} returning {@code true}, the instance {@code weaveTrait} delegating to
 * {@code TraitComposer.doExtendTraits}, and several edge branches of {@code weaveTraitWithGenerics}
 * (a trait with no generic type parameters, an interface class node that must be skipped entirely,
 * and a trait whose generic arity does not match - fully or partially - the number of arguments
 * supplied) - unexercised. These specs cover that gap directly: the {@code weaveTraitWithGenerics}
 * edge branches and the default {@code shouldWeave} are tested against bare, detached
 * {@code ClassNode}s (the technique used in {@link AbstractGormASTTransformationSpec}), while the
 * instance {@code weaveTrait} method - and the real {@code TraitComposer.doExtendTraits}
 * call it makes - is proven by compiling a real class through {@link TestTraitWeavingTransformation},
 * a test-only local transform applied via {@link ApplyTestTraitWeaving}, and asserting the compiled
 * class actually gained the trait's method.
 */
class AbstractTraitApplyingGormASTTransformationSpec extends Specification {

    static class MinimalTraitTransformation extends AbstractTraitApplyingGormASTTransformation {

        @Override
        protected Class getTraitClass() {
            TestWeavableTrait
        }

        @Override
        protected ClassNode getAnnotationType() {
            ClassHelper.make(ApplyTestTraitWeaving)
        }

        @Override
        protected Object getAppliedMarker() {
            new Object()
        }

        @Override
        int priority() {
            0
        }
    }

    private static ClassNode newTargetClassNode(String name) {
        new ClassNode(name, 0, ClassHelper.OBJECT_TYPE)
    }

    void "shouldWeave defaults to true when a subclass does not override it"() {
        given:
        MinimalTraitTransformation transformation = new MinimalTraitTransformation()
        ClassNode targetClassNode = newTargetClassNode('org.grails.datastore.gorm.transform.fixture.ShouldWeaveTarget')
        AnnotationNode annotationNode = new AnnotationNode(ClassHelper.make(ApplyTestTraitWeaving))

        expect:
        transformation.shouldWeave(annotationNode, targetClassNode)
    }

    void "weaveTraitWithGenerics adds the plain interface when the trait declares no generic type parameters"() {
        given:
        ClassNode targetClassNode = newTargetClassNode('org.grails.datastore.gorm.transform.fixture.NoGenericsWeaveTarget')

        expect:
        !targetClassNode.implementsInterface(ClassHelper.make(TestWeavableTrait))

        when:
        AbstractTraitApplyingGormASTTransformation.weaveTraitWithGenerics(targetClassNode, TestWeavableTrait)

        then:
        targetClassNode.implementsInterface(ClassHelper.make(TestWeavableTrait))
    }

    void "weaveTraitWithGenerics pads missing generic arguments with Object when fewer arguments are supplied than the trait declares"() {
        given:
        ClassNode targetClassNode = newTargetClassNode('org.grails.datastore.gorm.transform.fixture.GenericsPaddingWeaveTarget')
        ClassNode traitClassNode = ClassHelper.make(SingleGenericTestTrait)

        expect:
        !targetClassNode.implementsInterface(traitClassNode)

        when: 'weaving with zero generic arguments even though the trait declares one'
        AbstractTraitApplyingGormASTTransformation.weaveTraitWithGenerics(targetClassNode, SingleGenericTestTrait)

        then: 'the missing generic argument slot is padded with Object'
        targetClassNode.implementsInterface(traitClassNode)
        ClassNode wovenInterface = targetClassNode.interfaces.find { it.name == SingleGenericTestTrait.name }
        wovenInterface.genericsTypes.length == 1
        wovenInterface.genericsTypes[0].type == ClassHelper.OBJECT_TYPE
    }

    void "weaveTraitWithGenerics is a no-op for an interface class node"() {
        given: 'a class node that is itself an interface'
        ClassNode interfaceClassNode = new ClassNode(
                'org.grails.datastore.gorm.transform.fixture.NoWeaveInterfaceTarget',
                Modifier.PUBLIC | Modifier.INTERFACE,
                ClassHelper.OBJECT_TYPE)

        expect:
        interfaceClassNode.interface

        when:
        AbstractTraitApplyingGormASTTransformation.weaveTraitWithGenerics(interfaceClassNode, TestWeavableTrait)

        then: 'the trait is never added because interfaces are skipped entirely'
        !interfaceClassNode.implementsInterface(ClassHelper.make(TestWeavableTrait))
        interfaceClassNode.interfaces.length == 0
    }

    void "weaveTraitWithGenerics pads only the missing slots when some, but not all, generic arguments are supplied"() {
        given:
        ClassNode targetClassNode = newTargetClassNode('org.grails.datastore.gorm.transform.fixture.PartialGenericsWeaveTarget')
        ClassNode traitClassNode = ClassHelper.make(DoubleGenericTestTrait)
        ClassNode suppliedArgument = ClassHelper.make(String)

        when: 'only the first of the two declared generic arguments is supplied'
        AbstractTraitApplyingGormASTTransformation.weaveTraitWithGenerics(targetClassNode, DoubleGenericTestTrait, suppliedArgument)

        then: 'the supplied argument is used for the first slot and Object pads the second'
        targetClassNode.implementsInterface(traitClassNode)
        ClassNode wovenInterface = targetClassNode.interfaces.find { it.name == DoubleGenericTestTrait.name }
        wovenInterface.genericsTypes.length == 2
        wovenInterface.genericsTypes[0].type == ClassHelper.make(String)
        wovenInterface.genericsTypes[1].type == ClassHelper.OBJECT_TYPE
    }

    void "weaveTrait composes the trait onto the class via a real compilation unit"() {
        given:
        GroovyClassLoader classLoader = new GroovyClassLoader(getClass().classLoader)

        when:
        Class<?> compiled = classLoader.parseClass('''
            package org.grails.datastore.gorm.transform.fixture

            @org.grails.datastore.gorm.transform.ApplyTestTraitWeaving
            class WeavingTarget {
            }
        ''')

        then: 'the class implements the woven trait and the trait method was composed into it'
        TestWeavableTrait.isAssignableFrom(compiled)
        compiled.getDeclaredConstructor().newInstance().testTraitMarkerValue() == 'test-trait-woven'
    }
}
