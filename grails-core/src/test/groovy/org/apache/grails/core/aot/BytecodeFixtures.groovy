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

import java.util.function.IntUnaryOperator
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
 * A closure turned into an interface with {@code as} in a dynamically compiled class, as a plugin's
 * {@code beanRegistrar()} often is. Groovy compiles it to a call to {@code asType}, not to a cast.
 * {@code IntUnaryOperator} because nothing in the framework coerces a closure to it, so finding it proves
 * this class was read.
 */
class DynamicCoercionFixture {

    static IntUnaryOperator doubling() {
        { int operand -> operand * 2 } as IntUnaryOperator
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

/** A class an application without an optional dependency does not have. */
class OptionalDependencyFixture {
}

/**
 * A Groovy enum naming {@link OptionalDependencyFixture} only in a method body, so it loads without it, as a
 * class compiled against an optional integration does.
 */
enum OptionalDependencyEnumFixture {
    ONLY

    Object dependency() {
        new OptionalDependencyFixture()
    }
}

/** A trait naming {@link OptionalDependencyFixture} in a method, which its helper then carries. */
trait OptionalDependencyTraitFixture {

    Object dependency() {
        new OptionalDependencyFixture()
    }
}

/** A bean with a member Spring injects, naming {@link OptionalDependencyFixture} only in a method body. */
class OptionalDependencyBeanFixture {

    @Autowired
    private Object collaborator

    Object dependency() {
        new OptionalDependencyFixture()
    }
}

/** A Groovy enum whose class file a scan finds, but which the application cannot load. */
enum UnloadableFixture {
    ONLY
}
