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
package grails.test.web

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Issue
import spock.lang.Specification

import java.text.SimpleDateFormat

@Issue('GRAILS-9196')
class GetHeadersFromResponseSpec extends Specification implements ControllerUnitTest<YourController> {
    def "Test inspection of response headers"() {

        when:"An action that sets response headers is called"
            controller.index()

        then:"It is possible to inspect the mock response"
            response.header('Cache-Control') == 'no-cache' // that's fine
            formatDate(0)  in  response.headers('Expires') // will throw the exception

    }

    private String formatDate(long date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(new Date(date));
    }
}

@Artefact('Controller')
class YourController {

    def index() {
        nocache(response)
        render ("foo")
    }

    void nocache(response) {
        response.setHeader('Cache-Control', 'no-cache') // HTTP 1.1
        response.addDateHeader('Expires', 0)
        response.setDateHeader('max-age', 0)
        response.addHeader('cache-Control', 'private')
    }
}