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
package org.grails.datastore.mapping.query

import spock.lang.Shared
import spock.lang.Specification

import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.order.ManualEntityOrdering
import org.grails.datastore.mapping.query.projections.ManualProjections

class ManualOrderingAndProjectionsSpec extends Specification {

    @Shared
    MappingContext mappingContext = new KeyValueMappingContext('test')

    @Shared
    PersistentEntity entity = mappingContext.addPersistentEntity(MOItem)

    ManualEntityOrdering ordering = new ManualEntityOrdering(entity)
    ManualProjections projections = new ManualProjections(entity)

    List<MOItem> items = [
            new MOItem(id: 3L, name: 'c', score: 30),
            new MOItem(id: 1L, name: 'a', score: null),
            new MOItem(id: 2L, name: 'b', score: 10),
    ]

    void "results are ordered by a property in both directions with nulls first"() {
        expect:
        ordering.entity.is(entity)
        ordering.applyOrder(new ArrayList(items), Query.Order.asc('name'))*.name == ['a', 'b', 'c']
        ordering.applyOrder(new ArrayList(items), Query.Order.desc('name'))*.name == ['c', 'b', 'a']
        ordering.applyOrder(new ArrayList(items), Query.Order.asc('score'))*.score == [null, 10, 30]
        ordering.applyOrder(new ArrayList(items), Query.Order.desc('score'))*.score == [30, 10, null]
        ordering.applyOrder(new ArrayList(items), Query.Order.asc('id'))*.id == [1L, 2L, 3L]
    }

    void "unknown properties and non entity elements leave the order untouched"() {
        expect:
        ordering.applyOrder(new ArrayList(items), Query.Order.asc('missing'))*.name == ['c', 'a', 'b']
        ordering.applyOrder(['z', 'y'], Query.Order.asc('name')) == ['z', 'y']
        ordering.applyOrder(['z', 'y'], Query.Order.desc('name')) == ['y', 'z']
    }

    void "a list of orders is applied in sequence"() {
        expect:
        ordering.applyOrder(null, [Query.Order.asc('name')]) == null
        ordering.applyOrder(new ArrayList(items), (List) null)*.name == ['c', 'a', 'b']
        ordering.applyOrder(new ArrayList(items), [Query.Order.asc('name'), Query.Order.desc('id')])*.id == [3L, 2L, 1L]
    }

    void "manual projections compute property values over entity instances"() {
        expect:
        projections.property(items, 'name') == ['c', 'a', 'b']
        projections.property([items[0], 'not an item'], 'name') == ['c', null]
        projections.property([], 'name') == []
        projections.property(null, 'name') == []
        projections.distinct(items + [new MOItem(id: 4L, name: 'a')], 'name') == ['c', 'a', 'b']
        projections.countDistinct(items + [new MOItem(id: 4L, name: 'a')], 'name') == 3
        projections.min(items, 'name') == 'a'
        projections.max(items, 'name') == 'c'
        projections.min(items, 'score') == null
        projections.max(items, 'score') == 30
        projections.min([], 'name') == null
        projections.max(null, 'name') == null
        projections.min(['x', 'y'], 'name') == 'x'
        projections.max(['x', 'y'], 'name') == 'y'
    }
}

class MOItem {
    Long id
    String name
    Integer score
}
