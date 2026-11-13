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

import java.net.http.HttpRequest

import functional.test.app.Application
import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.TestHttpResponse
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Specification

@IgnoreIf({ System.getProperty('TESTCONFIG') != 'inactive' })
@Issue('https://github.com/apache/grails-core/issues/15880')
@Integration(applicationClass = Application)
class InactiveSecurityTagLibSpec extends Specification implements HttpClientSupport {

    void 'a page with sec tags renders when the plugin is inactive'() {
        when: 'a page that uses every kind of sec tag is requested with grails.plugin.springsecurity.active = false'
        TestHttpResponse response = sendHttpRequest(HttpRequest.newBuilder(URI.create("${httpBaseUrl}/miscTest/test")).GET().build())

        then: 'the tags render nothing instead of failing on a missing security bean'
        response.statusCode() == 200
        !response.body().contains('Error executing tag')
        !response.body().contains('roleHierarchy')
        !response.body().contains('user and admin')
        !response.body().contains('user or admin')
    }
}
