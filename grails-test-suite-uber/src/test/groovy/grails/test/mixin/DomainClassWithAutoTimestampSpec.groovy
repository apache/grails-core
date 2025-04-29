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
package grails.test.mixin

import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

/**
 * Tests the population of auto timestamp properties.
 */
class DomainClassWithAutoTimestampSpec extends Specification implements DomainUnitTest<DomainWithAutoTimestamp> {

    void "test that auto timestamp properties are populated on insert"() {
        when: "we persist a new instance"
            DomainWithAutoTimestamp d = new DomainWithAutoTimestamp(name: "foo")
            d.save(flush: true)

        then: "the auto timestamp properties are populated"
            d.dateCreated != null
            d.lastUpdated != null
            d.dateCreated == d.lastUpdated
    }

    void "test that auto timestamp properties are populated on update"() {
        given: "an existing persisted instance"
            DomainWithAutoTimestamp d = new DomainWithAutoTimestamp(name: "foo")
            d.save(flush: true)
            Date dateCreated = d.dateCreated
            Date lastUpdated = d.lastUpdated

        when: "we update the instance instance"
            // Wait at least 10 ms to get a different lastUpdated timestamp
            sleep(10)
            d.name = "foobar"
            d.save(flush: true)

        then: "the auto timestamp properties are populated"
            d.dateCreated != null
            d.dateCreated == dateCreated
            d.lastUpdated != null
            d.lastUpdated != lastUpdated
    }
}

@Entity
class DomainWithAutoTimestamp {
    String name
    Date dateCreated
    Date lastUpdated
}
