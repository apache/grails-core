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

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.springframework.dao.InvalidDataAccessResourceUsageException
import spock.lang.Unroll

/**
 * Verifies that a Map criterion value carrying a MongoDB operator key is rejected by every public
 * GORM query API, and that maps without such keys still compare as literal subdocuments.
 */
class MapCriterionValueSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    static final Map OPERATOR_MAP = ['$ne': 'x']

    void setupSpec() {
        manager.registerDomainClasses(ApiToken)
    }

    void setup() {
        new ApiToken(token: 'secret', uses: 42, status: Status.ACTIVE,
                attributes: [plan: 'gold', nested: [size: 'large']],
                items: [[sku: 'A'], [sku: 'B']]).save(flush: true)
        manager.session.clear()
    }

    @Unroll
    void "Test #label rejects a Map criterion value that carries an operator key"() {
        when: "A Map value is passed where a scalar is expected"
        query.call()

        then: "The query is refused"
        thrown(InvalidDataAccessResourceUsageException)

        where:
        label                     | query
        'findWhere'               | { ApiToken.findWhere(token: OPERATOR_MAP) }
        'findAllWhere'            | { ApiToken.findAllWhere(token: OPERATOR_MAP) }
        'criteria eq'             | { ApiToken.createCriteria().get { eq('token', OPERATOR_MAP) } }
        'criteria ne'             | { ApiToken.createCriteria().list { ne('token', OPERATOR_MAP) } }
        'criteria inList'         | { ApiToken.createCriteria().list { inList('uses', [1, OPERATOR_MAP]) } }
        'criteria idEq'           | { ApiToken.createCriteria().get { idEq(OPERATOR_MAP) } }
        'where DSL on id'         | { def m = OPERATOR_MAP; ApiToken.where { id == m }.list() }
        'findById'                | { ApiToken.findById(OPERATOR_MAP) }
        'where DSL'               | { def m = OPERATOR_MAP; ApiToken.where { token == m }.list() }
        'dynamic finder Integer'  | { ApiToken.findAllByUses(['$gt': 41]) }
        'dynamic finder gt'       | { ApiToken.findAllByUsesGreaterThan(['$gt': 41]) }
        'dynamic finder ne'       | { ApiToken.findAllByUsesNotEqual(['$gt': 41]) }
        'Map property operator'   | { ApiToken.findWhere(attributes: ['$exists': true]) }
        'negated eq'              | { ApiToken.createCriteria().list { not { eq('token', OPERATOR_MAP) } } }
        'enum findWhere'          | { ApiToken.findWhere(status: OPERATOR_MAP) }
        'enum criteria eq'        | { ApiToken.createCriteria().list { eq('status', OPERATOR_MAP) } }
        'enum criteria ne'        | { ApiToken.createCriteria().list { ne('status', OPERATOR_MAP) } }
        'enum where DSL'          | { def m = OPERATOR_MAP; ApiToken.where { status == m }.list() }
        'enum dynamic finder'     | { ApiToken.findAllByStatus(OPERATOR_MAP) }
        'enum inList'             | { ApiToken.createCriteria().list { inList('status', [OPERATOR_MAP]) } }
        'enum negated eq'         | { ApiToken.createCriteria().list { not { eq('status', OPERATOR_MAP) } } }
    }

    void "Test Map values without operator keys are sent as literal subdocument comparisons"() {
        expect: 'A dotted path into a Map property still matches'
        ApiToken.findWhere('attributes.nested': [size: 'large'])?.token == 'secret'
        ApiToken.createCriteria().get { eq('attributes.nested', [size: 'large']) } != null

        and: 'An array element match on a List property still matches'
        ApiToken.createCriteria().get { eq('items', [sku: 'A']) } != null
        ApiToken.createCriteria().get { eq('items', [sku: 'C']) } == null

        and: 'A map compared to a scalar property is a literal comparison that matches nothing'
        ApiToken.findWhere(token: [first: 'x']) == null
        ApiToken.findAllByUses([first: 1]).empty
    }

    void "Test enum values still query as before"() {
        expect:
        ApiToken.findWhere(status: Status.ACTIVE)?.token == 'secret'
        ApiToken.findWhere(status: Status.INACTIVE) == null
        ApiToken.findAllByStatusInList([Status.ACTIVE, Status.INACTIVE]).size() == 1
    }

    void "Test a dynamic finder on a String property converts a Map argument to a string"() {
        expect: "The map is stringified and does not match anything"
        ApiToken.findByToken(OPERATOR_MAP) == null
        ApiToken.findByToken('secret') != null
    }

    void "Test ordinary criterion values still match"() {
        expect:
        ApiToken.findWhere(token: 'secret')?.uses == 42
        ApiToken.findAllByUsesGreaterThan(41).size() == 1
        ApiToken.createCriteria().get { between('uses', 40, 45) } != null
        ApiToken.findWhere(attributes: [plan: 'gold', nested: [size: 'large']])?.token == 'secret'
        ApiToken.findWhere(attributes: [plan: 'gold']) == null
    }
}

@Entity
class ApiToken {
    String token
    Integer uses
    Status status
    Map attributes
    List items
}

enum Status {
    ACTIVE, INACTIVE
}
