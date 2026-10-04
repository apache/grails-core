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
package org.grails.datastore.gorm.timestamp

import spock.lang.Specification

class AggregateTimestampProviderSpec extends Specification {

    AggregateTimestampProvider aggregateTimestampProvider = new AggregateTimestampProvider()

    void "getTimestampProviders defaults to an empty list"() {
        expect:
        aggregateTimestampProvider.timestampProviders == []
    }

    void "setTimestampProviders stores the given providers"() {
        given:
        TimestampProvider dateProvider = Stub(TimestampProvider)
        TimestampProvider stringProvider = Stub(TimestampProvider)

        when:
        aggregateTimestampProvider.timestampProviders = [dateProvider, stringProvider]

        then:
        aggregateTimestampProvider.timestampProviders == [dateProvider, stringProvider]
    }

    void "supportsCreating returns true when any delegate provider supports the class"() {
        given:
        TimestampProvider dateProvider = Stub(TimestampProvider) {
            supportsCreating(Date) >> false
        }
        TimestampProvider stringProvider = Stub(TimestampProvider) {
            supportsCreating(Date) >> true
        }
        aggregateTimestampProvider.timestampProviders = [dateProvider, stringProvider]

        expect:
        aggregateTimestampProvider.supportsCreating(Date)
    }

    void "supportsCreating returns false when no delegate provider supports the class"() {
        given:
        TimestampProvider dateProvider = Stub(TimestampProvider) {
            supportsCreating(_) >> false
        }
        aggregateTimestampProvider.timestampProviders = [dateProvider]

        expect:
        !aggregateTimestampProvider.supportsCreating(Date)
    }

    void "supportsCreating returns false when there are no delegate providers"() {
        expect:
        !aggregateTimestampProvider.supportsCreating(Date)
    }

    void "createTimestamp delegates directly to the single registered provider"() {
        given:
        Date timestamp = new Date()
        TimestampProvider onlyProvider = Stub(TimestampProvider) {
            createTimestamp(Date) >> timestamp
        }
        aggregateTimestampProvider.timestampProviders = [onlyProvider]

        expect:
        aggregateTimestampProvider.createTimestamp(Date) == timestamp
    }

    void "createTimestamp with multiple providers delegates to the first provider that supports the class"() {
        given:
        Date timestamp = new Date()
        TimestampProvider unsupportingProvider = Stub(TimestampProvider) {
            supportsCreating(Date) >> false
        }
        TimestampProvider supportingProvider = Stub(TimestampProvider) {
            supportsCreating(Date) >> true
            createTimestamp(Date) >> timestamp
        }
        aggregateTimestampProvider.timestampProviders = [unsupportingProvider, supportingProvider]

        expect:
        aggregateTimestampProvider.createTimestamp(Date) == timestamp
    }

    void "createTimestamp with multiple providers throws when none support the class"() {
        given:
        TimestampProvider firstProvider = Stub(TimestampProvider) {
            supportsCreating(Date) >> false
        }
        TimestampProvider secondProvider = Stub(TimestampProvider) {
            supportsCreating(Date) >> false
        }
        aggregateTimestampProvider.timestampProviders = [firstProvider, secondProvider]

        when:
        aggregateTimestampProvider.createTimestamp(Date)

        then:
        thrown(IllegalArgumentException)
    }

    void "createTimestamp with no registered providers throws"() {
        when:
        aggregateTimestampProvider.createTimestamp(Date)

        then:
        thrown(NoSuchElementException)
    }
}
