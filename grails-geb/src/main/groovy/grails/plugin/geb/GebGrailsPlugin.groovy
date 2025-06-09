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

package grails.plugin.geb

import grails.plugins.Plugin
import grails.plugins.metadata.PluginSource
import groovy.transform.CompileStatic

@PluginSource
@CompileStatic
class GebGrailsPlugin extends Plugin {
    def grailsVersion = "7.0.0 > *"
    def pluginExcludes = []
    def title = "Grails Geb Plugin"
    def author = "Graeme Rocher"
    def authorEmail = ""
    def description = 'Plugin that adds Geb functional testing code generation features.'
    def documentation = "https://github.com/apache/grails-core/tree/HEAD/grails-geb#readme"
    def license = "APACHE"
    def issueManagement = [system: "Github Issues", url: "https://github.com/apache/grails-core/issues"]
    def scm = [url: "https://github.com/apache/grails-core"]
}
