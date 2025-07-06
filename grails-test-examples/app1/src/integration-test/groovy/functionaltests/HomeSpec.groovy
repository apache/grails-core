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
import spock.lang.PendingFeature

@Integration(applicationClass = Application)
class HomeSpec extends ContainerGebSpec {

    void "Test the home page renders correctly"() {
        when: "The home page is visited"
        go('/')
        if (title != "Welcome to Grails") {
            println pageSource
        }
        then: "The title is correct"
        title == "Welcome to Grails"
        $('li.controller', text: 'demo.AlphaController')
        $('li.controller', text: 'functionaltests.BookController')
        $('li.controller', text: 'functionaltests.ErrorsController')
        $('li.controller', text: 'functionaltests.ForwardingController')
        $('li.controller', text: 'functionaltests.InspectConfigController')
        $('li.controller', text: 'functionaltests.MiscController')
        $('li.controller', text: 'functionaltests.UploadController')
        $('li.controller', text: 'grails.functionaltests.InGrailsPackageController')

        // SimpleController should not become a controller as it is not in a grails-app/controllers dir
        !$('li.controller', text: 'functionaltests.SimpleController')

    }
}
