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
package org.grails.orm.hibernate.query

import grails.gorm.DetachedCriteria
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria
import org.grails.datastore.mapping.query.Query
import spock.lang.Specification

class DetachedAssociationFunctionSpec extends Specification {

    DetachedAssociationFunction function = new DetachedAssociationFunction()

    def "apply returns list with criteria if it is DetachedAssociationCriteria"() {
        given:
        def association = Mock(org.grails.datastore.mapping.model.types.Association) {
            getName() >> "test"
        }
        def criteria = new DetachedAssociationCriteria(Object, association)

        when:
        def result = function.apply(criteria)

        then:
        result.size() == 1
        result[0] == criteria
    }

    def "apply returns empty list if it is not DetachedAssociationCriteria"() {
        given:
        def criteria = new Query.Equals("prop", "value")

        when:
        def result = function.apply(criteria)

        then:
        result.isEmpty()
    }

    def "apply returns empty list for subquery criteria (isolation fix)"() {
        given: "a subquery criterion which contains association criteria internally"
        def subquery = new DetachedCriteria(Object).eq("assoc.prop", "val")
        def criterion = new Query.In("id", subquery)

        when:
        def result = function.apply(criterion)

        then: "it should NOT extract the internal association criteria (isolation)"
        result.isEmpty()
    }

    def "apply returns extracted criteria for Junction"() {
        given:
        def association1 = Mock(org.grails.datastore.mapping.model.types.Association) {
            getName() >> "test1"
        }
        def association2 = Mock(org.grails.datastore.mapping.model.types.Association) {
            getName() >> "test2"
        }
        def criteria1 = new DetachedAssociationCriteria(Object, association1)
        def criteria2 = new DetachedAssociationCriteria(Object, association2)
        def junction = new Query.Conjunction()
        junction.add(criteria1)
        junction.add(criteria2)
        junction.add(new Query.Equals("prop", "value"))

        when:
        def result = function.apply(junction)

        then:
        result.size() == 2
        result.contains(criteria1)
        result.contains(criteria2)
    }
}
