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

import java.lang.reflect.Field

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalListener
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import spock.lang.Shared
import spock.lang.Specification

/**
 * Covers the classes the framework's caches are built from being creatable in an image.
 *
 * <p>The names are Caffeine's, so each set of options the framework builds a cache with is built here and
 * the classes Caffeine chose for it are read back: a Caffeine upgrade that renames them fails this rather
 * than an image.</p>
 */
class CaffeineRuntimeHintsSpec extends Specification {

    @Shared
    RuntimeHints hints = new RuntimeHints()

    void setupSpec() {
        new CaffeineRuntimeHints().registerHints(hints, getClass().classLoader)
    }

    void 'a cache built #options is built from classes that can be created'() {
        when: 'a cache is built the way the framework builds one'
            def classes = classesOf(cache)

        then: 'both the cache class and the class of its entries are registered'
            classes.every {
                RuntimeHintsPredicates.reflection().onType(it)
                        .withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.ACCESS_DECLARED_FIELDS)
                        .test(hints)
            }

        where:
            options                                     | cache
            'with a bound on the number of entries'     | Caffeine.newBuilder().maximumSize(10).build()
            'with a bound on their weight'              | Caffeine.newBuilder().maximumWeight(10).weigher { k, v -> 1 }.build()
            'with weak keys'                            | Caffeine.newBuilder().weakKeys().build()
            'with a bound and a removal listener'       | Caffeine.newBuilder().removalListener({ k, v, cause -> } as RemovalListener)
                                                                  .executor(Runnable::run).maximumSize(10).build()
    }

    /** The cache class Caffeine created for the cache, and the class of the entries it holds. */
    private static List<Class<?>> classesOf(Cache cache) {
        Object local = read(cache, 'cache')
        [local.getClass(), read(local, 'nodeFactory').getClass()]
    }

    private static Object read(Object target, String name) {
        Class<?> type = target.getClass()
        while (type != null) {
            Field field = type.declaredFields.find { it.name == name }
            if (field != null) {
                field.accessible = true
                return field.get(target)
            }
            type = type.superclass
        }
        throw new IllegalStateException("No field '${name}' in ${target.getClass()}")
    }
}
