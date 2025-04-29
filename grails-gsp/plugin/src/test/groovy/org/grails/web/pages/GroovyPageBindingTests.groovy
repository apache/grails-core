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
package org.grails.web.pages

import org.grails.gsp.GroovyPageBinding
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 * @author Graeme Rocher
 * @since 1.1
 */
class GroovyPageBindingTests {

    @Test
    void testGroovyPageBinding() {
        def binding = new GroovyPageBinding()

        binding.foo = "bar"
        assertEquals "bar", binding.foo
        assertEquals([foo:'bar'], binding.variables)
        assertEquals binding.getMetaClass(), binding.metaClass
    }

    @Test
    void testVariables() {
        def parentBinding = new GroovyPageBinding()
        parentBinding.a = 1
        parentBinding.b = 2
        def binding = new GroovyPageBinding(parentBinding)
        binding.c = 3
        binding.d = 4
        def shouldbe=[a:1,b:2,c:3,d:4]
        assertEquals(shouldbe, binding.getVariables())
        def copied=[:]
        for (e in binding.getVariables().entrySet()) {
            copied.put(e.key, e.value)
        }
        assertEquals(shouldbe, copied)
    }
}
