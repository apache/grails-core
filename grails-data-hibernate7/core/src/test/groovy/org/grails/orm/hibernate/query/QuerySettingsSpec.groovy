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

import org.hibernate.jpa.AvailableHints
import spock.lang.Specification

class QuerySettingsSpec extends Specification {

    void "creates query settings with empty defaults"() {
        when:
        def settings = QuerySettings.of(null, null)

        then:
        settings.settings().isEmpty()
        settings.hints().isEmpty()
        !settings.hasHints()
        !settings.hasSetting(HibernateQueryArgument.MAX)
        settings.getSetting(HibernateQueryArgument.MAX) == null
    }

    void "filters JPA hints to valid defined hints"() {
        given:
        def rawHints = [
                (AvailableHints.HINT_SPEC_FETCH_GRAPH): "fetchGraphObj",
                "invalid.custom.hint": "ignoredValue"
        ]

        when:
        def settings = QuerySettings.of([max: 25, offset: 5], rawHints)

        then:
        settings.hasHints()
        settings.hints() == [(AvailableHints.HINT_SPEC_FETCH_GRAPH): "fetchGraphObj"]
        !settings.hints().containsKey("invalid.custom.hint")
        settings.hasSetting("max")
        settings.getSetting("max") == 25
        settings.getSetting(HibernateQueryArgument.MAX) == 25
        settings.hasSetting(HibernateQueryArgument.OFFSET)
        settings.getSetting(HibernateQueryArgument.OFFSET) == 5
    }

    void "settings are defensively copied"() {
        given:
        def mutableSettings = [max: 10]
        def mutableHints = [(AvailableHints.HINT_SPEC_LOAD_GRAPH): "graph"]

        when:
        def settings = QuerySettings.of(mutableSettings, mutableHints)
        mutableSettings.put("max", 50)
        mutableSettings.put("newKey", "val")

        then:
        settings.getSetting("max") == 10
        !settings.hasSetting("newKey")
    }
}
