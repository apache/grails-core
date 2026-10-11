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
package org.grails.core.io

import spock.lang.Specification

import org.springframework.core.io.ClassPathResource

class StaticResourceLoaderSpec extends Specification {

    void 'resolves resources relative to the configured base resource'() {
        given:
        def loader = new StaticResourceLoader()
        loader.setBaseResource(new ClassPathResource('org/grails/core/io/'))

        when:
        def resource = loader.getResource('DoesNotExist.txt')

        then:
        resource != null
        !resource.exists()
    }

    void 'getResource fails fast when no base resource has been set'() {
        given:
        def loader = new StaticResourceLoader()

        when:
        loader.getResource('anything.txt')

        then:
        thrown(IllegalStateException)
    }

    void 'getClassLoader returns the current thread context class loader'() {
        given:
        def loader = new StaticResourceLoader()

        expect:
        loader.getClassLoader() == Thread.currentThread().contextClassLoader
    }

}
