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

import org.codehaus.groovy.grails.webflow.WebFlowPluginSupport

class WebflowGrailsPlugin {
    def version = "1.2-SNAPSHOT"
    def dependsOn = [core:"1.2 > *",i18n:"1.2 > *", controllers:"1.2 > *"]
    def observe = ['controllers']
    def loadAfter = ['hibernate']

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.2 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    def author = "Graeme Rocher"
    def authorEmail = "graeme.rocher@springsource.com"
    def title = "Spring Web Flow Plugin"
    def description = '''\\
Integrates Spring Web Flow with Grails
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/webflow"

    def doWithSpring = WebFlowPluginSupport.doWithSpring

    def doWithDynamicMethods = WebFlowPluginSupport.doWithDynamicMethods

    def doWithApplicationContext = WebFlowPluginSupport.doWithApplicationContext

    def onChange = WebFlowPluginSupport.onChange

}
