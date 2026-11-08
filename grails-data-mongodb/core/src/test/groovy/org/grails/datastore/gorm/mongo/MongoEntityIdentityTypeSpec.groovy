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
package org.grails.datastore.gorm.mongo

import org.bson.types.ObjectId

import spock.lang.Specification

import org.grails.compiler.gorm.GormEntityTransformation

/**
 * Verifies the type of the {@code id} a MongoDB domain class is given when it declares none of its own.
 *
 * <p>{@code Long} by default, as in every earlier release, and {@code String} where the build opted in
 * with {@code grails { gorm { defaultIdType = 'native' } }}, which reaches the compiler as
 * {@link GormEntityTransformation#DEFAULT_ID_TYPE_PROPERTY}. A domain class is compiled here the same
 * way an application's is, so the whole path is exercised: the {@code @Entity} transformation resolves
 * {@link MongoEntityTraitProvider} from the classpath and takes the identity type from it.</p>
 *
 * @since 8.0
 */
class MongoEntityIdentityTypeSpec extends Specification {

    void cleanup() {
        System.clearProperty(GormEntityTransformation.DEFAULT_ID_TYPE_PROPERTY)
    }

    void 'the MongoDB implementation states String as its identity type'() {
        expect: 'String, not ObjectId - a generated ObjectId is handed back in its hexadecimal form'
        new MongoEntityTraitProvider().defaultIdentityType == String
    }

    void 'a MongoDB domain class is given a Long id by default'() {
        when:
        Class<?> personClass = compilePerson()

        then:
        personClass.getDeclaredField('id').type == Long
    }

    void 'a MongoDB domain class is given a String id where the build asked for native identity types'() {
        given:
        nativeIdTypes()

        when:
        Class<?> personClass = compilePerson()

        then:
        personClass.getDeclaredField('id').type == String
    }

    void 'a domain class naming MongoDB with mapWith is given a String id'() {
        given:
        nativeIdTypes()

        when:
        Class<?> personClass = compilePerson('', "static mapWith = 'mongo'")

        then:
        personClass.getDeclaredField('id').type == String
    }

    void 'a domain class that declares a Long id keeps it'() {
        given:
        nativeIdTypes()

        when:
        Class<?> personClass = compilePerson('Long id')

        then:
        personClass.getDeclaredField('id').type == Long
    }

    void 'a domain class that declares an ObjectId id keeps it'() {
        given:
        nativeIdTypes()

        when:
        Class<?> personClass = compilePerson('org.bson.types.ObjectId id')

        then:
        personClass.getDeclaredField('id').type == ObjectId
    }

    void 'the version property stays a Long'() {
        given:
        nativeIdTypes()

        when:
        Class<?> personClass = compilePerson()

        then:
        personClass.getDeclaredField('version').type == Long
    }

    private static void nativeIdTypes() {
        System.setProperty(GormEntityTransformation.DEFAULT_ID_TYPE_PROPERTY,
                GormEntityTransformation.DEFAULT_ID_TYPE_NATIVE)
    }

    private static Class<?> compilePerson(String declaredId = '', String mapping = '') {
        new GroovyClassLoader().parseClass("""
            import grails.gorm.annotation.Entity

            @Entity
            class Person {
                $declaredId
                $mapping
                String name
            }
        """)
    }
}
