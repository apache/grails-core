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

package org.grails.datastore.gorm.support

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class BeforeValidateHelperTests {

    def beforeValidateHelper

    @BeforeEach
    void setUp() {
        beforeValidateHelper = new BeforeValidateHelper()
    }

    @Test
    void testNoArgBeforeValidate() {
        def obj = new ClassWithNoArgBeforeValidate()
        assertEquals 0, obj.noArgCounter, 'wrong initial counter value'
        beforeValidateHelper.invokeBeforeValidate(obj, null)
        assertEquals 1, obj.noArgCounter, 'wrong counter value'
        beforeValidateHelper.invokeBeforeValidate(obj, [])
        assertEquals 2, obj.noArgCounter, 'wrong counter value'
        beforeValidateHelper.invokeBeforeValidate(obj, ['name', 'age', 'town'])
        assertEquals 3, obj.noArgCounter, 'wrong counter value'
    }

    @Test
    void testListArgBeforeValidate() {
        def obj = new ClassWithListArgBeforeValidate()
        assertEquals 0, obj.listArgCounter, 'wrong initial counter value'
        beforeValidateHelper.invokeBeforeValidate(obj, null)
        assertEquals  1, obj.listArgCounter, 'wrong counter value'
        beforeValidateHelper.invokeBeforeValidate(obj, [])
        assertEquals 2, obj.listArgCounter, 'wrong counter value'
        beforeValidateHelper.invokeBeforeValidate(obj, ['name', 'age', 'town'])
        assertEquals 3, obj.listArgCounter, 'wrong counter value'
    }

    @Test
    void testOverloadedBeforeValidate() {
        def obj = new ClassWithOverloadedBeforeValidate()
        assertEquals 0, obj.listArgCounter, 'wrong initial list arg counter value'
        assertEquals 0, obj.noArgCounter, 'wrong initial no arg counter value'

        beforeValidateHelper.invokeBeforeValidate(obj, null)
        assertEquals 0, obj.listArgCounter, 'wrong list arg counter value'
        assertEquals 1, obj.noArgCounter, 'wrong no arg counter value'

        beforeValidateHelper.invokeBeforeValidate(obj, [])
        assertEquals 1, obj.listArgCounter, 'wrong list arg counter value'
        assertEquals 1, obj.noArgCounter, 'wrong no arg counter value'

        beforeValidateHelper.invokeBeforeValidate(obj, ['name', 'age', 'town'])
        assertEquals 2, obj.listArgCounter, 'wrong list arg counter value'
        assertEquals 1, obj.noArgCounter, 'wrong no arg counter value'
    }

    @Test
    void testSerialization() {
        // Make sure something is in the cache
        def obj = new ClassWithNoArgBeforeValidate()
        beforeValidateHelper.invokeBeforeValidate(obj, null)
        assertEquals 1, obj.noArgCounter, 'wrong counter value'

        // Serialize
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()
        ObjectOutputStream oos = new ObjectOutputStream(byteArrayOutputStream)
        oos.writeObject(beforeValidateHelper)
        oos.close()
        assertTrue byteArrayOutputStream.toByteArray().length > 0, 'class is serialized'

        // Deserialize
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray())
        ObjectInputStream ois = new ObjectInputStream(byteArrayInputStream)
        beforeValidateHelper = ois.readObject() as BeforeValidateHelper
        assertNotNull beforeValidateHelper, 'class is deserialized'

        // Ensure it still works
        beforeValidateHelper.invokeBeforeValidate(obj, null)
        assertEquals 2, obj.noArgCounter, 'wrong counter value'
    }
}

class ClassWithNoArgBeforeValidate {
    def noArgCounter = 0
    def beforeValidate() {
        ++noArgCounter
    }
}

class ClassWithListArgBeforeValidate {
    def listArgCounter = 0
    def beforeValidate(List properties) {
        ++listArgCounter
    }
}

class ClassWithOverloadedBeforeValidate {
    def noArgCounter = 0
    def listArgCounter = 0
    def beforeValidate() {
        ++noArgCounter
    }
    def beforeValidate(List properties) {
        ++listArgCounter
    }
}
