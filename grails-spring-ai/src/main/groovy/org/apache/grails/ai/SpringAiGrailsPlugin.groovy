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
package org.apache.grails.ai

import groovy.transform.CompileStatic

import org.springframework.boot.autoconfigure.AutoConfiguration

import grails.plugins.Plugin
import org.apache.grails.ai.async.ChatResponseStreamItemRenderer

/**
 * Integrates Spring AI with a Grails application.
 *
 * <p>The plugin brings the Spring AI chat client and its auto-configuration, so an application adds one
 * provider starter and injects a {@code ChatClient.Builder}; it ships the defaults in its
 * {@code plugin.yml}; and, with grails-async, it renders the chat responses an action streams as the
 * text of server-sent events.</p>
 *
 * @since 8.1
 */
@CompileStatic
@AutoConfiguration
class SpringAiGrailsPlugin extends Plugin {

    def grailsVersion = '8.1.0-SNAPSHOT > *'
    def author = 'Apache Grails Team'
    def title = 'Grails Spring AI Plugin'
    def description = 'Integrates Spring AI with Grails applications'
    def documentation = 'https://grails.apache.org/docs/latest/guide/usingAi.html'
    def license = 'Apache 2.0 License'

    def beans = {
        // The renderer's signature names a grails-async type, so the guard sits on a class that is never
        // parsed when grails-async is absent rather than on the bean (see GrailsBeans).
        group('streaming').conditionalOnClass('grails.async.web.StreamItemRenderer') {
            bean('aiChatResponseStreamItemRenderer', ChatResponseStreamItemRenderer).conditionalOnMissingBean()
        }
    }

}
