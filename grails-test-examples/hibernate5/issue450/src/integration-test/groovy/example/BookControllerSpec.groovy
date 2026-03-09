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
package example

import spock.lang.Specification

import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClient

@Integration
class BookControllerSpec extends Specification {

    @Autowired HttpClient http

    void 'test books can be fetched'() {
        expect:
        http.get('/book/grails').expectContains('The definitive Guide to Grails 2')
        http.get('/book/grails').expectNotBodyContains('Groovy in Action')

        http.get('/book/groovy').expectContains('Groovy in Action')
        http.get('/book/groovy').expectNotBodyContains('The definitive Guide to Grails 2')
    }
}
