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

package specs

import grails.testing.mixin.integration.Integration
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import spock.lang.IgnoreIf
import spock.lang.Issue

@IgnoreIf({ System.getProperty('TESTCONFIG') != 'issue503' })
@Issue('https://github.com/apache/grails-spring-security/issues/503')
@Integration(applicationClass = functional.test.app.Application)
class CustomFilterRegistrationSpec extends HttpClientSpec {

    void 'GET request to /assets/spinner.gif should not throw error because custom filter is excluded'() {
        when: "A GET request to the assets directory is made"
        HttpResponse response = client.exchange(HttpRequest.GET("/assets/spinner.gif"))

        then: "the filter is not invoked because of the chainMap definition of filters: 'none' in application.groovy"
        response.status == HttpStatus.OK
    }
}
