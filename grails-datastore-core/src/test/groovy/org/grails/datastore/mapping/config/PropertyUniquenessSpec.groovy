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
package org.grails.datastore.mapping.config

import spock.lang.Specification

class PropertyUniquenessSpec extends Specification {

    void "setUnique with boolean sets single column unique flag without group"() {
        given:
        Property property = new Property()

        when:
        property.setUnique(true)

        then:
        property.isUnique()
        !property.isUniqueWithinGroup()
        property.getUniquenessGroup().isEmpty()
    }

    void "setUnique with String adds single property to uniqueness group"() {
        given:
        Property property = new Property()

        when:
        property.setUnique("category")

        then:
        property.isUnique()
        property.isUniqueWithinGroup()
        property.getUniquenessGroup() == ["category"]
    }

    void "setUnique with List adds all properties to uniqueness group"() {
        given:
        Property property = new Property()

        when:
        property.setUnique(["group", "department"])

        then:
        property.isUnique()
        property.isUniqueWithinGroup()
        property.getUniquenessGroup() == ["group", "department"]
    }

    void "setUnique with Collection adds all properties to uniqueness group"() {
        given:
        Property property = new Property()

        when:
        property.setUnique(['group', 'department'] as Set)

        then:
        property.isUnique()
        property.isUniqueWithinGroup()
        property.getUniquenessGroup() == ['group', 'department']
    }

    void "unique with Collection adds all properties to uniqueness group"() {
        given:
        Property property = new Property()

        when:
        property.unique(['group', 'department'] as Set)

        then:
        property.isUnique()
        property.isUniqueWithinGroup()
        property.getUniquenessGroup() == ['group', 'department']
    }

    void "setUnique with Map accepts List, Set, and CharSequence groups"() {
        given:
        Property property = new Property()

        when:
        property.setUnique([group: groupOption, nulls: 'value'])

        then:
        property.isUnique()
        property.isUniqueWithinGroup()
        property.getUniquenessGroup() == expectedGroup

        where:
        groupOption                      || expectedGroup
        'httpMethod'                     || ['httpMethod']
        ['country', 'language']          || ['country', 'language']
        (['country', 'language'] as Set) || ['country', 'language']
    }

    void "setUnique with Map with null or absent group sets single column unique flag"() {
        given:
        Property property = new Property()

        when:
        property.setUnique([nulls: 'skip'])

        then:
        property.isUnique()
        !property.isUniqueWithinGroup()
        property.getUniquenessGroup().isEmpty()
    }
}
