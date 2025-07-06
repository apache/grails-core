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

package functionaltests.layout

import functionaltests.Application
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import spock.lang.Issue
import spock.lang.PendingFeature

@Integration(applicationClass = Application)
class LayoutFunctionalSpec extends ContainerGebSpec {

    @Issue('GRAILS-12045')
    @PendingFeature(reason = 'title is empty')
    void 'test layout by convention'() {
        when:
        go('/layoutByConvention')

        then:
        title == 'Convention Layout'
    }

    @Issue('GRAILS-12045')
    @PendingFeature(reason='title is empty')
    void 'test layout specified in controller property'() {
        when:
        go('/layoutSpecifiedByProperty')

        then:
        title == 'Foo Layout'

    }

    @Issue('GRAILS-12045')
    @PendingFeature(reason='text does not contain the value')
    void 'test layout specified in controller property applied to a GSP that does not contain a root html tag'() {
        when:
        go('/layoutSpecifiedByProperty/snippetView')

        then:
        title  == 'Foo Layout'
        $().text().contains 'this is some content'
    }
}
