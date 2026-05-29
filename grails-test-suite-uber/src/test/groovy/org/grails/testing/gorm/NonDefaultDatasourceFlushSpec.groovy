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
package org.grails.testing.gorm

import grails.gorm.annotation.Entity
import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

/**
 * A domain mapped to a non-default {@code datasource} resolves to a dedicated per-connection child
 * datastore under the single shared GormRegistry. The unit-test harness must bind a session for that
 * connection so that {@code save()} without an explicit flush is observed by a later auto-flushing
 * query, exactly as it is for default-datasource domains.
 */
class NonDefaultDatasourceFlushSpec extends Specification implements DomainUnitTest<Widget> {

    @Override
    Closure doWithConfig() {
        { config ->
            config.dataSources = [secondDb: [:]]
        }
    }

    void "save() without flush on a non-default-datasource domain is visible to an auto-flushing query"() {
        when: 'an entity mapped to a non-default datasource is saved without an explicit flush'
        new Widget(name: 'one').save()

        then: 'the auto-flushing count() query observes the persisted instance'
        Widget.count() == 1
    }

    void "save() without flush on a non-default-datasource domain is retrievable and listable"() {
        when:
        def widget = new Widget(name: 'two').save()

        then:
        widget.id != null
        Widget.get(widget.id) != null
        Widget.list().size() == 1
    }
}

@Entity
class Widget {
    String name

    static mapping = {
        datasource 'secondDb'
    }
}
