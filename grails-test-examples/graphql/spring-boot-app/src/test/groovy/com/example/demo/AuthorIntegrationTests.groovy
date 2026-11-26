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

package com.example.demo

import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.web.client.RestTemplate
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AuthorIntegrationTests extends Specification {

    @Shared
    @AutoCleanup
    ConfigurableApplicationContext context

    @Shared
    int port

    @Shared
    RestTemplate restTemplate = new RestTemplate()

    void setupSpec() {
        context = new SpringApplication(DemoApplication).run('--server.port=0')
        port = context.environment.getProperty('local.server.port', Integer)
    }

    void 'author list endpoint returns empty list'() {
        when:
        String body = restTemplate.postForObject(
                "http://localhost:${port}/graphql" as String,
                '{ authorList { id } }',
                String)

        then:
        body == '{"data":{"authorList":[]}}'
    }
}
