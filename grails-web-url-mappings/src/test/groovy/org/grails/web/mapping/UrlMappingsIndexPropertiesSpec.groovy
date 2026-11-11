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
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.util.Collections
import java.util.Enumeration

import grails.web.mapping.AbstractUrlMappingsSpec
import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappings

class UrlMappingsIndexPropertiesSpec extends AbstractUrlMappingsSpec {

    void 'missing build-time URL mapping index is absent'() {
        when:
        UrlMappingsIndexProperties indexProperties = UrlMappingsIndexProperties.load(classLoaderWithNoResources())

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

    void 'load uses only the provided classloader'() {
        given:
        Thread currentThread = Thread.currentThread()
        ClassLoader originalClassLoader = currentThread.contextClassLoader
        currentThread.contextClassLoader = classLoaderWithProperties('source=thread-context')

        when:
        UrlMappingsIndexProperties indexProperties = UrlMappingsIndexProperties.load(classLoaderWithProperties('source=provided'))

        then:
        indexProperties.present
        indexProperties.getProperty('source') == 'provided'

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
        UrlMappingsIndexProperties indexProperties = UrlMappingsIndexProperties.load(classLoaderWithUnreadableResource())

        then:
        !indexProperties.present
        indexProperties.asProperties().isEmpty()
    }

    void 'unreadable resource does not stop a later readable copy on the same classloader'() {
        when:
        UrlMappingsIndexProperties indexProperties = UrlMappingsIndexProperties.load(
                classLoaderWithResources([unreadableResource(), propertiesResource('source=fallback')]))

        then:
        indexProperties.present
        indexProperties.getProperty('source') == 'fallback'
    }

    void 'null classloader yields an absent index'() {
        expect:
        !UrlMappingsIndexProperties.load(null).present
    }

    void 'propertyNames exposes descriptor keys when present and is empty otherwise'() {
        expect:
        UrlMappingsIndexProperties.load(classLoaderWithProperties('source=descriptor')).propertyNames() as Set == ['source'] as Set
        UrlMappingsIndexProperties.load(classLoaderWithNoResources()).propertyNames().empty
    }

    void 'runtime matching stays authoritative when a descriptor is present'() {
        given:
        Thread currentThread = Thread.currentThread()
        ClassLoader originalClassLoader = currentThread.contextClassLoader
        currentThread.contextClassLoader = classLoaderWithProperties('source=descriptor')

        when:
        UrlMappingsIndexProperties indexProperties = UrlMappingsIndexProperties.load(currentThread.contextClassLoader)
        UrlMappings holder = getUrlMappingsHolder {
            "/books"(controller: 'book', action: 'list')
        }
        UrlMappingInfo[] matches = holder.matchAll('/books')

        then:
        indexProperties.present
        matches.length == 1
        matches[0].controllerName == 'book'
        matches[0].actionName == 'list'

        cleanup:
        currentThread.contextClassLoader = originalClassLoader
    }

    private static ClassLoader classLoaderWithProperties(String properties) {
        classLoaderWithResources([propertiesResource(properties)])
    }

    private static ClassLoader classLoaderWithUnreadableResource() {
        classLoaderWithResources([unreadableResource()])
    }

    private static ClassLoader classLoaderWithNoResources() {
        classLoaderWithResources([])
    }

    private static ClassLoader classLoaderWithResources(List<URL> resources) {
        new ClassLoader() {
            @Override
            Enumeration<URL> getResources(String name) {
                name == UrlMappingsIndexProperties.LOCATION ?
                        Collections.enumeration(resources) :
                        Collections.emptyEnumeration()
            }

            @Override
            InputStream getResourceAsStream(String name) {
                null
            }
        }
    }

    private static URL propertiesResource(String properties) {
        byte[] bytes = properties.bytes
        new URL('memory', null, -1, '/' + System.identityHashCode(bytes), new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                new URLConnection(url) {
                    @Override
                    void connect() {
                    }

                    @Override
                    InputStream getInputStream() {
                        new ByteArrayInputStream(bytes)
                    }
                }
            }
        })
    }

    private static URL unreadableResource() {
        new URL('memory', null, -1, '/unreadable', new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                new URLConnection(url) {
                    @Override
                    void connect() {
                    }

                    @Override
                    InputStream getInputStream() {
                        throw new IOException('Resource access denied')
                    }
                }
            }
        })
    }
}
