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

import org.springframework.aot.generate.ClassNameGenerator
import org.springframework.aot.generate.DefaultGenerationContext
import org.springframework.aot.generate.InMemoryGeneratedFiles
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode
import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.support.GenericApplicationContext
import org.springframework.scheduling.annotation.ProxyAsyncConfiguration
import org.springframework.core.env.Environment
import org.springframework.javapoet.ClassName
import org.springframework.util.StopWatch
import spock.lang.Specification

/**
 * Covers the beans the framework declares being creatable and wirable in an image.
 *
 * <p>The plugins declare their beans as the application starts -- a registrar's are left out of the
 * generated code and declared again, and the bean DSL's are declared again at run time -- so Spring creates
 * and wires them reflectively, and an image built without these stopped at the first plugin bean declared
 * by class, and then at the first with an autowired setter.</p>
 */
class BeanReflectionBeanFactoryInitializationAotProcessorSpec extends Specification {

    GenericApplicationContext context = new GenericApplicationContext()

    void cleanup() {
        context.close()
    }

    private void register(BeanRegistrar registrar) {
        new BeanRegistryAdapter(context, context.beanFactory, context.environment, registrar.getClass()).register(registrar)
    }

    private RuntimeHints hints() {
        def contribution = new BeanReflectionBeanFactoryInitializationAotProcessor().processAheadOfTime(context.beanFactory)
        def generation = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get('com.example', 'Hints')), new InMemoryGeneratedFiles())
        contribution.applyTo(generation, Mock(BeanFactoryInitializationCode))
        generation.runtimeHints
    }

    private static boolean registered(RuntimeHints hints, Class<?> type, MemberCategory... categories) {
        RuntimeHintsPredicates.reflection().onType(type).withMemberCategories(categories).test(hints)
    }

    void 'a bean a registrar declares by class can be created and given its properties'() {
        given:
            register(new FixtureRegistrar())

        when:
            def hints = hints()

        then: 'it is created through its constructor and given its properties through its setters'
            registered(hints, DeclaredByClassFixture, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS)
    }

    void 'a bean a registrar declares with a supplier is wired but not created reflectively'() {
        given:
            register(new FixtureRegistrar())

        when:
            def hints = hints()

        then: 'the supplier creates it, and Spring still sets its properties afterwards'
            registered(hints, DeclaredWithSupplierFixture, MemberCategory.INVOKE_PUBLIC_METHODS)
            !registered(hints, DeclaredWithSupplierFixture, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
    }

    void 'a bean declared at run time by other means is covered as well'() {
        given: 'a bean declared by class outside any registrar, as the bean DSL declares one'
            context.registerBean('declaredByDsl', DeclaredByClassFixture)

        when:
            def hints = hints()

        then:
            registered(hints, DeclaredByClassFixture, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS)
    }

    void 'a bean declared by the name of its class is covered too'() {
        given: 'a definition holding only the class name, as the bean DSL often declares one'
            context.registerBeanDefinition('byName', new GenericBeanDefinition(beanClassName: DeclaredByClassFixture.name))

        when:
            def hints = hints()

        then:
            registered(hints, DeclaredByClassFixture, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS)
    }

    void 'the members Spring injects through an annotation can be reached, however visible they are'() {
        given:
            context.registerBean('injected', InjectedFixture)

        when:
            def hints = hints()

        then: 'the post-processor that injects them does so reflectively'
            RuntimeHintsPredicates.reflection().onMethodInvocation(InjectedFixture, 'setEnvironment').test(hints)
            RuntimeHintsPredicates.reflection().onFieldAccess(InjectedFixture, 'context').test(hints)
            RuntimeHintsPredicates.reflection().onFieldAccess(InjectedFixture, 'name').test(hints)
    }

    void 'a Spring bean has only its injection points reached'() {
        given: 'a Spring bean with an autowired method, which the processors registered again under AOT inject too'
            context.registerBean('async', ProxyAsyncConfiguration)
            context.registerBean('ordinary', DeclaredByClassFixture)

        when:
            def hints = hints()

        then: 'the generated code creates it, so its constructors are not registered'
            RuntimeHintsPredicates.reflection().onMethodInvocation(ProxyAsyncConfiguration, 'setConfigurers').test(hints)
            !registered(hints, ProxyAsyncConfiguration, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
    }

    void 'a Spring class a registrar declares by class can be created'() {
        given: 'a registrar bean is left out of the generated code, so Spring creates it as the registrar declared it'
            register(new SpringClassRegistrar())

        when:
            def hints = hints()

        then:
            registered(hints, StopWatch, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS)
    }

    void 'the product of a factory method has its injection points reached'() {
        given: 'a bean a factory method creates'
            context.registerBeanDefinition('produced', new RootBeanDefinition(FactoryFixture).tap {
                factoryMethodName = 'create'
            })

        when:
            def hints = hints()

        then: 'it is injected after the method returns it'
            RuntimeHintsPredicates.reflection().onFieldAccess(InjectedFixture, 'context').test(hints)
            !registered(hints, InjectedFixture, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
    }

    void 'a context with no beans contributes nothing'() {
        expect:
            new BeanReflectionBeanFactoryInitializationAotProcessor().processAheadOfTime(context.beanFactory) == null
    }

    static class FixtureRegistrar implements BeanRegistrar {

        @Override
        void register(BeanRegistry registry, Environment environment) {
            registry.registerBean('byClass', DeclaredByClassFixture)
            registry.registerBean('withSupplier', DeclaredWithSupplierFixture) { BeanRegistry.Spec<DeclaredWithSupplierFixture> spec ->
                spec.supplier { new DeclaredWithSupplierFixture() }
            }
        }
    }

    static class SpringClassRegistrar implements BeanRegistrar {

        @Override
        void register(BeanRegistry registry, Environment environment) {
            registry.registerBean('watch', StopWatch)
        }
    }

    static class DeclaredByClassFixture {

        String name
    }

    static class DeclaredWithSupplierFixture {

        String name
    }

    static class FactoryFixture {

        static InjectedFixture create() {
            new InjectedFixture()
        }
    }

    static class InjectedFixture {

        @Autowired
        private GenericApplicationContext context

        @Value('${fixture.name:fixture}')
        private String name

        private Environment environment

        @Autowired
        protected void setEnvironment(Environment environment) {
            this.environment = environment
        }
    }
}
