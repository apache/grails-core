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
package org.grails.forge

import groovy.transform.CompileStatic
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Import
import grails.plugins.Plugin

/**
 * The application generator as a Grails plugin: the feature catalogue, the project generator and the
 * templates, so that the hosted generator ({@code grails-forge-web}) and the command line generator
 * ({@code grails-forge-cli}) are two Grails applications sharing one implementation.
 *
 * <p>The generator beans are Spring components under {@code org.grails.forge}, registered by the
 * {@link ForgeCoreConfiguration} this descriptor imports as a Spring Boot auto-configuration.</p>
 */
@CompileStatic
@AutoConfiguration
@Import(ForgeCoreConfiguration)
class ForgeGrailsPlugin extends Plugin {

    def grailsVersion = '8.1.0-SNAPSHOT > *'
    def author = 'Apache Grails Team'
    def title = 'Grails Forge'
    def description = 'Generates Grails applications: the feature catalogue and project generator shared by the Forge web service and the Forge CLI'
    def documentation = 'https://grails.apache.org/'
    def license = 'Apache 2.0 License'
    def organization = [name: 'Apache Grails', url: 'https://grails.apache.org/']
    def issueManagement = [system: 'GitHub', url: 'https://github.com/apache/grails-core/issues']
    def scm = [url: 'https://github.com/apache/grails-core']

}
