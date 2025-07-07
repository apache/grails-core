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
class InspectConfigControllerSpec extends ContainerGebSpec {

    @Issue('GRAILS-11951')
    void "test config properties from plugins"() {
        when:
        go('/inspectConfig/showPropertyValues')

        then:
        $('div', 0).text() == 'Prop One Defined By LoadFirst Plugin'
        $('div', 1).text() == 'Prop Two Defined By LoadSecond Plugin'
        $('div', 2).text() == 'Prop Three Defined By app1 Application'
        $('div', 3).text() == 'Prop One Defined By LoadFirst Plugin'
        $('div', 4).text() == 'Prop Two Defined By LoadSecond Plugin'
        $('div', 5).text() == 'Prop Three Defined By app1 Application'
        $('div', 6).text() == 'Prop One Defined By LoadFirst Plugin'
        $('div', 7).text() == 'Prop Two Defined By LoadSecond Plugin'
        $('div', 8).text() == 'Prop Three Defined By app1 Application'
    }
}
