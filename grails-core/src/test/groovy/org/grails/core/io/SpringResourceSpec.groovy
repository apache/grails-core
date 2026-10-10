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

import org.springframework.core.io.ByteArrayResource

class SpringResourceSpec extends Specification {

    void 'delegates read-only operations to the wrapped Spring resource'() {
        given:
        def springResource = new ByteArrayResource('content'.bytes, 'a description')
        def resource = new SpringResource(springResource)

        expect:
        resource.exists() == springResource.exists()
        resource.isReadable() == springResource.isReadable()
        resource.contentLength() == springResource.contentLength()
        resource.getDescription() == springResource.getDescription()
        resource.getFilename() == springResource.getFilename()
        resource.getInputStream().text == 'content'
    }

    void 'createRelative wraps the delegate relative resource'() {
        given:
        def springResource = Mock(org.springframework.core.io.Resource)
        def relative = Mock(org.springframework.core.io.Resource)
        springResource.createRelative('child.txt') >> relative
        def resource = new SpringResource(springResource)

        when:
        def related = resource.createRelative('child.txt')

        then:
        related instanceof SpringResource
    }

    void 'createRelative returns null when the delegate raises an IOException'() {
        given:
        def springResource = Mock(org.springframework.core.io.Resource)
        springResource.createRelative('child.txt') >> { throw new IOException('boom') }
        def resource = new SpringResource(springResource)

        expect:
        resource.createRelative('child.txt') == null
    }

}
