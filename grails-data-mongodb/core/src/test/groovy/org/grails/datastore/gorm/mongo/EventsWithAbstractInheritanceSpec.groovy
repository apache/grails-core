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
import spock.lang.Issue

/**
 * Created by graemerocher on 21/04/16.
 */
class EventsWithAbstractInheritanceSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([ConcreteEventDomain])
    }

    @Issue('https://github.com/grails/grails-data-mapping/issues/701')
    def 'Test that events work with abstract inheritance'() {
        when: "An entity is saved"
        ConcreteEventDomain ced = new ConcreteEventDomain(name: "Bob").save(flush: true)

        then: "An event listener inherited from the base class is fired"
        ced.eventCount('beforeInsert') == 1

        when: "An an instance is updated"
        ced.name = "Fred"
        ced.save(flush: true)

        then: "The beforeUpdate event listener is fired"
        ced.eventCount('beforeInsert') == 1
        ced.eventCount('beforeUpdate') == 1

        when: "An an instance is updated again"
        ced.name = "Joe"
        ced.save(flush: true)

        then: "The beforeUpdate event listener is fired"
        ced.eventCount('beforeInsert') == 1
        ced.eventCount('beforeUpdate') == 2
    }
}

@Entity
class ConcreteEventDomain extends AbstractEventDomain {
    String name
}

abstract class AbstractEventDomain {
    private Map<String, Integer> eventCount = [:].withDefault {
        0
    }

    int eventCount(String name) {
        eventCount[name]
    }

    boolean beforeInsert() {
        eventCount['beforeInsert']++
        return true
    }

    boolean beforeUpdate() {
        eventCount['beforeUpdate']++
        return true
    }
}
