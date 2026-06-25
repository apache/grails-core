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
package functionaltests.marshaller

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Tag

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Functional tests verifying that Date and Calendar objects are marshalled
 * through the Grails JSON and XML converters using the JDK's ISO formatters.
 *
 * - JSON marshallers use {@link DateTimeFormatter#ISO_INSTANT} (UTC, "Z" suffix).
 * - XML marshaller uses {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME} in the
 *   system default zone (numeric offset, e.g. "+00:00", "-04:00").
 */
@Integration
@Tag('http-client')
@Narrative('''
Grails converters marshal Date and Calendar objects using the JDK's standard
ISO formatters. JSON output is RFC 3339 / ISO 8601 in UTC. XML output is
ISO 8601 offset date-time in the system default zone.
''')
class DateMarshallerSpec extends Specification implements HttpClientSupport {

    private static final Map<String, String> ACCEPT_JSON = [Accept: 'application/json']
    private static final Map<String, String> ACCEPT_XML = [Accept: 'application/xml']

    // ========== JSON Date Marshalling ==========

    def "Date at epoch is marshalled to ISO_INSTANT in JSON"() {
        when:
        def response = http(ACCEPT_JSON, '/dateMarshaller/date')

        then: "ISO_INSTANT drops the fraction on whole-second instants"
        response.assertJson(200, [dateField: '1970-01-01T00:00:00Z'])
    }

    def "Date with milliseconds renders fraction to .SSS in JSON"() {
        when:
        def response = http(ACCEPT_JSON, '/dateMarshaller/dateWithMillis')

        then:
        response.assertJson(200, [dateField: '2009-02-13T23:31:30.123Z'])
    }

    // ========== JSON Calendar Marshalling ==========

    def "Calendar at epoch is marshalled to ISO_INSTANT in JSON"() {
        when:
        def response = http(ACCEPT_JSON, '/dateMarshaller/calendar')

        then:
        response.assertJson(200, [calField: '1970-01-01T00:00:00Z'])
    }

    // ========== XML Date Marshalling ==========

    def "Date at epoch is marshalled as ISO_OFFSET_DATE_TIME in XML"() {
        given:
        def expectedDate = xmlFormatter().format(Instant.EPOCH)

        when:
        def response = http(ACCEPT_XML, '/dateMarshaller/date')

        then:
        response.assertContains(200, expectedDate)
    }

    def "Date with milliseconds renders fraction in XML"() {
        given:
        def expectedDate = xmlFormatter().format(Instant.ofEpochMilli(1234567890123L))

        when:
        def response = http(ACCEPT_XML, '/dateMarshaller/dateWithMillis')

        then:
        response.assertContains(200, expectedDate)
    }

    // ========== URL Extension Format ==========

    def "Date JSON via .json URL extension"() {
        when:
        def response = http('/dateMarshaller/date.json')

        then:
        response.assertJson(200, [dateField: '1970-01-01T00:00:00Z'])
    }

    def "Date XML via .xml URL extension"() {
        given:
        def expectedDate = xmlFormatter().format(Instant.EPOCH)

        when:
        def response = http('/dateMarshaller/date.xml')

        then:
        response.assertContains(200, expectedDate)
    }

    private static DateTimeFormatter xmlFormatter() {
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())
    }
}
