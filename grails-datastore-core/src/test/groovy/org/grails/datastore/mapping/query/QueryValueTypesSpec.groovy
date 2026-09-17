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

import spock.lang.Specification

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion
import org.grails.datastore.mapping.query.event.PostQueryEvent
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.grails.datastore.mapping.query.event.QueryEventType
import org.grails.datastore.mapping.query.jpa.JpaQueryInfo

class QueryValueTypesSpec extends Specification {

    Datastore datastore = Stub(Datastore)
    Session session = Stub(Session) { getDatastore() >> datastore }
    Query query = Stub(Query) { getSession() >> session }

    void "query events default their source to the query's datastore"() {
        when:
        PreQueryEvent pre = new PreQueryEvent(query)
        PostQueryEvent post = new PostQueryEvent(query, ['r'])

        then:
        pre.source.is(datastore)
        pre.query.is(query)
        pre.eventType == QueryEventType.PreExecution
        post.source.is(datastore)
        post.query.is(query)
        post.results == ['r']
        post.eventType == QueryEventType.PostExecution
        QueryEventType.values()*.name() == ['PreExecution', 'PostExecution']
    }

    void "query events accept an explicit source"() {
        expect:
        new PreQueryEvent('src', query).source == 'src'
        new PostQueryEvent('src', query, []).source == 'src'
    }

    void "post query event results can be replaced but not nulled"() {
        given:
        PostQueryEvent post = new PostQueryEvent(query, ['r'])

        when:
        post.results = ['s']

        then:
        post.results == ['s']

        when:
        post.results = null

        then:
        IllegalArgumentException e = thrown()
        e.message == 'results must be non-null'
    }

    void "function calling criteria wrap a property criterion"() {
        given:
        Query.PropertyCriterion criterion = new Query.Equals('name', 'x')

        when:
        FunctionCallingCriterion onProperty = new FunctionCallingCriterion('lower', criterion)
        FunctionCallingCriterion onValue = new FunctionCallingCriterion('other', 'upper', criterion, true)

        then:
        onProperty.property == 'name'
        onProperty.functionName == 'lower'
        onProperty.propertyCriterion.is(criterion)
        !onProperty.onValue
        onValue.property == 'other'
        onValue.functionName == 'upper'
        onValue.onValue
    }

    void "jpa query info and query exceptions carry their state"() {
        given:
        Throwable cause = new IllegalStateException('cause')

        expect:
        new JpaQueryInfo('q', [1]).query == 'q'
        new JpaQueryInfo('q', [1]).parameters == [1]
        new QueryException('m').message == 'm'
        new QueryException('m', cause).cause.is(cause)
    }
}
