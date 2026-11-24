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

import grails.gorm.DetachedCriteria

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.NegationAddress
import org.apache.grails.data.testing.tck.domains.NegationOwner
import spock.lang.Requires
import spock.lang.Unroll

/**
 * A negated condition on an embedded value, in every form a criteria takes it and in every position: as an association
 * block, as a dotted property, in a detached criteria and in a where query, beside an or branch that has no embedded
 * value in it, and under a count. The expected values are what Hibernate 5, the reference, returns: the embedded value is
 * the columns of its owner, so a negated condition on it is unknown for an owner that has no such value and does not
 * match, exactly as the positive condition does not. MongoDB matches a missing field with {@code $ne}, {@code $nin} and
 * {@code $nor}, so it has to require the embedded value itself.
 *
 * It runs for Hibernate 5 and MongoDB, the stores that have embedded values: the in-memory datastore does not answer
 * criteria on an embedded value, with or without a negation, so there is nothing to compare for it.
 */
@Requires({ Boolean.getBoolean('hibernate5.gorm.suite') || Boolean.getBoolean('mongodb.gorm.suite') })
class CriteriaEmbeddedNegationSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.domainClasses.addAll([NegationOwner])
    }

    /** Amelie lives in Paris, Bob in Rome, and Nina has no address */
    private void seed() {
        new NegationOwner(name: 'Amelie', address: new NegationAddress(city: 'Paris', country: 'FR')).save(flush: true, failOnError: true)
        new NegationOwner(name: 'Bob', address: new NegationAddress(city: 'Rome', country: 'IT')).save(flush: true, failOnError: true)
        new NegationOwner(name: 'Nina').save(flush: true, failOnError: true)
        manager.session.clear()
    }

    /** a query that fails is the string THROWS, whatever the datastore calls the error */
    private static Object outcome(Closure query) {
        try {
            Object result = query.call()
            return result instanceof Collection ? result.sort() : result
        }
        catch (Throwable ignored) {
            return 'THROWS'
        }
    }

    @Unroll
    void 'a condition on an embedded value in #form'() {
        given:
        seed()

        expect:
        outcome(query) == expected

        where:
        [form, query, expected] << QUERIES
    }

    private static List names(List<NegationOwner> owners) {
        owners*.name
    }

    private static final List<List> QUERIES = [
        ['a block with eq, the positive control', { -> names(NegationOwner.createCriteria().list { address { eq('city', 'Paris') } }) }, ['Amelie']],
        ['a block with ne', { -> names(NegationOwner.createCriteria().list { address { ne('city', 'Paris') } }) }, ['Bob']],
        ['a block with notIn', { -> names(NegationOwner.createCriteria().list { address { not { inList('city', ['Paris']) } } }) }, ['Bob']],
        ['a block with not and eq', { -> names(NegationOwner.createCriteria().list { address { not { eq('city', 'Paris') } } }) }, ['Bob']],
        ['a block with ne on the second property', { -> names(NegationOwner.createCriteria().list { address { ne('country', 'FR') } }) }, ['Bob']],
        ['a dotted property with eq, the positive control', { -> names(NegationOwner.createCriteria().list { eq('address.city', 'Paris') }) }, ['Amelie']],
        ['a dotted property with ne', { -> names(NegationOwner.createCriteria().list { ne('address.city', 'Paris') }) }, ['Bob']],
        ['a dotted property under not', { -> names(NegationOwner.createCriteria().list { not { eq('address.city', 'Paris') } }) }, ['Bob']],
        ['a dotted property with not and inList', { -> names(NegationOwner.createCriteria().list { not { inList('address.city', ['Paris']) } }) }, ['Bob']],
        ['a detached block with ne', { -> names(new DetachedCriteria(NegationOwner).build { address { ne('city', 'Paris') } }.list()) }, ['Bob']],
        ['a detached block with not and inList', { -> names(new DetachedCriteria(NegationOwner).build { address { not { inList('city', ['Paris']) } } }.list()) }, ['Bob']],
        ['a detached dotted property with ne', { -> names(new DetachedCriteria(NegationOwner).build { ne('address.city', 'Paris') }.list()) }, ['Bob']],
        ['a where query with !=', { -> names(NegationOwner.where { address.city != 'Paris' }.list()) }, ['Bob']],
        ['an or with a branch that has no embedded value', { -> names(NegationOwner.createCriteria().list { or { eq('name', 'Nina'); address { ne('city', 'Paris') } } }) }, ['Bob', 'Nina']],
        ['an or of a block and its negation', { -> names(NegationOwner.createCriteria().list { or { address { eq('city', 'Paris') }; address { ne('city', 'Paris') } } }) }, ['Amelie', 'Bob']],
        ['an and of a name and a negated block', { -> names(NegationOwner.createCriteria().list { and { eq('name', 'Nina'); address { ne('city', 'Paris') } } }) }, []],
        ['an and of a negated block and a name', { -> names(NegationOwner.createCriteria().list { and { address { ne('city', 'Paris') }; eq('name', 'Bob') } }) }, ['Bob']],
        ['a negated block', { -> names(NegationOwner.createCriteria().list { not { address { eq('city', 'Paris') } } }) }, ['Bob']],
        ['a count of a negated block', { -> NegationOwner.createCriteria().count { address { ne('city', 'Paris') } } }, 1],
    ]
}
