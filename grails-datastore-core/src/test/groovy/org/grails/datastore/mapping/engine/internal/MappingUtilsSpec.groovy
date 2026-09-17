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
package org.grails.datastore.mapping.engine.internal

import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.PropertyMapping

class MappingUtilsSpec extends Specification {

    @Unroll
    void "accessor names for #property are #getter and #setter"() {
        expect:
        MappingUtils.getGetterName(property) == getter
        MappingUtils.getSetterName(property) == setter

        where:
        property | getter    | setter
        'name'   | 'getName' | 'setName'
        'aB'     | 'getaB'   | 'setaB'
        'URL'    | 'getURL'  | 'setURL'
        'x'      | 'getX'    | 'setX'
    }

    void "the target key comes from the mapped form and falls back to the property name"() {
        given:
        PersistentProperty unmapped = Stub(PersistentProperty) { getName() >> 'plain'; getMapping() >> null }
        PersistentProperty noForm = Stub(PersistentProperty) {
            getName() >> 'noForm'
            getMapping() >> Stub(PropertyMapping) { getMappedForm() >> null }
        }
        PersistentProperty unnamed = Stub(PersistentProperty) {
            getName() >> 'unnamed'
            getMapping() >> Stub(PropertyMapping) { getMappedForm() >> new Property() }
        }
        PersistentProperty named = Stub(PersistentProperty) {
            getName() >> 'named'
            getMapping() >> Stub(PropertyMapping) { getMappedForm() >> new Property(targetName: 'col') }
        }

        expect:
        MappingUtils.getTargetKey(unmapped) == 'plain'
        MappingUtils.getTargetKey(noForm) == 'noForm'
        MappingUtils.getTargetKey(unnamed) == 'unnamed'
        MappingUtils.getTargetKey(named) == 'col'
    }

    @Unroll
    void "a concrete #type.simpleName is a #expected.simpleName"() {
        expect:
        MappingUtils.createConcreteCollection(type).getClass() == expected

        where:
        type       | expected
        List       | ArrayList
        SortedSet  | TreeSet
        Queue      | ArrayDeque
        Set        | LinkedHashSet
        Collection | LinkedHashSet
    }

    void "declared fields are found up the class hierarchy"() {
        expect:
        MappingUtils.getDeclaredField(MUChild, 'childField').declaringClass == MUChild
        MappingUtils.getDeclaredField(MUChild, 'parentField').declaringClass == MUParent
        MappingUtils.getDeclaredField(MUChild, 'missing') == null
    }

    void "generic component types are read from the field declaration"() {
        expect:
        MappingUtils.getGenericTypeForProperty(MUChild, 'names') == String
        MappingUtils.getGenericTypeForProperty(MUChild, 'scores') == Integer
        MappingUtils.getGenericTypeForProperty(MUChild, 'raw') == null
        MappingUtils.getGenericTypeForProperty(MUChild, 'wild') == null
        MappingUtils.getGenericTypeForProperty(MUChild, 'missing') == null
        MappingUtils.getGenericTypeForProperty(MUChild, 'parentNames') == Long
    }

    void "map key and value types are read from the field declaration"() {
        expect:
        MappingUtils.getGenericTypeForMapProperty(MUChild, 'scores', true) == String
        MappingUtils.getGenericTypeForMapProperty(MUChild, 'scores', false) == Integer
        MappingUtils.getGenericTypeForMapProperty(MUChild, 'raw', false) == null
        MappingUtils.getGenericTypeForMapProperty(MUChild, 'missing', false) == null
    }

    void "the bound of the first type parameter is the generic type"() {
        expect:
        MappingUtils.getGenericType(MUBounded) == Number
        MappingUtils.getGenericType(MUUnbounded) == Object
        MappingUtils.getGenericType(String) == null
    }
}

class MUParent {
    String parentField
    List<Long> parentNames
}

class MUChild extends MUParent {
    String childField
    List<String> names
    Map<String, Integer> scores
    List raw
    List<?> wild
}

class MUBounded<T extends Number> {
}

class MUUnbounded<T> {
}
