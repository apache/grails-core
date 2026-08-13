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

package org.grails.datastore.rx.query

import spock.lang.Specification

class QueryStateSpec extends Specification {

    void "test getLoadedEntity returns null when no entity of the given type has been loaded"() {
        given:
        QueryState queryState = new QueryState()

        expect:
        queryState.getLoadedEntity(String, "1") == null
    }

    void "test getLoadedEntity returns null when the type is known but the id is not"() {
        given:
        QueryState queryState = new QueryState()
        queryState.addLoadedEntity(String, "1", "one")

        expect:
        queryState.getLoadedEntity(String, "2") == null
    }

    void "test addLoadedEntity then getLoadedEntity returns the stored entity"() {
        given:
        QueryState queryState = new QueryState()

        when:
        queryState.addLoadedEntity(String, "1", "one")

        then:
        queryState.getLoadedEntity(String, "1") == "one"
    }

    void "test addLoadedEntity with a second id for an already known type reuses the existing type map"() {
        given:
        QueryState queryState = new QueryState()
        queryState.addLoadedEntity(String, "1", "one")

        when:
        queryState.addLoadedEntity(String, "2", "two")

        then:
        queryState.getLoadedEntity(String, "1") == "one"
        queryState.getLoadedEntity(String, "2") == "two"
    }

    void "test addLoadedEntity overwrites an entity previously stored for the same type and id"() {
        given:
        QueryState queryState = new QueryState()
        queryState.addLoadedEntity(String, "1", "one")

        when:
        queryState.addLoadedEntity(String, "1", "uno")

        then:
        queryState.getLoadedEntity(String, "1") == "uno"
    }

    void "test entities are tracked independently per type"() {
        given:
        QueryState queryState = new QueryState()

        when:
        queryState.addLoadedEntity(String, "1", "one")
        queryState.addLoadedEntity(Integer, "1", 1)

        then:
        queryState.getLoadedEntity(String, "1") == "one"
        queryState.getLoadedEntity(Integer, "1") == 1
    }
}
