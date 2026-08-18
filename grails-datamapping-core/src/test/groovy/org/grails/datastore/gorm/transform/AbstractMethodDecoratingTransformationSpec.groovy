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

import org.springframework.util.ReflectionUtils

import spock.lang.Specification

/**
 * {@code AbstractMethodDecoratingTransformation}'s method-selection logic in {@code weaveClassNode} -
 * which methods a decorating transform like {@code @Transactional} actually gets applied to - is
 * normally exercised indirectly and incompletely through whichever fixtures the real transforms'
 * specs happen to declare. This spec drives it directly, through a real compilation, using
 * {@link TestMethodDecoratingTransformation} - a pass-through decorator with no side effects of its
 * own - so each inclusion/exclusion branch can be asserted on independently of any particular real
 * transform's semantics.
 */
class AbstractMethodDecoratingTransformationSpec extends Specification {

    private static Class<?> compile(String source) {
        new GroovyClassLoader().parseClass(source)
    }

    void "a plain public instance method is renamed and re-dispatched to"() {
        when:
        Class<?> target = compile('''
            package org.grails.datastore.gorm.transform.fixture

            @org.grails.datastore.gorm.transform.ApplyTestMethodDecorating
            class PlainMethodTarget {
                String updateFoo() { 'original' }
            }
        ''')

        then:
        ReflectionUtils.findMethod(target, '$test__updateFoo') != null
        target.getDeclaredConstructor().newInstance().updateFoo() == 'original'
    }

    void "static, private and abstract methods are never woven"() {
        when:
        Class<?> concrete = compile('''
            package org.grails.datastore.gorm.transform.fixture

            @org.grails.datastore.gorm.transform.ApplyTestMethodDecorating
            class StaticAndPrivateMethodsTarget {
                static void staticMethod() { }
                private void privateMethod() { }
            }
        ''')
        Class<?> abstractTarget = compile('''
            package org.grails.datastore.gorm.transform.fixture

            @org.grails.datastore.gorm.transform.ApplyTestMethodDecorating
            abstract class AbstractMethodTarget {
                abstract void abstractMethod()
            }
        ''')

        then:
        ReflectionUtils.findMethod(concrete, '$test__staticMethod') == null
        ReflectionUtils.findMethod(concrete, '$test__privateMethod', String) == null
        concrete.declaredMethods.every { !it.name.contains('$test__privateMethod') }
        ReflectionUtils.findMethod(abstractTarget, '$test__abstractMethod') == null
    }

    void "METHOD_NAME_EXCLUDES keeps lifecycle method names such as afterPropertiesSet and destroy unwoven"() {
        when:
        Class<?> target = compile('''
            package org.grails.datastore.gorm.transform.fixture

            @org.grails.datastore.gorm.transform.ApplyTestMethodDecorating
            class LifecycleMethodTarget {
                void afterPropertiesSet() { }
                void destroy() { }
            }
        ''')

        then:
        ReflectionUtils.findMethod(target, '$test__afterPropertiesSet') == null
        ReflectionUtils.findMethod(target, '$test__destroy') == null
    }

    void "setters are never woven and a getter is only woven when it has no matching setter"() {
        when:
        Class<?> target = compile('''
            package org.grails.datastore.gorm.transform.fixture

            @org.grails.datastore.gorm.transform.ApplyTestMethodDecorating
            class GetterSetterTarget {
                String name
                String getAge() { 'ageless' }
            }
        ''')

        then: 'the setter itself is never a weaving candidate'
        ReflectionUtils.findMethod(target, '$test__setName', String) == null

        and: 'the getter that has a matching setter is skipped'
        ReflectionUtils.findMethod(target, '$test__getName') == null

        and: 'the getter with no matching setter is woven'
        ReflectionUtils.findMethod(target, '$test__getAge') != null
    }

    void "a dollar-prefixed method name that is not a spock feature method is skipped"() {
        when:
        Class<?> target = compile('''
            package org.grails.datastore.gorm.transform.fixture

            @org.grails.datastore.gorm.transform.ApplyTestMethodDecorating
            class DollarMethodTarget {
                void $rawMethod() { }
            }
        ''')

        then:
        ReflectionUtils.findMethod(target, '$test__$rawMethod') == null
    }

    void "hasExcludedAnnotation skips methods annotated with PostConstruct"() {
        when:
        Class<?> target = compile('''
            package org.grails.datastore.gorm.transform.fixture

            @org.grails.datastore.gorm.transform.ApplyTestMethodDecorating
            class PostConstructTarget {
                @jakarta.annotation.PostConstruct
                void init() { }
            }
        ''')

        then:
        ReflectionUtils.findMethod(target, '$test__init') == null
    }

    void "spock setup and cleanup are routed to weaveTestSetupMethod instead of being renamed"() {
        when:
        Class<?> target = compile('''
            package org.grails.datastore.gorm.transform.fixture

            @org.grails.datastore.gorm.transform.ApplyTestMethodDecorating
            class SpockSetupCleanupTarget extends spock.lang.Specification {
                def setup() { }
                def cleanup() { }
            }
        ''')

        then:
        ReflectionUtils.findMethod(target, '$test__setup') == null
        ReflectionUtils.findMethod(target, '$test__cleanup') == null
    }

    void "a JUnit-annotated method is routed to weaveTestSetupMethod instead of being renamed"() {
        when:
        Class<?> target = compile('''
            package org.grails.datastore.gorm.transform.fixture

            @org.grails.datastore.gorm.transform.ApplyTestMethodDecorating
            class JunitAnnotatedTarget {
                @org.junit.jupiter.api.BeforeEach
                void junitSetup() { }
            }
        ''')

        then:
        ReflectionUtils.findMethod(target, '$test__junitSetup') == null
    }

    void "an overriding method is renamed with a class-qualified prefix while the parent's own method uses the plain prefix"() {
        when: 'evaluating the script (rather than parsing a single class) returns both declared types, in order'
        List<Class<?>> types = new GroovyShell().evaluate('''
            package org.grails.datastore.gorm.transform.fixture

            @org.grails.datastore.gorm.transform.ApplyTestMethodDecorating
            class OverrideParentTarget {
                String sound() { 'parent' }
            }

            @org.grails.datastore.gorm.transform.ApplyTestMethodDecorating
            class OverrideChildTarget extends OverrideParentTarget {
                @Override
                String sound() { 'child' }
            }

            [OverrideParentTarget, OverrideChildTarget]
        ''') as List<Class<?>>
        Class<?> parent = types[0]
        Class<?> child = types[1]

        then: 'the parent method - not an override - uses the plain renamed prefix'
        ReflectionUtils.findMethod(parent, '$test__sound') != null

        and: 'the overriding method is renamed with the decapitalized declaring class name mixed in'
        ReflectionUtils.findMethod(child, '$test__overrideChildTarget_sound') != null

        and: 'the child does not declare its own plain-prefixed renamed method (only inherits the parent one)'
        child.declaredMethods.every { it.name != '$test__sound' }

        and: 'the woven methods still dispatch correctly'
        parent.getDeclaredConstructor().newInstance().sound() == 'parent'
        child.getDeclaredConstructor().newInstance().sound() == 'child'
    }
}
