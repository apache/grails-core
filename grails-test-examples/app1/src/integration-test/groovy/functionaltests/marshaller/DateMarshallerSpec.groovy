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
 * through the Grails JSON and XML converters using DateTimeFormatter.
 *
 * These tests exercise the DateTimeFormatter-based marshallers:
 * - org.grails.web.converters.marshaller.json.DateMarshaller (ISO 8601 UTC)
 * - org.grails.web.converters.marshaller.json.CalendarMarshaller (ISO 8601 UTC)
 * - org.grails.web.converters.marshaller.xml.DateMarshaller (system default timezone)
 *
 * JSON format: yyyy-MM-dd'T'HH:mm:ss.SSS'Z' (always UTC)
 * XML format: yyyy-MM-dd HH:mm:ss.SSS z (system default timezone)
 */
@Integration
@Narrative('''
Grails converters marshal Date and Calendar objects using DateTimeFormatter.
JSON output uses ISO 8601 UTC format. XML output uses system default timezone.
Both produce consistent 3-digit millisecond formatting (.000, .123, etc.).
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

    def "Date at epoch is marshalled to ISO 8601 UTC in JSON"() {
        when: "requesting date endpoint with Accept: application/json"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/dateMarshaller/date')
                .accept(MediaType.APPLICATION_JSON),
            String
        )

        then: "response contains date in yyyy-MM-dd'T'HH:mm:ss.SSS'Z' format"
        response.status == HttpStatus.OK

        and: "date value is epoch in UTC"
        def json = new JsonSlurper().parseText(response.body())
        json.dateField == '1970-01-01T00:00:00.000Z'
    }

    def "Date with milliseconds uses 3-digit padded format in JSON"() {
        when: "requesting dateWithMillis endpoint with Accept: application/json"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/dateMarshaller/dateWithMillis')
                .accept(MediaType.APPLICATION_JSON),
            String
        )

        then: "response contains date with .123 milliseconds"
        response.status == HttpStatus.OK

        and: "milliseconds are 3-digit zero-padded"
        def json = new JsonSlurper().parseText(response.body())
        json.dateField == '2009-02-13T23:31:30.123Z'
    }

    // ========== JSON Calendar Marshalling ==========

    def "Calendar at epoch is marshalled to ISO 8601 UTC in JSON"() {
        when: "requesting calendar endpoint with Accept: application/json"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/dateMarshaller/calendar')
                .accept(MediaType.APPLICATION_JSON),
            String
        )

        then: "response contains calendar in ISO 8601 UTC format"
        response.status == HttpStatus.OK

        and: "calendar value is epoch in UTC"
        def json = new JsonSlurper().parseText(response.body())
        json.calField == '1970-01-01T00:00:00.000Z'
    }

    // ========== XML Date Marshalling ==========

    def "Date at epoch is marshalled in system default timezone in XML"() {
        given: "the expected formatted date using the same pattern as the XML marshaller"
        def expectedDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z")
                .withZone(ZoneId.systemDefault())
                .format(Instant.EPOCH)

        when: "requesting date endpoint with Accept: application/xml"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/dateMarshaller/date')
                .accept(MediaType.APPLICATION_XML),
            String
        )

        then: "response is XML containing date in system default timezone format"
        response.status == HttpStatus.OK
        response.body().contains(expectedDate)
    }

    def "Date with milliseconds uses 3-digit padded format in XML"() {
        given: "the expected formatted date"
        def expectedDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(1234567890123L))

        when: "requesting dateWithMillis endpoint with Accept: application/xml"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/dateMarshaller/dateWithMillis')
                .accept(MediaType.APPLICATION_XML),
            String
        )

        then: "response is XML containing date with .123 milliseconds"
        response.status == HttpStatus.OK
        response.body().contains(expectedDate)
    }

    // ========== URL Extension Format ==========

    def "Date JSON via .json URL extension"() {
        when: "requesting date with .json extension"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/dateMarshaller/date.json'),
            String
        )

        then: "response is JSON with correct date format"
        response.status == HttpStatus.OK

        and: "date is in ISO 8601 UTC format"
        def json = new JsonSlurper().parseText(response.body())
        json.dateField == '1970-01-01T00:00:00.000Z'
    }

    def "Date XML via .xml URL extension"() {
        given: "the expected formatted date"
        def expectedDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z")
                .withZone(ZoneId.systemDefault())
                .format(Instant.EPOCH)

        when: "requesting date with .xml extension"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/dateMarshaller/date.xml'),
            String
        )

        then: "response is XML with correct date format"
        response.status == HttpStatus.OK
        response.body().contains(expectedDate)
    }
}
