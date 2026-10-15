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

import groovy.yaml.YamlSlurper

import spock.lang.Specification

/**
 * The defaults the plugin ships are what the AI chapter of the guide documents, so they have to stay in step.
 */
class SpringAiPluginDefaultsSpec extends Specification {

    private static Map defaults() {
        (Map) new YamlSlurper().parse(new File('grails-app/conf/plugin.yml'))
    }

    void 'a failed model call is retried a few times, not the ten Spring AI defaults to'() {
        expect:
        defaults().spring.ai.retry['max-attempts'] == 3
    }

    void 'prompts and completions are logged in development only'() {
        given:
        Map environments = (Map) defaults().environments

        expect:
        environments.development.spring.ai.chat.client.observations['log-prompt'] == true
        environments.development.spring.ai.chat.client.observations['log-completion'] == true
        environments.development.spring.ai.chat.observations['include-error-logging'] == true
        !environments.containsKey('production')
        !environments.containsKey('test')
    }

    void 'no provider or credential is chosen for the application'() {
        expect:
        !defaults().spring.ai.containsKey('model')
        !defaults().spring.ai.containsKey('openai')
    }

}
