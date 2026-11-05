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

import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.Query
import spock.lang.Specification

class AssociationAliasOrderSpec extends Specification {

    def 'an order keeps its block and the property as the block names it'() {
        given:
        def block = new DetachedAssociationCriteria(Object, Mock(Association))

        when:
        def order = new AssociationAliasOrder('owner.scores.score', Query.Order.Direction.DESC, block, 'second.score')

        then:
        order.property == 'owner.scores.score'
        order.direction == Query.Order.Direction.DESC
        order.association.is(block)
        order.aliasedProperty == 'second.score'
        !order.ignoreCase
    }

    def 'an order qualified with an enclosing association is still resolved against its own block'() {
        given:
        def block = new DetachedAssociationCriteria(Object, Mock(Association))
        def order = new AssociationAliasOrder('owner.scores.score', Query.Order.Direction.DESC, block, 'second.score')
        if (ignoreCase) {
            order.ignoreCase()
        }

        when:
        def qualified = order.qualifiedWith('pet')

        then: 'only the path from the root changes'
        qualified.property == 'pet.owner.scores.score'
        qualified.direction == Query.Order.Direction.DESC
        qualified.association.is(block)
        qualified.aliasedProperty == 'second.score'
        qualified.ignoreCase == ignoreCase

        where:
        ignoreCase << [false, true]
    }
}
