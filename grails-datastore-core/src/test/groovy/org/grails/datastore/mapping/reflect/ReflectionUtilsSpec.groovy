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

import java.beans.PropertyDescriptor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.model.DatastoreConfigurationException

class ReflectionUtilsSpec extends Specification {

    void "primitive compatibility map is symmetric"() {
        expect:
        ReflectionUtils.PRIMITIVE_TYPE_COMPATIBLE_CLASSES[Integer] == int
        ReflectionUtils.PRIMITIVE_TYPE_COMPATIBLE_CLASSES[int] == Integer
        ReflectionUtils.PRIMITIVE_TYPE_COMPATIBLE_CLASSES[boolean] == Boolean
        ReflectionUtils.PRIMITIVE_TYPE_COMPATIBLE_CLASSES[Character] == char
        ReflectionUtils.PRIMITIVE_TYPE_COMPATIBLE_CLASSES.size() == 16
    }

    @Unroll
    void "isAssignableFrom(#left, #right) == #expected"() {
        expect:
        ReflectionUtils.isAssignableFrom(left, right) == expected

        where:
        left         | right   || expected
        Object       | String  || true
        String       | String  || true
        Integer      | int     || true
        int          | Integer || true
        Number       | int     || true
        Number       | Integer || true
        CharSequence | String  || true
        String       | Integer || false
        int          | long    || false
        Long         | int     || false
    }

    void "isAssignableFrom rejects null arguments"() {
        when:
        ReflectionUtils.isAssignableFrom(null, String)

        then:
        NullPointerException e = thrown()
        e.message == 'Left type is null!'

        when:
        ReflectionUtils.isAssignableFrom(String, null)

        then:
        e = thrown()
        e.message == 'Right type is null!'
    }

    void "instantiate creates an instance via the public no-arg constructor"() {
        expect:
        ReflectionUtils.instantiate(null) == null
        ReflectionUtils.instantiate(Simple) instanceof Simple
    }

    @Unroll
    void "instantiate wraps #cause.simpleName in InstantiationException for #type.simpleName"() {
        when:
        ReflectionUtils.instantiate(type)

        then:
        InstantiationException e = thrown()
        cause.isInstance(e.cause)
        e.message.startsWith(cause.name)

        where:
        type         | cause
        NoDefault    | NoSuchMethodException
        PrivateCtor  | NoSuchMethodException
        Abstract     | java.lang.InstantiationException
        Exploding    | InvocationTargetException
    }

    void "makeAccessible opens non-public fields and methods"() {
        given:
        Field hidden = Simple.getDeclaredField('hidden')
        Method secret = Simple.getDeclaredMethod('secret')
        Field open = Simple.getDeclaredField('open')

        when:
        ReflectionUtils.makeAccessible(hidden)
        ReflectionUtils.makeAccessible(secret)
        ReflectionUtils.makeAccessible(open)

        then:
        hidden.canAccess(new Simple())
        secret.canAccess(new Simple())
        open.canAccess(new Simple())
        !open.isAccessible()
    }

    void "getPropertiesOfType returns descriptors assignable to the requested type"() {
        when:
        PropertyDescriptor[] strings = ReflectionUtils.getPropertiesOfType(Bean, String)
        PropertyDescriptor[] numbers = ReflectionUtils.getPropertiesOfType(Bean, Integer)
        PropertyDescriptor[] anything = ReflectionUtils.getPropertiesOfType(Bean, Object)

        then:
        strings*.name.sort() == ['name', 'title']
        numbers*.name == ['count']
        anything.length == 0
        ReflectionUtils.getPropertiesOfType(null, String).length == 0
        ReflectionUtils.getPropertiesOfType(Bean, null).length == 0
    }

    @Unroll
    void "isGetter(#name, #args) == #expected"() {
        expect:
        ReflectionUtils.isGetter(name, args as Class[]) == expected

        where:
        name      | args     || expected
        'getName' | []       || true
        'isName'  | []       || true
        'getname' | []       || false
        'get'     | []       || false
        'is'      | []       || false
        'name'    | []       || false
        'getName' | [String] || false
        ''        | []       || false
        null      | []       || false
        'getName' | null     || false
    }

    @Unroll
    void "isSetter(#name, #args) == #expected"() {
        expect:
        ReflectionUtils.isSetter(name, args as Class[]) == expected

        where:
        name      | args             || expected
        'setName' | [String]         || true
        'setname' | [String]         || false
        'set'     | [String]         || false
        'setName' | []               || false
        'setName' | [String, String] || false
        'name'    | [String]         || false
        ''        | [String]         || false
        null      | [String]         || false
        'setName' | null             || false
    }

    void "forName loads a class without initialising it"() {
        expect:
        ReflectionUtils.forName(Simple.name, Simple.classLoader) == Simple
    }

    void "forName wraps ClassNotFoundException in DatastoreConfigurationException"() {
        when:
        ReflectionUtils.forName('does.not.Exist', Simple.classLoader)

        then:
        DatastoreConfigurationException e = thrown()
        e.message.startsWith('Class not found loading GORM: ')
        e.cause instanceof ClassNotFoundException
    }

    static class Simple {
        public String open
        private String hidden

        private String secret() {
            hidden
        }
    }

    static class NoDefault {
        NoDefault(String value) {
        }
    }

    static class PrivateCtor {
        private PrivateCtor() {
        }
    }

    static abstract class Abstract {
    }

    static class Exploding {
        Exploding() {
            throw new IllegalStateException('boom')
        }
    }

    static class Bean {
        String name
        String title
        Integer count
        Object anything
    }
}
