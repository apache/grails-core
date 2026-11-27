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

import java.lang.reflect.Proxy
import java.util.function.IntBinaryOperator

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeReference
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import spock.lang.Shared
import spock.lang.Specification

import grails.config.ConfigProperties
import grails.util.Environment
import org.grails.config.PropertySourcesConfig

/**
 * Covers what Groovy does reflectively on behalf of the framework's classes being possible in an
 * image: creating a Groovy enum's constants, and turning a closure into the interface a method takes.
 * Both happen even under static compilation, and an image does neither unless it was asked to.
 */
class BytecodeRuntimeHintsSpec extends Specification {

    @Shared
    RuntimeHints hints = new RuntimeHints()

    @Shared
    RuntimeHints hintsWithoutOptionalClasses = new RuntimeHints()

    void setupSpec() {
        new BytecodeRuntimeHints().registerHints(hints, getClass().classLoader)
        new BytecodeRuntimeHints().registerHints(hintsWithoutOptionalClasses,
                without(OptionalDependencyFixture, UnloadableFixture))
    }

    /** A loader that cannot load the given classes, as an application without an optional dependency cannot. */
    private ClassLoader without(Class<?>... absent) {
        Set<String> names = absent*.name as Set<String>
        new ClassLoader(getClass().classLoader) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name in names) {
                    throw new ClassNotFoundException(name)
                }
                super.loadClass(name, resolve)
            }
        }
    }

    void 'a Groovy enum can have its constants created'() {
        expect: 'each constant is created by choosing a constructor through Groovy'
            RuntimeHintsPredicates.reflection().onType(GroovyEnumFixture)
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS).test(hints)
    }

    void 'the environment can be initialized'() {
        expect: 'it is the first thing an application touches, so an image without it stops before Spring starts'
            RuntimeHintsPredicates.reflection().onType(Environment)
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS).test(hints)
    }

    void 'a class that is not an enum is not registered for its constructors'() {
        expect: 'what a scan of every class registers has to stay bounded to what needs it'
            !RuntimeHintsPredicates.reflection().onType(ClosureCoercionFixture)
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS).test(hints)
    }

    void 'an interface a closure is coerced to can be proxied'() {
        expect: 'the closure becomes the interface through a JDK proxy created as the call is made'
            RuntimeHintsPredicates.proxies().forInterfaces(IntBinaryOperator).test(hints)
    }

    void 'the interface #coercion is coerced to can be proxied'() {
        when: 'the fixture runs the coercion, which is the code the hint is for'
            def coerced = fixture.call()

        then: 'Groovy makes a JDK proxy for it as the coercion runs'
            Proxy.isProxyClass(coerced.getClass())
            coerced.apply(21) == 42

        and: 'the image is told to allow one, whatever the code declared the value as'
            RuntimeHintsPredicates.proxies().forInterfaces(target).test(hints)

        where:
            coercion                                        | fixture                                     | target
            'a closure literal in dynamic code'             | { DynamicCoercionFixture.closureLiteral() } | ClosureLiteralCoercion
            'a Closure variable in dynamic code'            | { DynamicCoercionFixture.typedClosure() }   | TypedClosureCoercion
            'a def variable holding a closure'              | { DynamicCoercionFixture.untypedClosure() } | UntypedClosureCoercion
            'a method result that is a closure'             | { DynamicCoercionFixture.closureResult() }  | ClosureResultCoercion
            'a map literal in dynamic code'                 | { DynamicCoercionFixture.mapLiteral() }     | MapLiteralCoercion
            'a closure literal in statically compiled code' | { StaticCoercionFixture.closureLiteral() }  | StaticClosureLiteralCoercion
            'a map literal in statically compiled code'     | { StaticCoercionFixture.mapLiteral() }      | StaticMapLiteralCoercion
    }

    void 'a sealed interface named with as is not registered for a proxy'() {
        given: 'one the JVM sees as sealed'
            assert SealedCoercion.isSealed()

        when: 'a value that already implements it is coerced to it'
            def coerced = SealedCoercionFixture.permitted()

        then: 'it needs no proxy, and the JDK refuses to make one for a sealed interface, so a hint is of no use'
            coerced instanceof PermittedCoercion
            !RuntimeHintsPredicates.proxies().forInterfaces(SealedCoercion).test(hints)
    }

    void 'the configuration can list its property names as properties'() {
        when: 'the names are asked for, which turns a map of closures into an enumeration'
            def names = new ConfigProperties(new PropertySourcesConfig('grails.fixture': 'value')).propertyNames()

        then: 'Groovy makes a JDK proxy for it, which the image is told to allow'
            Proxy.isProxyClass(names.getClass())
            RuntimeHintsPredicates.proxies().forInterfaces(Enumeration).test(hints)
    }

    void 'a trait can be initialized by the classes implementing it'() {
        given: 'the helpers Groovy writes for a trait with a field, by the names an image is told'
            def helper = TypeReference.of(GreetingTraitFixture.name + '$Trait$Helper')
            def fieldHelper = TypeReference.of(GreetingTraitFixture.name + '$Trait$FieldHelper')

        expect: 'an implementing class calls the helper from its constructor through a dynamic call'
            RuntimeHintsPredicates.reflection().onType(helper)
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_METHODS).test(hints)
            RuntimeHintsPredicates.reflection().onType(fieldHelper)
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_METHODS).test(hints)
    }

    void 'a trait helper is registered under the name its class has'() {
        when: 'the names an image is told are read back'
            def names = hints.reflection().typeHints().map { it.type.name }.toList()

        then: 'a name rebuilt from the helper class drops the Trait in the middle and names nothing'
            names.contains(GreetingTraitFixture.name + '$Trait$Helper')
            !names.contains(GreetingTraitFixture.name + '$Helper')
    }

    void 'the application class can be created'() {
        expect: 'it implements GrailsApplicationClass, and was the first class an image could not create'
            RuntimeHintsPredicates.reflection().onType(TypeReference.of('grails.core.GrailsApplicationClass$Trait$Helper'))
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_METHODS).test(hints)
    }

    void 'the members Spring injects or calls through an annotation can be reached'() {
        expect: 'a bean created by a supplier and declared as an interface is known only once it exists, so ' +
                'its members are found in its bytecode instead'
            RuntimeHintsPredicates.reflection().onFieldAccess(AnnotatedMembersFixture, 'collaborator').test(hints)
            RuntimeHintsPredicates.reflection().onFieldAccess(AnnotatedMembersFixture, 'setting').test(hints)
            RuntimeHintsPredicates.reflection().onMethodInvocation(AnnotatedMembersFixture, 'initialize').test(hints)
            RuntimeHintsPredicates.reflection().onMethodInvocation(AnnotatedMembersFixture, 'close').test(hints)
    }

    void 'a Groovy class can be asked about its methods'() {
        expect: 'a Groovy object given a property dynamically is first asked whether it overrides setProperty'
            RuntimeHintsPredicates.reflection().onType(ClosureCoercionFixture).test(hints)
    }

    void 'a class that is not a Groovy class is not registered for that'() {
        expect: 'a Java class in a scanned package, which no Groovy dispatch asks about'
            !RuntimeHintsPredicates.reflection().onType(CaffeineRuntimeHints).test(hints)
    }

    void 'a #kind whose bytecode names a class the application does not have is not registered'() {
        expect: 'it loads, since only a method body names the absent class, but registering it in any way -- ' +
                'as a type, or for its constructors, methods or fields -- would fail the image build, which ' +
                'parses what it is asked to keep'
            hintsWithoutOptionalClasses.reflection().typeHints().noneMatch { it.type.name == className }

        and: 'it is registered where the class is there'
            hints.reflection().typeHints().anyMatch { it.type.name == className }

        where:
            kind                                | className
            'Groovy enum'                       | OptionalDependencyEnumFixture.name
            'trait helper'                      | OptionalDependencyTraitFixture.name + '$Trait$Helper'
            'class with members Spring injects' | OptionalDependencyBeanFixture.name
    }

    void 'a class the scan finds but the application cannot load is not registered'() {
        expect: 'its class file is read like any other'
            hintsWithoutOptionalClasses.reflection().typeHints().noneMatch { it.type.name == UnloadableFixture.name }

        and: 'it is registered, as a type and for its constructors, where it loads'
            RuntimeHintsPredicates.reflection().onType(UnloadableFixture)
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS).test(hints)
    }

    void 'the coercion still works'() {
        expect: 'the fixture is the code the hint is for, so it has to do what it says'
            ClosureCoercionFixture.sum(4) == 6
    }

    void 'the trait still initializes'() {
        expect: 'the fixture is the code the hint is for, so it has to do what it says'
            new GreeterFixture().greeting == 'hello'
    }
}
