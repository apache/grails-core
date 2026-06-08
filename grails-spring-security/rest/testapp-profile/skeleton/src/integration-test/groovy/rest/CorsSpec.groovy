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
package rest

import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpOptions
import org.apache.http.impl.client.DefaultHttpClient
import spock.lang.Ignore

/**
 * Specification to test CORS support
 *
 * @see https://github.com/grails/grails-spring-security-rest/issues/4
 */
//FIXME
@Ignore
class CorsSpec extends AbstractRestSpec {

    void "OPTIONS requests are allowed"() {

        given:
        HttpClient client = new DefaultHttpClient()
        HttpOptions options = new HttpOptions("${baseUrl}/api/login")
        options.addHeader 'Origin', 'http://www.example.com'
        options.addHeader 'Access-Control-Request-Method', 'POST'

        when:
        HttpResponse response = client.execute(options)

        then:
        response.getHeaders('Access-Control-Allow-Origin').first().value == 'http://www.example.com'

    }

}