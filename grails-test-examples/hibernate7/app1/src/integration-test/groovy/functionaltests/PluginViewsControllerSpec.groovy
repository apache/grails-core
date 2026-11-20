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

import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

/**
 */
@Integration(applicationClass = Application)
@Rollback
class PluginViewsControllerSpec extends ContainerGebSpec {

    void "Test that when plugin templates are rendered they render correctly"() {
        when:"A view that renders plugin templates is visisted"
        go('/pluginViews/testTemplateFromPlugin')

        then:"The title is correct"
        	$('div', 0).text() == "Template from plugin: Hello from plugin"
            $('div', 1).text() == "Template from plugin no plugin attribute: Hello from app"
    }

    void "Test that views from a plugin loaded after another plugin override when using loadAfter"() {
        when:"A view that renders plugin views"
        go('/pluginViews/testPluginViewOverrideInPlugin')

        then:"The title is correct"
            $('h1', 0).text() == "Second Plugin"
            
    }    
}
