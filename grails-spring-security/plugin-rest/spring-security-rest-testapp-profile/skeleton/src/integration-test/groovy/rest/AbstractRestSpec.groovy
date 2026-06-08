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

import grails.plugins.rest.client.RestBuilder
import grails.testing.mixin.integration.Integration
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Shared
import spock.lang.Specification

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT

@Integration
@SpringBootTest(webEnvironment = DEFINED_PORT)
abstract class AbstractRestSpec extends Specification {

    @Shared
    ConfigObject config = new ConfigSlurper().parse(new File('grails-app/conf/application.groovy').toURL())

    @Shared
    RestBuilder restBuilder = new RestBuilder()

    String getBaseUrl() {
        "http://localhost:8080"
    }

    def sendWrongCredentials() {
        if (config.grails.plugin.springsecurity.rest.login.useRequestParamsCredentials == true) {
            restBuilder.post("${baseUrl}/api/login?username=foo&password=bar")
        } else {
            restBuilder.post("${baseUrl}/api/login") {
                json {
                    username = 'foo'
                    password = 'bar'
                }
            }
        }
    }

    def sendCorrectCredentials(String u = 'jimi', String p = 'jimispassword') {
        if (config.grails.plugin.springsecurity.rest.login.useRequestParamsCredentials == true) {
            restBuilder.post("${baseUrl}/api/login?username=${u}&password=${p}")
        } else {
            restBuilder.post("${baseUrl}/api/login") {
                json {
                    username = u
                    password = p
                }
            }
        }
    }

}