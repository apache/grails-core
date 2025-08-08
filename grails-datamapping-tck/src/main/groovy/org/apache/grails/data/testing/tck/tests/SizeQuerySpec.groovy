/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.Child_BT_Default_P
import org.apache.grails.data.testing.tck.domains.Owner_Default_Bi_P
import spock.lang.Unroll

/**
 * Tests for querying the size of collections etc.
 */
class SizeQuerySpec extends GrailsDataTckSpec {
    void setupSpec() {
        manager.addAllDomainClasses([Owner_Default_Bi_P, Child_BT_Default_P])
    }

    private void setupTestData() {
        // Owner A has 1 child
        new Owner_Default_Bi_P(name: "Owner A")
                .addToChildren(new Child_BT_Default_P(title: "Child 1"))
                .save(flush: true)

        // Owner B has 2 children
        new Owner_Default_Bi_P(name: "Owner B")
                .addToChildren(new Child_BT_Default_P(title: "Child 5"))
                .addToChildren(new Child_BT_Default_P(title: "Child 6"))
                .save(flush: true)

        // Owner C has 3 children
        new Owner_Default_Bi_P(name: "Owner C")
                .addToChildren(new Child_BT_Default_P(title: "Child 2"))
                .addToChildren(new Child_BT_Default_P(title: "Child 3"))
                .addToChildren(new Child_BT_Default_P(title: "Child 4"))
                .save(flush: true)

        manager.session.clear()
    }

    @Unroll("Test sizeLe criterion with size #size expects #expectedNames")
    void "Test sizeLe criterion"(int size, List<String> expectedNames) {
        given: "A set of owners with 1, 2, and 3 children"
        setupTestData()

        when: "We query for owners with at most #size children"
        def results = Owner_Default_Bi_P.withCriteria {
            sizeLe "children", size
            order "name"
        }

        then: "We get the correct owners back"
        results*.name == expectedNames

        where:
        size | expectedNames
        3    | ['Owner A', 'Owner B', 'Owner C']
        2    | ['Owner A', 'Owner B']
        1    | ['Owner A']
        0    | []
    }

    @Unroll("Test sizeLt criterion with size #size expects #expectedNames")
    void "Test sizeLt criterion"(int size, List<String> expectedNames) {
        given: "A set of owners with 1, 2, and 3 children"
        setupTestData()

        when: "We query for owners with less than #size children"
        def results = Owner_Default_Bi_P.withCriteria {
            sizeLt "children", size
            order "name"
        }

        then: "We get the correct owners back"
        results*.name == expectedNames

        where:
        size | expectedNames
        3    | ['Owner A', 'Owner B']
        2    | ['Owner A']
        1    | []
    }

    @Unroll("Test sizeGt criterion with size #size expects #expectedNames")
    void "Test sizeGt criterion"(int size, List<String> expectedNames) {
        given: "A set of owners with 1, 2, and 3 children"
        setupTestData()

        when: "We query for owners with more than #size children"
        def results = Owner_Default_Bi_P.withCriteria {
            sizeGt "children", size
            order "name"
        }

        then: "We get the correct owners back"
        results*.name == expectedNames

        where:
        size | expectedNames
        0    | ['Owner A', 'Owner B', 'Owner C']
        1    | ['Owner B', 'Owner C']
        2    | ['Owner C']
        3    | []
    }

    @Unroll("Test sizeGe criterion with size #size expects #expectedNames")
    void "Test sizeGe criterion"(int size, List<String> expectedNames) {
        given: "A set of owners with 1, 2, and 3 children"
        setupTestData()

        when: "We query for owners with at least #size children"
        def results = Owner_Default_Bi_P.withCriteria {
            sizeGe "children", size
            order "name"
        }

        then: "We get the correct owners back"
        results*.name == expectedNames

        where:
        size | expectedNames
        1    | ['Owner A', 'Owner B', 'Owner C']
        2    | ['Owner B', 'Owner C']
        3    | ['Owner C']
        4    | []
    }

    @Unroll("Test sizeEq criterion with size #size expects #expectedNames")
    void "Test sizeEq criterion"(int size, List<String> expectedNames) {
        given: "A set of owners with 1, 2, and 3 children"
        setupTestData()

        when: "We query for owners with exactly #size children"
        def results = Owner_Default_Bi_P.withCriteria {
            sizeEq "children", size
            order "name"
        }

        then: "We get the correct owners back"
        results*.name == expectedNames

        where:
        size | expectedNames
        1    | ['Owner A']
        2    | ['Owner B']
        3    | ['Owner C']
        4    | []
    }

    @Unroll("Test sizeNe criterion for #description expects #expectedNames")
    void "Test sizeNe criterion"(String description, Closure queryLogic, List<String> expectedNames) {
        given: "A set of owners with 1, 2, and 3 children"
        setupTestData()

        when: "We query for owners where the number of children meets a condition"
        
        def results = Owner_Default_Bi_P.withCriteria {
            // Set the delegate of the query closure to the criteria builder and call it
            queryLogic.delegate = delegate
            queryLogic.call()
            order "name"
        }

        then: "We get the correct owners back"
        results*.name == expectedNames

        where:
        description           | queryLogic                                           | expectedNames
        "size != 1"           | { sizeNe "children", 1 }                             | ['Owner B', 'Owner C']
        "size != 2"           | { sizeNe "children", 2 }                             | ['Owner A', 'Owner C']
        "size != 3"           | { sizeNe "children", 3 }                             | ['Owner A', 'Owner B']
        "size != 1 and != 3"  | { and { sizeNe "children", 1; sizeNe "children", 3 } } | ['Owner B']
    }
}