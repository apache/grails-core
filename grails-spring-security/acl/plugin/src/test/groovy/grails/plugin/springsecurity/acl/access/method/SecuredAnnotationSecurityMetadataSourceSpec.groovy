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
package grails.plugin.springsecurity.acl.access.method

import grails.plugin.springsecurity.annotation.Secured
import spock.lang.Specification

class SecuredAnnotationSecurityMetadataSourceSpec extends Specification {

    private SecuredAnnotationSecurityMetadataSource metadataSource = new SecuredAnnotationSecurityMetadataSource(
        serviceClassNames: [AnnotatedService.name]
    )

    void 'getAttributes resolves method annotations for proxied or subclassed service targets'() {
        given:
        def method = ProxiedAnnotatedService.getMethod('userAnnotated')

        expect:
        metadataSource.getAttributes(method, ProxiedAnnotatedService)*.attribute == ['ROLE_USER']
    }

    void 'getAttributes falls back to inherited class annotations for proxied or subclassed service targets'() {
        given:
        def method = ProxiedAnnotatedService.getMethod('notAnnotated')

        expect:
        metadataSource.getAttributes(method, ProxiedAnnotatedService)*.attribute == ['ROLE_ADMIN']
    }

    @Secured('ROLE_ADMIN')
    private static class AnnotatedService {

        void notAnnotated() {}

        @Secured('ROLE_USER')
        void userAnnotated() {}
    }

    private static class ProxiedAnnotatedService extends AnnotatedService {
    }
}

