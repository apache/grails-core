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

import spock.lang.Shared
import spock.lang.Specification

import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity

class FieldEntityAccessSpec extends Specification {

    @Shared
    MappingContext mappingContext = new KeyValueMappingContext('test')

    @Shared
    PersistentEntity entity = mappingContext.addPersistentEntity(Widget)

    void setup() {
        FieldEntityAccess.clearReflectors()
    }

    private FieldEntityAccess accessFor(Widget widget) {
        new FieldEntityAccess(entity, widget, mappingContext.conversionService)
    }

    void "reflectors are cached per entity name until cleared"() {
        when:
        EntityReflector first = FieldEntityAccess.getOrIntializeReflector(entity)
        EntityReflector second = FieldEntityAccess.getOrIntializeReflector(entity)

        then:
        first.is(second)
        FieldEntityAccess.getReflector(entity.name).is(first)
        FieldEntityAccess.getReflector('nope') == null

        when:
        FieldEntityAccess.clearReflectors()

        then:
        FieldEntityAccess.getReflector(entity.name) == null
        !FieldEntityAccess.getOrIntializeReflector(entity).is(first)
    }

    void "entity access exposes the entity and its metadata"() {
        given:
        Widget widget = new Widget(name: 'w', count: 3)
        FieldEntityAccess access = accessFor(widget)

        expect:
        access.entity.is(widget)
        access.persistentEntity.is(entity)
        access.identifierName == 'id'
        access.getPropertyType('name') == String
        access.getPropertyType('count') == Integer
        access.getPropertyType('missing') == null
        access.getProperty('name') == 'w'
        access.getPropertyValue('count') == 3
    }

    void "setProperty converts the value using the conversion service"() {
        given:
        Widget widget = new Widget()
        FieldEntityAccess access = accessFor(widget)

        when:
        access.setProperty('count', '42')
        access.setProperty('name', 7)

        then:
        widget.count == 42
        widget.name == '7'
    }

    void "setProperty reports values that cannot be converted"() {
        given:
        FieldEntityAccess access = accessFor(new Widget())

        when:
        access.setProperty('count', 'not a number')

        then:
        IllegalArgumentException e = thrown()
        e.message.startsWith('Cannot assign value [not a number] to property [count] of type [java.lang.Integer] of class [' + Widget.name + ']')
    }

    void "setProperty rejects unknown properties"() {
        when:
        accessFor(new Widget()).setProperty('missing', 1)

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Property [missing] is not a valid property of ' + Widget
    }

    void "setPropertyNoConversion writes the raw value and reports incompatible types"() {
        given:
        Widget widget = new Widget()
        FieldEntityAccess access = accessFor(widget)

        when:
        access.setPropertyNoConversion('count', 5)

        then:
        widget.count == 5

        when:
        access.setPropertyNoConversion('count', 'five')

        then:
        IllegalArgumentException e = thrown()
        e.message.startsWith('Cannot assign value [five] with type [java.lang.String] to property [count] of class [' + Widget.name + ']')
    }

    void "identifier is read and written with and without conversion"() {
        given:
        Widget widget = new Widget()
        FieldEntityAccess access = accessFor(widget)

        expect:
        access.identifier == null

        when:
        access.setIdentifier('10')

        then:
        widget.id == 10L
        access.identifier == 10L

        when:
        access.setIdentifierNoConversion(11L)

        then:
        widget.id == 11L

        when:
        access.setIdentifier('abc')

        then:
        IllegalArgumentException e = thrown()
        e.message.startsWith('Cannot assign identifier [abc] to property [id] of type [java.lang.Long] of class [' + Widget.name + ']')

        when:
        access.setIdentifierNoConversion('abc')

        then:
        e = thrown()
        e.message.startsWith('Cannot assign identifier [abc] to property [id] of type [java.lang.Long] of class [' + Widget.name + ']')
    }

    void "refresh is a no-op"() {
        given:
        Widget widget = new Widget(name: 'w')
        FieldEntityAccess access = accessFor(widget)

        when:
        access.refresh()

        then:
        widget.name == 'w'
    }

    void "the reflector reads and writes by name and by index"() {
        given:
        EntityReflector reflector = FieldEntityAccess.getOrIntializeReflector(entity)
        Widget widget = new Widget(name: 'w', count: 1)
        List<String> names = entity.persistentProperties*.name

        expect:
        reflector.persitentEntity.is(entity)
        reflector.identifierName == 'id'
        reflector.identifierType() == Long
        reflector.propertyNames.toList().sort() == (names + ['id']).sort()
        reflector.getProperty(widget, 'name') == 'w'
        reflector.getProperty(widget, names.indexOf('count')) == 1

        when:
        reflector.setProperty(widget, 'name', 'x')
        reflector.setProperty(widget, names.indexOf('count'), 2)
        reflector.setIdentifier(widget, 3L)

        then:
        widget.name == 'x'
        widget.count == 2
        reflector.getIdentifier(widget) == 3L
        reflector.getIdentifier(null) == null
    }

    void "the reflector exposes readers and writers backed by fields"() {
        given:
        EntityReflector reflector = FieldEntityAccess.getOrIntializeReflector(entity)

        when:
        EntityReflector.PropertyReader reader = reflector.getPropertyReader('name')
        EntityReflector.PropertyWriter writer = reflector.getPropertyWriter('name')

        then:
        reader.field().name == 'name'
        reader.getter().name == 'getName'
        reader.propertyType() == String
        writer.field().name == 'name'
        writer.setter().name == 'setName'
        writer.propertyType() == String

        when:
        reflector.getPropertyReader('missing')

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Property [missing] is not a valid property of ' + Widget

        when:
        reflector.getPropertyWriter('missing')

        then:
        e = thrown()
        e.message == 'Property [missing] is not a valid property of ' + Widget
    }

    void "the reflector falls back to accessor methods for getter-only properties"() {
        given:
        PersistentEntity derivedEntity = mappingContext.addPersistentEntity(Derived)
        EntityReflector reflector = FieldEntityAccess.getOrIntializeReflector(derivedEntity)
        Derived derived = new Derived(base: 'abc')

        when:
        EntityReflector.PropertyReader reader = reflector.getPropertyReader('upper')
        EntityReflector.PropertyWriter writer = reflector.getPropertyWriter('upper')

        then:
        reader.field() == null
        reader.getter().name == 'getUpper'
        reader.propertyType() == String
        reader.read(derived) == 'ABC'
        writer.field() == null
        writer.setter().name == 'setUpper'
        writer.propertyType() == String

        when:
        writer.write(derived, 'xyz')

        then:
        derived.base == 'xyz'
    }

    void "the reflector reads trait properties through the generated trait field"() {
        given:
        PersistentEntity traitedEntity = mappingContext.addPersistentEntity(Traited)
        EntityReflector reflector = FieldEntityAccess.getOrIntializeReflector(traitedEntity)
        Traited traited = new Traited(label: 'l')

        when:
        EntityReflector.PropertyReader reader = reflector.getPropertyReader('label')

        then:
        reader.field().name.endsWith('__label')
        reader.read(traited) == 'l'

        when:
        reflector.getPropertyWriter('label').write(traited, 'm')

        then:
        traited.label == 'm'
    }

    void "dirty checking state is read from the DirtyCheckable trait field"() {
        given:
        PersistentEntity trackedEntity = mappingContext.addPersistentEntity(Tracked)
        EntityReflector reflector = FieldEntityAccess.getOrIntializeReflector(trackedEntity)
        Tracked tracked = new Tracked()

        expect:
        reflector.getDirtyCheckingState(tracked) == null
        FieldEntityAccess.getOrIntializeReflector(entity).getDirtyCheckingState(new Widget()) == null

        when:
        tracked.trackChanges()
        tracked.name = 'changed'

        then:
        reflector.getDirtyCheckingState(tracked) == [name: null]
    }

    void "fastClass is created lazily and cached"() {
        given:
        EntityReflector reflector = FieldEntityAccess.getOrIntializeReflector(entity)

        expect:
        reflector.fastClass() != null
        reflector.fastClass().is(reflector.fastClass())
    }

    static class Widget {
        Long id
        String name
        Integer count
    }

    static class Derived {
        Long id
        String base

        String getUpper() {
            base?.toUpperCase()
        }

        void setUpper(String value) {
            base = value?.toLowerCase()
        }
    }

    static class Tracked implements DirtyCheckable {
        Long id
        String name

        void setName(String name) {
            markDirty('name')
            this.name = name
        }
    }
}

trait Labelled {
    String label
}

class Traited implements Labelled {
    Long id
}
