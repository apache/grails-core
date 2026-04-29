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

import groovy.json.JsonSlurper

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Specification

import grails.testing.mixin.integration.Integration

/**
 * Functional tests verifying that Date and Calendar objects are marshalled
 * through the Grails JSON and XML converters using the JDK's ISO formatters.
 *
 * - JSON marshallers use {@link DateTimeFormatter#ISO_INSTANT} (UTC, "Z" suffix).
 * - XML marshaller uses {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME} in the
 *   system default zone (numeric offset, e.g. "+00:00", "-04:00").
 */
@Integration
@Narrative('''
Grails converters marshal Date and Calendar objects using the JDK's standard
ISO formatters. JSON output is RFC 3339 / ISO 8601 in UTC. XML output is
ISO 8601 offset date-time in the system default zone.
''')
class DateMarshallerSpec extends Specification {

    @Shared
    HttpClient client

    def setup() {
        client = client ?: HttpClient.create(new URL("http://localhost:$serverPort"))
    }

    def cleanupSpec() {
        client.close()
    }

    // ========== JSON Date Marshalling ==========

    def "Date at epoch is marshalled to ISO_INSTANT in JSON"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/dateMarshaller/date')
                .accept(MediaType.APPLICATION_JSON),
            String
        )

        then:
        response.status == HttpStatus.OK

        and: "ISO_INSTANT drops the fraction on whole-second instants"
        def json = new JsonSlurper().parseText(response.body())
        json.dateField == '1970-01-01T00:00:00Z'
    }

    def "Date with milliseconds renders fraction to .SSS in JSON"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/dateMarshaller/dateWithMillis')
                .accept(MediaType.APPLICATION_JSON),
            String
        )

        then:
        response.status == HttpStatus.OK

        and:
        def json = new JsonSlurper().parseText(response.body())
        json.dateField == '2009-02-13T23:31:30.123Z'
    }

    // ========== JSON Calendar Marshalling ==========

    def "Calendar at epoch is marshalled to ISO_INSTANT in JSON"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/dateMarshaller/calendar')
                .accept(MediaType.APPLICATION_JSON),
            String
        )

        then:
        response.status == HttpStatus.OK

        and:
        def json = new JsonSlurper().parseText(response.body())
        json.calField == '1970-01-01T00:00:00Z'
    }

    // ========== XML Date Marshalling ==========

    def "Date at epoch is marshalled as ISO_OFFSET_DATE_TIME in XML"() {
        given:
        def expectedDate = xmlFormatter().format(Instant.EPOCH)

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/dateMarshaller/date')
                .accept(MediaType.APPLICATION_XML),
            String
        )

        then:
        response.status == HttpStatus.OK
        response.body().contains(expectedDate)
    }

    def "Date with milliseconds renders fraction in XML"() {
        given:
        def expectedDate = xmlFormatter().format(Instant.ofEpochMilli(1234567890123L))

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/dateMarshaller/dateWithMillis')
                .accept(MediaType.APPLICATION_XML),
            String
        )

        then:
        response.status == HttpStatus.OK
        response.body().contains(expectedDate)
    }

    // ========== URL Extension Format ==========

    def "Date JSON via .json URL extension"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/dateMarshaller/date.json'),
            String
        )

        then:
        response.status == HttpStatus.OK

        and:
        def json = new JsonSlurper().parseText(response.body())
        json.dateField == '1970-01-01T00:00:00Z'
    }

    def "Date XML via .xml URL extension"() {
        given:
        def expectedDate = xmlFormatter().format(Instant.EPOCH)

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/dateMarshaller/date.xml'),
            String
        )

        then:
        response.status == HttpStatus.OK
        response.body().contains(expectedDate)
    }

    private static DateTimeFormatter xmlFormatter() {
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())
    }
}
