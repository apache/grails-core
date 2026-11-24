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

import grails.gorm.DetachedCriteria
import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Unroll

/**
 * A negated condition on an embedded value whose property is stored under another name than its own, at each level of
 * nesting. The fields of the query document are the stored names, so the embedded value that a negation requires has to
 * be found by the name it is stored under, not by the name of the property: an owner that has no address does not match a
 * negated condition on the address, whatever the address is stored as. The expected values are those of the same queries
 * where the names are not changed, in {@code CriteriaEmbeddedNegationSpec}. Only the block form is here: a dotted property
 * through an embedded property that is stored under another name, such as {@code eq('address.city', ...)}, finds nothing on
 * MongoDB with or without a negation, and is a limit of its own.
 */
class EmbeddedNegationStoredNamesSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([SnOwner, SnCity])
    }

    /** Amelie lives in Paris, in the north zone; Bob in Rome, in the south zone; Nina has no address */
    private void seed() {
        new SnOwner(name: 'Amelie', address: new SnAddress(city: 'Paris', geo: new SnGeo(zone: 'north'))).save(flush: true, failOnError: true)
        new SnOwner(name: 'Bob', address: new SnAddress(city: 'Rome', geo: new SnGeo(zone: 'south'))).save(flush: true, failOnError: true)
        new SnOwner(name: 'Nina').save(flush: true, failOnError: true)
        new SnCity(name: 'Paris').save(flush: true, failOnError: true)
        new SnCity(name: 'Oslo').save(flush: true, failOnError: true)
        new SnCity(name: 'Rome').save(flush: true, failOnError: true)
        new SnCity(name: 'north').save(flush: true, failOnError: true)
        manager.session.clear()
    }

    /** the names of the cities that are taken, as a subquery: the form of notIn that takes the values from a query */
    private static DetachedCriteria<SnCity> cities(List<String> taken) {
        new DetachedCriteria(SnCity).build { inList('name', taken) }.property('name')
    }

    private static List names(List<SnOwner> owners) {
        owners*.name.sort()
    }

    @Unroll
    void 'a condition in #form on an embedded value stored under other names'() {
        given:
        seed()

        expect:
        names(query.call()) == expected

        where:
        [form, query, expected] << QUERIES
    }

    private static final List<List> QUERIES = [
        ['a block with eq, the positive control', { -> SnOwner.createCriteria().list { address { eq('city', 'Paris') } } }, ['Amelie']],
        ['a block with ne', { -> SnOwner.createCriteria().list { address { ne('city', 'Paris') } } }, ['Bob']],
        ['a block with not and inList', { -> SnOwner.createCriteria().list { address { not { inList('city', ['Paris']) } } } }, ['Bob']],
        ['a block with notIn of a subquery', { -> SnOwner.createCriteria().list { address { notIn('city', cities(['Paris'])) } } }, ['Bob']],
        ['a block with notIn of a subquery of several values', { -> SnOwner.createCriteria().list { address { notIn('city', cities(['Paris', 'Oslo'])) } } }, ['Bob']],
        ['a block with notIn of a subquery of the values of both owners', { -> SnOwner.createCriteria().list { address { notIn('city', cities(['Paris', 'Rome'])) } } }, []],
        ['a detached block with notIn of a subquery', { -> new DetachedCriteria(SnOwner).build { address { notIn('city', cities(['Paris'])) } }.list() }, ['Bob']],
        ['a block in a block with notIn of a subquery', { -> SnOwner.createCriteria().list { address { geo { notIn('zone', cities(['north'])) } } } }, ['Bob']],
        ['an or of a name and a block with notIn of a subquery', { -> SnOwner.createCriteria().list { or { eq('name', 'Nina'); address { notIn('city', cities(['Paris'])) } } } }, ['Bob', 'Nina']],
        ['a block with not and eq', { -> SnOwner.createCriteria().list { address { not { eq('city', 'Paris') } } } }, ['Bob']],
        ['a negated block', { -> SnOwner.createCriteria().list { not { address { eq('city', 'Paris') } } } }, ['Bob']],
        ['a detached block with ne', { -> new DetachedCriteria(SnOwner).build { address { ne('city', 'Paris') } }.list() }, ['Bob']],
        ['a block in a block with ne', { -> SnOwner.createCriteria().list { address { geo { ne('zone', 'north') } } } }, ['Bob']],
        ['a block in a block with not and inList', { -> SnOwner.createCriteria().list { address { geo { not { inList('zone', ['north']) } } } } }, ['Bob']],
        ['a negated block in a block', { -> SnOwner.createCriteria().list { not { address { geo { eq('zone', 'north') } } } } }, ['Bob']],
        ['an and of a name and a negated block', { -> SnOwner.createCriteria().list { and { eq('name', 'Bob'); address { ne('city', 'Paris') } } } }, ['Bob']],
        ['a negated and of a name and a block', { -> SnOwner.createCriteria().list { not { and { eq('name', 'Bob'); address { eq('city', 'Paris') } } } } }, ['Amelie', 'Bob', 'Nina']],
        ['an or of a name and a negated block', { -> SnOwner.createCriteria().list { or { eq('name', 'Nina'); address { ne('city', 'Paris') } } } }, ['Bob', 'Nina']],
    ]
}

@Entity
class SnCity {

    String id
    String name

    static mapWith = 'mongo'
}

@Entity
class SnOwner {

    String id
    String name
    SnAddress address

    static mapWith = 'mongo'

    static embedded = ['address']

    static constraints = {
        address nullable: true
    }

    static mapping = {
        collection 'sn_owners'
        address field: 'addr'
    }
}

@Entity
class SnAddress {

    String city
    SnGeo geo

    static mapWith = 'mongo'

    static embedded = ['geo']

    static mapping = {
        city field: 'town'
        geo field: 'g'
    }
}

@Entity
class SnGeo {

    String zone

    static mapWith = 'mongo'

    static mapping = {
        zone field: 'zn'
    }
}
