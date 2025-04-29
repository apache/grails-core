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

package grails.plugin.cache.util

import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Jeff Brown
 */
class ClassUtilsSpec extends Specification {

    @Unroll("#fieldOrPropertyName value should have been #expectedValue")
    def 'Test retrieving field or property value'() {
        given:
        def obj = new SomeGroovyClass()

        expect:
        expectedValue == ClassUtils.getPropertyOrFieldValue(obj, fieldOrPropertyName)

        where:
        fieldOrPropertyName        | expectedValue
        'someProperty'             | 1
        'publicField'              | 2
        'privateField'             | 3
        'propertyWithPrivateField' | 100
    }
}

class SomeGroovyClass {

    public publicField = 2
    private int privateField = 3
    private propertyWithPrivateField = 4
    def getSomeProperty() {
        1
    }

    def getPropertyWithPrivateField() {
        100
    }
}
