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
package org.grails.web.mapping

import java.io.ByteArrayInputStream
import java.io.InputStream

import grails.web.mapping.UrlMapping
import spock.lang.Specification

class UrlMappingsIndexPropertiesSpec extends Specification {

    void 'missing build-time URL mapping index keeps runtime fallback active'() {
        when:
        DefaultUrlMappingsHolder holder = new DefaultUrlMappingsHolder([] as List<UrlMapping>)

        then:
        !holder.precomputedIndexProperties.present
        holder.urlMappings.length == 0
        holder.matchAll('/books').length == 0
    }

    void 'thread context classloader takes precedence for build-time URL mapping index'() {
        given:
        Thread currentThread = Thread.currentThread()
        ClassLoader originalClassLoader = currentThread.contextClassLoader
        ClassLoader threadContextClassLoader = classLoaderWithProperties('source=thread-context')
        ClassLoader fallbackClassLoader = classLoaderWithProperties('source=fallback')
        currentThread.contextClassLoader = threadContextClassLoader

        when:
        UrlMappingsIndexProperties indexProperties = UrlMappingsIndexProperties.load(fallbackClassLoader)

        then:
        indexProperties.present
        indexProperties.getProperty('source') == 'thread-context'

        cleanup:
        currentThread.contextClassLoader = originalClassLoader
    }

    void 'malformed build-time URL mapping index keeps runtime fallback active'() {
        when:
        UrlMappingsIndexProperties indexProperties = UrlMappingsIndexProperties.load(classLoaderWithProperties('source=\\uZZZZ'))

        then:
        !indexProperties.present
        indexProperties.asProperties().isEmpty()
    }

    void 'unreadable build-time URL mapping index keeps runtime fallback active'() {
        when:
        UrlMappingsIndexProperties indexProperties = UrlMappingsIndexProperties.load(new ClassLoader() {
            @Override
            InputStream getResourceAsStream(String name) {
                throw new SecurityException('Resource access denied')
            }
        })

        then:
        !indexProperties.present
        indexProperties.asProperties().isEmpty()
    }

    void 'valid build-time URL mapping index is present'() {
        when:
        UrlMappingsIndexProperties indexProperties = UrlMappingsIndexProperties.load(classLoaderWithProperties('source=descriptor'))

        then:
        indexProperties.present
        indexProperties.getProperty('source') == 'descriptor'
    }

    private static ClassLoader classLoaderWithProperties(String properties) {
        new ClassLoader() {
            @Override
            InputStream getResourceAsStream(String name) {
                name == UrlMappingsIndexProperties.LOCATION ? new ByteArrayInputStream(properties.bytes) : null
            }
        }
    }
}
