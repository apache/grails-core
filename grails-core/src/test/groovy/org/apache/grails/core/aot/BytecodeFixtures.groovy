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

import java.util.stream.IntStream

import groovy.transform.CompileStatic

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

/**
 * A Groovy enum, which creates its constants by choosing a constructor through Groovy. In a package
 * {@link BytecodeRuntimeHints} scans, as the framework's own are.
 */
enum GroovyEnumFixture {
    FIRST,
    SECOND
}

/**
 * A closure passed where a method takes an interface, which Groovy turns into that interface with a
 * JDK proxy even under static compilation. {@code IntBinaryOperator} because nothing in the framework
 * coerces a closure to it, so finding it proves this class was read.
 */
@CompileStatic
class ClosureCoercionFixture {

    static int sum(int upTo) {
        IntStream.range(0, upTo).reduce(0) { int left, int right -> left + right }
    }
}

/**
 * A trait with a field, so Groovy writes both a helper and a field helper for it, and a class implementing
 * it initializes it from its constructor through the helper.
 */
trait GreetingTraitFixture {

    String greeting = 'hello'
}

/** A class implementing the trait, which is what calls the helper. */
@CompileStatic
class GreeterFixture implements GreetingTraitFixture {
}

/**
 * A bean whose members Spring injects and calls through annotations, as the link generator's are, which a
 * scan finds whatever the bean was declared as.
 */
class AnnotatedMembersFixture {

    @Autowired
    private Object collaborator

    @Value('${fixture.setting:default}')
    String setting

    @PostConstruct
    void initialize() {
    }

    @PreDestroy
    void close() {
    }
}
