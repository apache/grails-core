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
package org.apache.grails.core.aot

import java.lang.reflect.Method

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.InstanceSupplier
import org.springframework.beans.factory.support.RegisteredBean
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.cglib.proxy.Enhancer
import org.springframework.cglib.proxy.NoOp
import spock.lang.Specification

/**
 * Covers what the generated code marks on the definitions it contributes, and what is told from the marks:
 * which instances that code created, and which of their members it injected.
 */
class GeneratedBeanDefinitionsSpec extends Specification {

    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory()

    /** A definition as the generated code contributes one, naming the given type. */
    private static RootBeanDefinition generated(Class<?> beanClass, Class<?> declaredType = beanClass) {
        new RootBeanDefinition(beanClass).tap {
            instanceSupplier = { beanClass.getDeclaredConstructor().newInstance() }
            GeneratedBeanDefinitions.markGenerated(it, declaredType.name)
        }
    }

    void 'a generated definition names the type its code injects'() {
        expect:
            generated(SubclassFixture, SuperclassFixture)
                    .getAttribute(GeneratedBeanDefinitions.DECLARED_TYPE_ATTRIBUTE) == SuperclassFixture.name
    }

    void 'the instance the generated supplier creates is told from another instance of the bean'() {
        given:
            beanFactory.registerBeanDefinition('generated', generated(SuperclassFixture))

        when:
            def created = beanFactory.getBean('generated')

        then:
            !GeneratedBeanDefinitions.isAnotherInstanceOfGeneratedBean(beanFactory, 'generated', created)
            GeneratedBeanDefinitions.isAnotherInstanceOfGeneratedBean(beanFactory, 'generated', new SuperclassFixture())
    }

    void 'instances are told apart by identity, not by their own equality'() {
        given: 'a bean every instance of which equals every other'
            beanFactory.registerBeanDefinition('generated', generated(EqualFixture))

        when:
            def created = beanFactory.getBean('generated')
            def another = new EqualFixture()

        then:
            created == another
            !GeneratedBeanDefinitions.isAnotherInstanceOfGeneratedBean(beanFactory, 'generated', created)
            GeneratedBeanDefinitions.isAnotherInstanceOfGeneratedBean(beanFactory, 'generated', another)
    }

    void 'the factory method of the supplier the generated code gave the definition is kept'() {
        given: 'a supplier as the generated code writes for a factory method'
            Method factoryMethod = FactoryFixture.getMethod('create')
            def definition = new RootBeanDefinition(SuperclassFixture)
            definition.instanceSupplier = new InstanceSupplier<SuperclassFixture>() {
                @Override
                SuperclassFixture get(RegisteredBean registeredBean) {
                    FactoryFixture.create()
                }

                @Override
                Method getFactoryMethod() {
                    factoryMethod
                }
            }

        when:
            GeneratedBeanDefinitions.markGenerated(definition, SuperclassFixture.name)
            beanFactory.registerBeanDefinition('generated', definition)

        then: 'the supplier that records what it creates answers with it, as the container asks it to'
            ((InstanceSupplier) definition.instanceSupplier).factoryMethod == factoryMethod

        and:
            !GeneratedBeanDefinitions.isAnotherInstanceOfGeneratedBean(beanFactory, 'generated',
                    beanFactory.getBean('generated'))
    }

    void 'no instance is another instance of a bean #reason'() {
        given:
            beanFactory.registerBeanDefinition('declared', new RootBeanDefinition(SuperclassFixture))

        expect:
            !GeneratedBeanDefinitions.isAnotherInstanceOfGeneratedBean(factory(beanFactory), name, new SuperclassFixture())

        where:
            reason                           | name       | factory
            'declared any other way'         | 'declared' | { it }
            'nothing declares'               | 'missing'  | { it }
            'outside a listable bean factory' | 'declared' | { null }
    }

    void 'every member of the declared type is marked for an instance #ofWhat'() {
        given:
            def definition = generated(SuperclassFixture)

        when:
            GeneratedBeanDefinitions.markMembersInjectedByGeneratedCode(definition, beanType,
                    new AutowiredAnnotationBeanPostProcessor())

        then:
            definition.externallyManagedConfigMembers == [
                    SuperclassFixture.getDeclaredField('collaborator'),
                    SuperclassFixture.getMethod('setOverridable', String)
            ] as Set

        where:
            ofWhat                                         | beanType
            'of that type'                                 | SuperclassFixture
            'of the subclass Spring generates for a class' | new Enhancer(superclass: SuperclassFixture, callback: NoOp.INSTANCE).create().getClass()
    }

    void 'the members the generated code injected are marked for an instance of a subclass'() {
        given: 'a factory method product declared as a superclass of the class it returns'
            def definition = generated(SubclassFixture, SuperclassFixture)

        when:
            GeneratedBeanDefinitions.markMembersInjectedByGeneratedCode(definition, SubclassFixture,
                    new AutowiredAnnotationBeanPostProcessor())

        then: 'those of the superclass, and the override of one that the generated code called, not what only the subclass declares'
            definition.externallyManagedConfigMembers == [
                    SuperclassFixture.getDeclaredField('collaborator'),
                    SuperclassFixture.getMethod('setOverridable', String),
                    SubclassFixture.getMethod('setOverridable', String)
            ] as Set
    }

    void 'the override of a generic superclass method is marked, not the bridge to it'() {
        given: 'a factory method product declared as a generic superclass, whose subclass overrides its setter'
            def definition = generated(GenericSubclassFixture, GenericSuperclassFixture)

        expect: 'the subclass has a bridge taking the erased type, beside the override taking the actual one'
            GenericSubclassFixture.getMethod('setDependency', Object).bridge

        when:
            GeneratedBeanDefinitions.markMembersInjectedByGeneratedCode(definition, GenericSubclassFixture,
                    new AutowiredAnnotationBeanPostProcessor())

        then: 'the generated code called the override through the bridge, and the override is what a processor injects'
            definition.externallyManagedConfigMembers == [
                    GenericSuperclassFixture.getMethod('setDependency', Object),
                    GenericSubclassFixture.getMethod('setDependency', String)
            ] as Set
    }

    void 'nothing is marked when #reason'() {
        when:
            GeneratedBeanDefinitions.markMembersInjectedByGeneratedCode(definition, beanType,
                    new AutowiredAnnotationBeanPostProcessor())

        then:
            definition.externallyManagedConfigMembers.empty

        where:
            reason                                       | definition                                 | beanType
            'the definition was declared any other way'  | new RootBeanDefinition(SuperclassFixture) | SuperclassFixture
            'the bean is not of the type it names at all' | generated(SuperclassFixture)               | ConfigurationFixture
    }

    static class ConfigurationFixture {
    }

    static class SuperclassFixture {

        @Autowired
        protected String collaborator

        @Autowired
        void setOverridable(String collaborator) {
        }
    }

    static class SubclassFixture extends SuperclassFixture {

        @Override
        @Autowired
        void setOverridable(String collaborator) {
        }

        @Autowired
        void setOwn(String collaborator) {
        }
    }

    static class GenericSuperclassFixture<T> {

        @Autowired
        void setDependency(T dependency) {
        }
    }

    static class GenericSubclassFixture extends GenericSuperclassFixture<String> {

        @Override
        @Autowired
        void setDependency(String dependency) {
        }
    }

    /** Equal to every other instance, as a bean whose equality changes as it is injected may be. */
    static class EqualFixture {

        @Override
        boolean equals(Object other) {
            other instanceof EqualFixture
        }

        @Override
        int hashCode() {
            1
        }
    }

    static class FactoryFixture {

        static SuperclassFixture create() {
            new SuperclassFixture()
        }
    }
}
