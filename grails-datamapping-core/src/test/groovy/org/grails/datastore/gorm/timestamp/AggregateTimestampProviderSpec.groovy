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

    void "test supportsCreating returns false when no delegate providers are configured"() {
        given:
        def provider = new AggregateTimestampProvider()

        expect:
        !provider.supportsCreating(Date)
    }

    void "test supportsCreating returns true when any delegate provider supports the type"() {
        given:
        def provider = new AggregateTimestampProvider()
        provider.timestampProviders = [stubProvider(false), stubProvider(true)]

        expect:
        provider.supportsCreating(Date)
    }

    void "test createTimestamp delegates to the sole provider when only one is configured"() {
        given:
        def provider = new AggregateTimestampProvider()
        def sole = new DefaultTimestampProvider()
        provider.timestampProviders = [sole]

        expect:
        provider.createTimestamp(Date) instanceof Date
    }

    void "test createTimestamp delegates to the first provider that supports the type when several are configured"() {
        given:
        def provider = new AggregateTimestampProvider()
        provider.timestampProviders = [stubProvider(false), new DefaultTimestampProvider()]

        expect:
        provider.createTimestamp(Date) instanceof Date
    }

    void "test createTimestamp throws when no configured provider supports the type"() {
        given:
        def provider = new AggregateTimestampProvider()
        provider.timestampProviders = [stubProvider(false), stubProvider(false)]

        when:
        provider.createTimestamp(Date)

        then:
        thrown(IllegalArgumentException)
    }

    void "test getTimestampProviders returns an empty list by default"() {
        expect:
        new AggregateTimestampProvider().timestampProviders == []
    }

    private static TimestampProvider stubProvider(boolean supports) {
        new TimestampProvider() {
            @Override
            boolean supportsCreating(Class<?> dateTimeClass) {
                return supports
            }

            @Override
            def <T> T createTimestamp(Class<T> dateTimeClass) {
                throw new UnsupportedOperationException()
            }
        }
    }
}
