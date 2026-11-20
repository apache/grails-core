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
package org.grails.compiler.gorm

import spock.lang.Specification

/**
 * Verifies the type of the {@code id} {@link GormEntityTransformation} adds to an entity that declares
 * none of its own, for a compilation classpath that carries no {@link GormEntityTraitProvider} at all.
 *
 * <p>Such an entity has no GORM implementation to ask, so it is given a {@code Long} id whatever
 * {@link GormEntityTransformation#DEFAULT_ID_TYPE_PROPERTY} says. The type an implementation supplies
 * for itself is covered where that implementation is on the classpath - see
 * {@code MongoEntityIdentityTypeSpec} in GORM for MongoDB.</p>
 *
 * <p>Also pins the name of that property to its literal value. The Grails Gradle plugin publishes the
 * same name from a constant of its own, in a separate Gradle build that cannot reference this one, so
 * nothing but a test on each side stops the two drifting apart - and a build that publishes a name the
 * compiler no longer reads changes no behaviour visibly, it just quietly stops working. The Gradle side
 * of the pair is {@code GormDefaultIdTypeFunctionalSpec}, which asserts the same literal.</p>
 *
 * @since 8.0
 */
class GormEntityIdentityTypeSpec extends Specification {

    void cleanup() {
        System.clearProperty(GormEntityTransformation.DEFAULT_ID_TYPE_PROPERTY)
    }

    void 'the property name is the one the Grails Gradle plugin publishes'() {
        expect: 'the literal, not the constant - this is a wire contract, not a tautology'
        GormEntityTransformation.DEFAULT_ID_TYPE_PROPERTY == 'grails.gorm.defaultIdType'
        GormEntityTransformation.DEFAULT_ID_TYPE_LONG == 'long'
        GormEntityTransformation.DEFAULT_ID_TYPE_NATIVE == 'native'
    }

    void 'an entity that declares no id is given a Long id'() {
        when:
        Class<?> bookClass = compileBook()

        then:
        bookClass.getDeclaredField('id').type == Long
    }

    void 'an entity is given a Long id where no GORM implementation is on the classpath to ask'() {
        given:
        System.setProperty(GormEntityTransformation.DEFAULT_ID_TYPE_PROPERTY,
                GormEntityTransformation.DEFAULT_ID_TYPE_NATIVE)

        when:
        Class<?> bookClass = compileBook()

        then:
        bookClass.getDeclaredField('id').type == Long
    }

    void 'an unrecognised setting falls back to a Long id'() {
        given:
        System.setProperty(GormEntityTransformation.DEFAULT_ID_TYPE_PROPERTY, 'objectid')

        when:
        Class<?> bookClass = compileBook()

        then:
        bookClass.getDeclaredField('id').type == Long
    }

    void 'an entity keeps the id type it declares'() {
        given:
        System.setProperty(GormEntityTransformation.DEFAULT_ID_TYPE_PROPERTY,
                GormEntityTransformation.DEFAULT_ID_TYPE_NATIVE)

        when:
        Class<?> bookClass = compileBook('String id')

        then:
        bookClass.getDeclaredField('id').type == String
    }

    void 'the version property stays a Long whatever the id type is'() {
        given:
        System.setProperty(GormEntityTransformation.DEFAULT_ID_TYPE_PROPERTY,
                GormEntityTransformation.DEFAULT_ID_TYPE_NATIVE)

        when:
        Class<?> bookClass = compileBook('String id')

        then:
        bookClass.getDeclaredField('version').type == Long
    }

    private static Class<?> compileBook(String declaredId = '') {
        new GroovyClassLoader().parseClass("""
            import grails.gorm.annotation.Entity

            @Entity
            class Book {
                $declaredId
                String title
            }
        """)
    }
}
