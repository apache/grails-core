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
package org.grails.spring.context.support

import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import spock.lang.Specification

class PluginAwareResourceBundleMessageSourceSpec extends Specification {

    Resource applicationMessages
    Resource pluginMessages

    void setup() {
        applicationMessages = new TestResource('messages.properties', '''\
            foo=bar
            shared.message=Application Message
        '''.stripIndent().getBytes('UTF-8'))

        pluginMessages = new TestResource('somePlugin.properties', '''\
            shared.message=Plugin Message
        '''.stripIndent().getBytes('UTF-8'))
    }

    void 'basenames explicitly configured via setBasenames take priority over plugin-discovered basenames'() {
        given: 'a message source with an explicitly-configured basename, as a developer would via bean config'
        def messageSource = new PluginAwareResourceBundleMessageSource()
        messageSource.setResourceLoader(new DefaultResourceLoader() {
            Resource getResourceByPath(String path) {
                path.startsWith('messages') ? applicationMessages : pluginMessages
            }
        })
        messageSource.setSearchClasspath(true)
        messageSource.setResourceResolver(new PathMatchingResourcePatternResolver() {
            @Override
            Resource[] getResources(String locationPattern) {
                [pluginMessages] as Resource[]
            }
        })
        messageSource.setBasenames('messages')

        when: 'the plugin manager lifecycle discovers plugin bundles and merges them with the configured basename'
        messageSource.afterPropertiesSet()

        then: 'the explicitly configured application bundle still wins message resolution over the discovered plugin bundle'
        messageSource.getMessage('shared.message', null, Locale.default) == 'Application Message'
        messageSource.getMessage('foo', null, Locale.default) == 'bar'
    }

    void 'discovered plugin basenames are still merged in when no basename was explicitly configured'() {
        given: 'a message source relying purely on plugin discovery, as before any explicit basenames are set'
        def messageSource = new PluginAwareResourceBundleMessageSource()
        messageSource.setResourceLoader(new DefaultResourceLoader() {
            Resource getResourceByPath(String path) {
                path.startsWith('messages') ? applicationMessages : pluginMessages
            }
        })
        messageSource.setSearchClasspath(true)
        messageSource.setResourceResolver(new PathMatchingResourcePatternResolver() {
            @Override
            Resource[] getResources(String locationPattern) {
                [pluginMessages] as Resource[]
            }
        })

        when: 'the plugin manager lifecycle discovers plugin bundles with no explicit basename configured'
        messageSource.afterPropertiesSet()

        then: 'the discovered plugin bundle is still picked up'
        messageSource.getMessage('shared.message', null, Locale.default) == 'Plugin Message'
    }

    class TestResource extends ByteArrayResource {
        String filename

        TestResource(String filename, byte[] byteArray) {
            super(byteArray)
            this.filename = filename
        }
    }

}
