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

package functionaltests

import datasources.Application
import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore
import io.micronaut.http.client.HttpClient
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@Integration(applicationClass = Application)
@Stepwise
class MultiDataSourceWithSessionSpec extends Specification {

    @Shared
    HttpClient client

    @OnceBefore
    void init() {
        String baseUrl = "http://localhost:$serverPort"
        this.client = HttpClient.create(baseUrl.toURL())
    }

    @Issue('https://github.com/apache/grails-core/issues/14333')
    void "withSession on secondary datasource does not throw No Session found"() {
        when:
        String response = client.toBlocking().retrieve('/secondaryBook/withSessionTest')

        then:
        response.contains('sessionObtained:true')
    }

    @Issue('https://github.com/apache/grails-core/issues/14333')
    void "CRUD via withSession on secondary datasource works"() {
        when:
        String response = client.toBlocking().retrieve('/secondaryBook/crudViaWithSession')

        then:
        response.contains('count:1')

        cleanup:
        client.toBlocking().retrieve('/secondaryBook/cleanup')
    }

    @Issue('https://github.com/apache/grails-core/issues/11798')
    void "domain class on secondary datasource can be validated via withSession"() {
        when:
        String response = client.toBlocking().retrieve('/secondaryBook/validateCommandObject')

        then:
        response.contains('validated:true')
        response.contains('hasErrors:true')
    }

    @Issue('https://github.com/apache/grails-core/issues/14333')
    void "withSession works after executeUpdate on secondary datasource"() {
        when:
        String response = client.toBlocking().retrieve('/secondaryBook/sessionAfterExecuteUpdate')

        then:
        response.contains('title:After Update')

        cleanup:
        client.toBlocking().retrieve('/secondaryBook/cleanup')
    }
}
