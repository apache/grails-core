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
package org.grails.io.support

import spock.lang.Specification

class DefaultResourceLoaderSpec extends Specification {

    void 'getResource resolves a classpath: pseudo-URL to a ClassPathResource'() {
        given:
        DefaultResourceLoader loader = new DefaultResourceLoader()

        when:
        Resource resource = loader.getResource('classpath:org/grails/io/support/Resource.class')

        then:
        resource instanceof ClassPathResource
        ((ClassPathResource) resource).getPath() == 'org/grails/io/support/Resource.class'
    }

    void 'getResource resolves a real URL to a UrlResource'() {
        given:
        DefaultResourceLoader loader = new DefaultResourceLoader()

        when:
        Resource resource = loader.getResource('file:///tmp/foo.txt')

        then:
        resource instanceof UrlResource
    }

    void 'getResource falls back to a classpath-relative resource for a plain path'() {
        given:
        DefaultResourceLoader loader = new DefaultResourceLoader()

        when:
        Resource resource = loader.getResource('org/grails/io/support/Resource.class')

        then:
        resource instanceof ClassPathResource
        resource.exists()
    }

    void 'getClassLoader defaults to the thread context class loader'() {
        expect:
        new DefaultResourceLoader().getClassLoader() != null
    }

    void 'a custom classloader passed to the constructor is used'() {
        given:
        ClassLoader custom = new URLClassLoader(new URL[0], null)

        expect:
        new DefaultResourceLoader(custom).getClassLoader().is(custom)
    }

    void 'setClassLoader overrides the classloader used for classpath resolution'() {
        given:
        DefaultResourceLoader loader = new DefaultResourceLoader()
        ClassLoader custom = new URLClassLoader(new URL[0], null)

        when:
        loader.setClassLoader(custom)

        then:
        loader.getClassLoader().is(custom)
    }

}
