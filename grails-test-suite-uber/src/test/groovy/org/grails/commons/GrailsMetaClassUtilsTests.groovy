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
package org.grails.commons

import grails.util.GrailsMetaClassUtils
import org.junit.jupiter.api.Test
import org.springframework.beans.BeanUtils

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull

/**
 * Tests for the GrailsMetaClassUtils class.
 *
 * @author Graeme Rocher
 */
class GrailsMetaClassUtilsTests {

    @Test
    void testGetMetaRegistry() {
        assertNotNull(GrailsMetaClassUtils.getRegistry())
    }

    @Test
    void testCopyExpandoMetaClass() {
        def metaClass = new ExpandoMetaClass(Dummy, true)

        // add property
        metaClass.getFoo = {-> "bar" }
        // add instance method
        metaClass.foo = { String txt -> "bar:$txt".toString() }
        // add static method
        metaClass.'static'.bar = {-> "foo" }
        // add constructor
        metaClass.constructor = { String txt ->
            def obj = BeanUtils.instantiateClass(Dummy)
            obj.name = txt
            obj
        }

        metaClass.initialize()

        def d = new Dummy("foo")
        assertEquals "foo", d.name
        assertEquals "bar", d.foo
        assertEquals "bar", d.getFoo()
        assertEquals "bar:1", d.foo("1")
        assertEquals "foo", Dummy.bar()

        GrailsMetaClassUtils.copyExpandoMetaClass(Dummy, Dummy2, false)

        d = new Dummy2("foo")
        assertEquals "foo", d.name
        assertEquals "bar", d.foo
        assertEquals "bar", d.getFoo()
        assertEquals "bar:1", d.foo("1")
        assertEquals "foo", Dummy.bar()
    }
}

class Dummy {
    String name
}

class Dummy2 {
    String name
}
