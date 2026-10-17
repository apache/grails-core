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

import java.util.function.IntBinaryOperator

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeReference
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import spock.lang.Shared
import spock.lang.Specification

import grails.util.Environment

/**
 * Covers what Groovy does reflectively on behalf of the framework's classes being possible in an
 * image: creating a Groovy enum's constants, and turning a closure into the interface a method takes.
 * Both happen even under static compilation, and an image does neither unless it was asked to.
 */
class BytecodeRuntimeHintsSpec extends Specification {

    @Shared
    RuntimeHints hints = new RuntimeHints()

    void setupSpec() {
        new BytecodeRuntimeHints().registerHints(hints, getClass().classLoader)
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

    void 'the coercion still works'() {
        expect: 'the fixture is the code the hint is for, so it has to do what it says'
            ClosureCoercionFixture.sum(4) == 6
    }

    void 'the trait still initializes'() {
        expect: 'the fixture is the code the hint is for, so it has to do what it says'
            new GreeterFixture().greeting == 'hello'
    }
}
