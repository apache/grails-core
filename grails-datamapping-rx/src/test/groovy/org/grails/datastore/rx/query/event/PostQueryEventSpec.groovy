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
package org.grails.datastore.rx.query.event

import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.event.QueryEventType
import rx.Observable
import spock.lang.Specification

class PostQueryEventSpec extends Specification {

    void "constructor stores the source, query and observable"() {
        given:
        def source = new Object()
        def query = Mock(Query)
        def observable = Observable.empty()

        when:
        def event = new PostQueryEvent(source, query, observable)

        then:
        event.source.is(source)
        event.query.is(query)
        event.observable.is(observable)
    }

    void "getEventType returns PostExecution"() {
        given:
        def event = new PostQueryEvent(new Object(), Mock(Query), Observable.empty())

        expect:
        event.eventType == QueryEventType.PostExecution
    }
}
