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

package org.apache.grails.undertow.plugin

import groovy.transform.CompileStatic

import grails.plugins.Plugin

/**
 * Plugin that runs Grails applications on the Undertow embedded servlet container.
 *
 * The actual web server wiring is provided by the vendored Spring Boot Undertow
 * autoconfiguration in the grails-undertow-spring-boot module; this plugin exists
 * to bring that support onto the application classpath as a Grails plugin.
 *
 * @since 8.0
 */
@CompileStatic
class UndertowGrailsPlugin extends Plugin {

    def grailsVersion = '8.0.0-SNAPSHOT > *'

    def author = 'Grails Core Team'
    def title = 'Undertow for Grails'
    def description = 'Runs Grails applications on the Undertow embedded servlet container'
    def documentation = 'https://grails.apache.org/docs/latest/guide/single.html'

}
