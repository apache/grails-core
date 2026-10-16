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
package org.grails.datastore.mapping.reflect

import spock.lang.Specification
import spock.lang.Unroll

class ClassUtilsSpec extends Specification {

    void "primitive compatibility map is symmetric"() {
        expect:
        ClassUtils.PRIMITIVE_TYPE_COMPATIBLE_CLASSES[Long] == long
        ClassUtils.PRIMITIVE_TYPE_COMPATIBLE_CLASSES[long] == Long
        ClassUtils.PRIMITIVE_TYPE_COMPATIBLE_CLASSES.size() == 16
    }

    void "isPresent reports whether a class can be loaded"() {
        expect:
        ClassUtils.isPresent('java.lang.String')
        !ClassUtils.isPresent('does.not.Exist')
        ClassUtils.isPresent('java.lang.String', ClassUtils.classLoader)
        !ClassUtils.isPresent('does.not.Exist', ClassUtils.classLoader)
        !ClassUtils.isPresent('does.not.Exist', null)
    }

    @Unroll
    void "isAssignableOrConvertibleFrom(#clazz, #type) == #expected"() {
        expect:
        ClassUtils.isAssignableOrConvertibleFrom(clazz, type) == expected

        where:
        clazz      | type     || expected
        null       | String   || false
        String     | null     || false
        Integer    | int      || true
        Number     | int      || true
        String     | int      || false
        Iterable   | String[] || true
        Collection | String[] || true
        String     | String[] || false
        Object     | String   || true
        String     | Object   || false
    }

    @Unroll
    void "getBooleanFromMap(#key, #map) == #expected"() {
        expect:
        ClassUtils.getBooleanFromMap(key, map) == expected

        where:
        key   | map               || expected
        'a'   | null              || false
        'a'   | [:]               || false
        'a'   | [a: null]         || false
        'a'   | [a: true]         || true
        'a'   | [a: false]        || false
        'a'   | [a: 'true']       || true
        'a'   | [a: 'TRUE']       || true
        'a'   | [a: 'yes']        || false
        'a'   | [a: 1]            || false
    }

    void "isClassBelowPackage matches on package prefixes and skips null entries"() {
        expect:
        ClassUtils.isClassBelowPackage(ClassUtilsSpec, ['org.grails.datastore'])
        ClassUtils.isClassBelowPackage(ClassUtilsSpec, [null, 'org.grails.datastore.mapping.reflect'])
        !ClassUtils.isClassBelowPackage(ClassUtilsSpec, ['com.example'])
        !ClassUtils.isClassBelowPackage(ClassUtilsSpec, [])
        !ClassUtils.isClassBelowPackage(ClassUtilsSpec, [null])
    }

    void "isMultiTenant detects an interface named MultiTenant anywhere in the hierarchy"() {
        expect:
        ClassUtils.isMultiTenant(Tenanted)
        ClassUtils.isMultiTenant(TenantedChild)
        !ClassUtils.isMultiTenant(Plain)
        !ClassUtils.isMultiTenant(String)
    }

    static interface MultiTenant {
    }

    static class Tenanted implements MultiTenant {
    }

    static class TenantedChild extends Tenanted {
    }

    static class Plain {
    }
}
