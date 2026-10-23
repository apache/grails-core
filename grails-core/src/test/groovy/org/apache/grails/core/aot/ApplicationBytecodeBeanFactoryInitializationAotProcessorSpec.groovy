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

import java.util.function.LongBinaryOperator

import org.springframework.aot.generate.ClassNameGenerator
import org.springframework.aot.generate.DefaultGenerationContext
import org.springframework.aot.generate.InMemoryGeneratedFiles
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeReference
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode
import org.springframework.context.support.GenericApplicationContext
import org.springframework.javapoet.ClassName
import spock.lang.Specification

import com.example.nativeapp.AuditedFixture
import com.example.nativeapp.AuditedOrderFixture
import com.example.nativeapp.NativeApplicationFixture
import com.example.nativeapp.OrderStatusFixture
import com.example.nativeapp.TotalsFixture

/**
 * Covers what Groovy does reflectively on behalf of an application's own classes being possible in an image.
 *
 * <p>The framework's packages are covered by {@link BytecodeRuntimeHints}; an application's are the
 * ones its application class names. Without this an application could not use a Groovy enum it declared.</p>
 */
class ApplicationBytecodeBeanFactoryInitializationAotProcessorSpec extends Specification {

    GenericApplicationContext context = new GenericApplicationContext()

    void cleanup() {
        context.close()
    }

    private RuntimeHints hints() {
        def contribution = new ApplicationBytecodeBeanFactoryInitializationAotProcessor()
                .processAheadOfTime(context.beanFactory)
        def generation = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get('com.example', 'Hints')), new InMemoryGeneratedFiles())
        contribution.applyTo(generation, Mock(BeanFactoryInitializationCode))
        generation.runtimeHints
    }

    void 'a Groovy enum the application declares can have its constants created'() {
        given: 'an application whose classes are in a package of its own'
            context.registerBean('application', NativeApplicationFixture)

        when:
            def hints = hints()

        then:
            RuntimeHintsPredicates.reflection().onType(OrderStatusFixture)
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS).test(hints)
    }

    void 'a trait the application declares can be initialized'() {
        given:
            context.registerBean('application', NativeApplicationFixture)

        when:
            def hints = hints()

        then:
            RuntimeHintsPredicates.reflection().onType(TypeReference.of(AuditedFixture.name + '$Trait$Helper'))
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_METHODS).test(hints)
    }

    void 'an interface the application coerces a closure to can be proxied'() {
        given:
            context.registerBean('application', NativeApplicationFixture)

        when:
            def hints = hints()

        then:
            RuntimeHintsPredicates.proxies().forInterfaces(LongBinaryOperator).test(hints)
    }

    void 'a context with no application class contributes nothing'() {
        expect: 'a plain Spring application being generated names no packages'
            new ApplicationBytecodeBeanFactoryInitializationAotProcessor().processAheadOfTime(context.beanFactory) == null
    }

    void 'the fixtures do what the hints are for'() {
        expect:
            OrderStatusFixture.values()*.name() == ['OPEN', 'CLOSED']
            new AuditedOrderFixture().auditedBy == 'system'
            TotalsFixture.total(4) == 6
    }
}
