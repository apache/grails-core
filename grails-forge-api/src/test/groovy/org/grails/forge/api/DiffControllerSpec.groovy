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

package org.grails.forge.api


import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.grails.forge.application.ApplicationType
import spock.lang.Specification

@MicronautTest
class DiffControllerSpec extends Specification {

    @Inject
    DiffClient diffClient

    void 'test diff app'() {
        when:
        def result = diffClient.diffApp(
                ApplicationType.WEB,
                "test",
                ["gorm-mongodb"],
                null,
                null,
                null,
                null
        )

        then:
        result.contains('+## Feature gorm-mongodb documentation')
    }

    void 'test diff feature'() {
        when:
        def result = diffClient.diffFeature(
                ApplicationType.WEB,
                null,
                "gorm-mongodb",
                null,
                null,
                null,
                null
        )

        then:
        result.contains('+## Feature gorm-mongodb documentation')
    }

    void 'test diff invalid feature'() {
        when:
        diffClient.diffFeature(
                ApplicationType.WEB,
                null,
                "junkkkkk",
                null,
                null,
                null,
                null
        )

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
        e.getResponse().getBody(Map).get()._embedded.errors[0].message == 'The requested feature does not exist: junkkkkk'
    }
}
