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

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import spock.lang.Shared
import spock.lang.Specification

/**
 * Covers the JDK types dynamically compiled framework code calls being callable in an image, where Groovy
 * resolves each call through a metaclass and makes it reflectively.
 */
class JdkDispatchRuntimeHintsSpec extends Specification {

    @Shared
    RuntimeHints hints = new RuntimeHints()

    void setupSpec() {
        new JdkDispatchRuntimeHints().registerHints(hints, getClass().classLoader)
    }

    void '#type.simpleName can be called'() {
        expect:
            RuntimeHintsPredicates.reflection().onType(type).withMemberCategories(MemberCategory.INVOKE_PUBLIC_METHODS).test(hints)

        where: 'the types a descriptor or the bean DSL was seen calling methods on as an application started'
            type << [Map, Map.Entry, String, Number, Boolean, Enum, Object, System, Collections, Arrays, Objects, File]
    }

    void 'a constant can be read'() {
        expect: 'reading File.separator in dynamically compiled code is a reflective field read'
            RuntimeHintsPredicates.reflection().onFieldAccess(File, 'separator').test(hints)
    }

    void '#type.simpleName can be created'() {
        expect: 'a new in dynamically compiled code is a call Groovy resolves too'
            RuntimeHintsPredicates.reflection().onType(type).withMemberCategories(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS).test(hints)

        where:
            type << [ArrayList, LinkedHashMap, HashSet, TreeMap, BigDecimal, BigInteger]
    }
}
