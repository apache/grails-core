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
package org.grails.orm.hibernate.access

import org.hibernate.MappingException
import org.hibernate.property.access.spi.GetterFieldImpl
import org.hibernate.property.access.spi.GetterMethodImpl
import org.hibernate.property.access.spi.SetterFieldImpl
import org.hibernate.property.access.spi.SetterMethodImpl
import spock.lang.Specification
import spock.lang.Unroll

// ─── Test fixtures ────────────────────────────────────────────────────────────

trait HasName {
    String name
}

trait HasActive {
    boolean active
}

trait HasFlag {
    Boolean flag
}

trait HasComputed {
    String getComputed() { "foo" }
}

/** Plain Groovy class — no trait involvement. */
class PlainPerson {
    String plain
}

/** Groovy class implementing a String trait. */
class NamedEntity implements HasName {}

/** Groovy class implementing a primitive-boolean trait. */
class ActiveEntity implements HasActive {}

/** Groovy class implementing a boxed-Boolean trait. */
class FlaggedEntity implements HasFlag {}

/** Groovy class implementing a computed-property trait. */
class ComputedEntity implements HasComputed {}

/** Computed read-write property via trait methods (no backing field). */
trait HasComputedRW {
    String getComputedRW() { "rw" }
    void setComputedRW(String v) { }
}

class ComputedRWEntity implements HasComputedRW {}

// ─── Spec ─────────────────────────────────────────────────────────────────────

class TraitPropertyAccessStrategySpec extends Specification {

    TraitPropertyAccessStrategy strategy = new TraitPropertyAccessStrategy()

    // ─── getTraitFieldName ────────────────────────────────────────────────────

    void "getTraitFieldName encodes dots as underscores with double-underscore separator"() {
        expect:
        strategy.getTraitFieldName(HasName, 'name') ==
            'org_grails_orm_hibernate_access_HasName__name'
    }

    void "getTraitFieldName encodes different trait class correctly"() {
        expect:
        strategy.getTraitFieldName(HasActive, 'active') ==
            'org_grails_orm_hibernate_access_HasActive__active'
    }

    void "getTraitFieldName replaces every dot in the package name"() {
        given:
        def fieldName = strategy.getTraitFieldName(HasName, 'name')

        expect:
        !fieldName.contains('.')
        fieldName.contains('__')
        fieldName.endsWith('__name')
    }

    // ─── buildPropertyAccess: String trait property ───────────────────────────

    void "buildPropertyAccess returns non-null PropertyAccess for String trait property"() {
        when:
        def access = strategy.buildPropertyAccess(NamedEntity, 'name')

        then:
        access != null
        access.getter != null
        access.setter != null
    }

    void "PropertyAccess.getPropertyAccessStrategy returns the originating strategy"() {
        given:
        def access = strategy.buildPropertyAccess(NamedEntity, 'name')

        expect:
        access.propertyAccessStrategy.is(strategy)
    }

    void "getter and setter for String trait property are field-based"() {
        given:
        def access = strategy.buildPropertyAccess(NamedEntity, 'name')

        expect:
        access.getter instanceof GetterFieldImpl
        access.setter instanceof SetterFieldImpl
    }

    void "getter.getReturnTypeClass returns String for String trait property"() {
        given:
        def access = strategy.buildPropertyAccess(NamedEntity, 'name')

        expect:
        access.getter.returnTypeClass == String
    }

    void "getter.getMember returns the backing trait Field for String property"() {
        given:
        def access = strategy.buildPropertyAccess(NamedEntity, 'name')

        expect:
        access.getter.getMember() instanceof java.lang.reflect.Field
        (access.getter.getMember() as java.lang.reflect.Field).name ==
            'org_grails_orm_hibernate_access_HasName__name'
    }

    // ─── buildPropertyAccess: primitive boolean trait property ───────────────

    void "buildPropertyAccess resolves primitive boolean trait property via isXxx getter"() {
        when:
        def access = strategy.buildPropertyAccess(ActiveEntity, 'active')

        then:
        access != null
        access.getter instanceof GetterFieldImpl
        access.setter instanceof SetterFieldImpl
    }

    void "getter.getReturnTypeClass returns boolean for boolean trait property"() {
        given:
        def access = strategy.buildPropertyAccess(ActiveEntity, 'active')

        expect:
        access.getter.returnTypeClass == boolean
    }

    void "getter.getMember returns the backing trait Field for boolean property"() {
        given:
        def access = strategy.buildPropertyAccess(ActiveEntity, 'active')

        expect:
        (access.getter.getMember() as java.lang.reflect.Field).name ==
            'org_grails_orm_hibernate_access_HasActive__active'
    }

    // ─── buildPropertyAccess: boxed Boolean trait property ───────────────────

    void "buildPropertyAccess resolves boxed Boolean trait property via isXxx getter"() {
        when:
        def access = strategy.buildPropertyAccess(FlaggedEntity, 'flag')

        then:
        access != null
        access.getter instanceof GetterFieldImpl
        access.setter instanceof SetterFieldImpl
    }

    void "getter.getMember returns the backing trait Field for Boolean property"() {
        given:
        def access = strategy.buildPropertyAccess(FlaggedEntity, 'flag')

        expect:
        (access.getter.getMember() as java.lang.reflect.Field).name ==
            'org_grails_orm_hibernate_access_HasFlag__flag'
    }

    // ─── buildPropertyAccess: error paths ────────────────────────────────────

    void "buildPropertyAccess throws IllegalStateException for non-trait property"() {
        when:
        strategy.buildPropertyAccess(PlainPerson, 'plain')

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('plain')
        e.message.contains('PlainPerson')
        e.message.contains('not provided by a trait')
    }

    void "buildPropertyAccess throws IllegalStateException for non-existent property"() {
        when:
        strategy.buildPropertyAccess(NamedEntity, 'nonExistent')

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('nonExistent')
        e.message.contains('not provided by a trait')
    }

    void "buildPropertyAccess error message includes class name"() {
        when:
        strategy.buildPropertyAccess(NamedEntity, 'missing')

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('NamedEntity')
    }

    // ─── 3-arg overload ───────────────────────────────────────────────────────

    void "3-arg buildPropertyAccess delegates to 2-arg version"() {
        given:
        def access2 = strategy.buildPropertyAccess(NamedEntity, 'name')
        def access3 = strategy.buildPropertyAccess(NamedEntity, 'name', true)

        expect:
        access2.getter.class     == access3.getter.class
        access2.setter.class     == access3.setter.class
        access3.propertyAccessStrategy.is(strategy)
    }

    @Unroll
    void "3-arg overload with setterRequired=#req still resolves correctly"() {
        when:
        def access = strategy.buildPropertyAccess(NamedEntity, 'name', req)

        then:
        access.getter instanceof GetterFieldImpl

        where:
        req << [true, false]
    }

    // ─── multiple independent buildPropertyAccess calls ───────────────────────

    void "two buildPropertyAccess calls for same class return independent instances"() {
        given:
        def access1 = strategy.buildPropertyAccess(NamedEntity, 'name')
        def access2 = strategy.buildPropertyAccess(NamedEntity, 'name')

        expect:
        !access1.is(access2)
        access1.getter.returnTypeClass == access2.getter.returnTypeClass
    }

    void "buildPropertyAccess works on two different trait-implementing classes"() {
        given:
        def nameAccess   = strategy.buildPropertyAccess(NamedEntity, 'name')
        def activeAccess = strategy.buildPropertyAccess(ActiveEntity, 'active')

        expect:
        nameAccess.getter.returnTypeClass   == String
        activeAccess.getter.returnTypeClass == boolean
    }

    // ─── Read-only property (no field, no setter) ───────────────────────────

    void "buildPropertyAccess for computed property returns method-based getter and no setter if not required"() {
        when:
        def access = strategy.buildPropertyAccess(ComputedEntity, 'computed', false)

        then:
        access != null
        access.getter instanceof GetterMethodImpl
        access.setter == null
    }

    void "buildPropertyAccess for computed property throws MappingException if setter is required"() {
        when:
        strategy.buildPropertyAccess(ComputedEntity, 'computed', true)

        then:
        thrown(MappingException)
    }

    void "buildPropertyAccess for computed read-write property creates method-based getter and setter"() {
        when:
        def access = strategy.buildPropertyAccess(ComputedRWEntity, 'computedRW', false)

        then:
        access != null
        access.getter instanceof GetterMethodImpl
        access.setter instanceof SetterMethodImpl
    }

    void "buildPropertyAccess throws IllegalStateException if readMethod has no trait annotations"() {
        when: "using a method that looks like a getter but is not from a trait"
        strategy.buildPropertyAccess(NoTraitAnnotationEntity, 'notATraitProp')

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('not provided by a trait')
    }

    void "buildPropertyAccess ignores isXxx if return type is not boolean"() {
        when: "calling for a property where isXxx returns String"
        strategy.buildPropertyAccess(InvalidBooleanEntity, 'fakeBoolean')

        then:
        thrown(IllegalStateException)
    }
}

class NoTraitAnnotationEntity {
    String getNotATraitProp() { "foo" }
}

class InvalidBooleanEntity {
    String isFakeBoolean() { "not a boolean" }
}

