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

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Stepwise

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

@Stepwise
@Integration
class MultiDataSourceWithSessionSpec extends Specification implements HttpClientSupport {

    @Issue('https://github.com/apache/grails-core/issues/14333')
    void "withSession on secondary datasource does not throw No Session found"() {
        when:
        def response = http('/secondaryBook/withSessionTest')

        then:
        response.assertContains('sessionObtained:true')
    }

    @Issue('https://github.com/apache/grails-core/issues/14333')
    void "CRUD via withSession on secondary datasource works"() {
        when:
        def response = http('/secondaryBook/crudViaWithSession')

        then:
        response.assertContains('count:1')

        cleanup:
        http('/secondaryBook/cleanup')
    }

    @Issue('https://github.com/apache/grails-core/issues/11798')
    void "domain class on secondary datasource can be validated via withSession"() {
        when:
        def response = http('/secondaryBook/validateCommandObject')

        then:
        response.assertContains('validated:true')
                .assertContains('hasErrors:true')
    }

    @Issue('https://github.com/apache/grails-core/issues/14333')
    void "withSession works after executeUpdate on secondary datasource"() {
        when:
        def response = http('/secondaryBook/sessionAfterExecuteUpdate')

        then:
        response.assertContains('title:After Update')

        cleanup:
        http('/secondaryBook/cleanup')
    }
}
