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

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import spock.lang.Issue

@Integration(applicationClass = Application)
class ValidationFunctionalSpec extends ContainerGebSpec {

    @Issue('GRAILS-12039')
    void "Test validation of an invalid Hibernate entity"() {
        when:
        go '/book/validateBook'

        then:
        $().text() == 'The validate method returned false'
    }

    @Issue('GRAILS-12039')
    void "Test validation of a valid Hibernate entity"() {
        when:
        go '/book/validateBook?title=DGG'

        then:
        $().text() == 'The validate method returned true'
    }
}
