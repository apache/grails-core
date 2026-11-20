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
package org.springframework.security.access.method

import org.springframework.security.access.annotation.Secured
import org.springframework.security.access.annotation.SecuredAnnotationSecurityMetadataSource
import spock.lang.Specification

class AbstractFallbackMethodSecurityMetadataSourceSpec extends Specification {

    private final SecuredAnnotationSecurityMetadataSource metadataSource = new SecuredAnnotationSecurityMetadataSource()

    void 'getAttributes prefers method metadata from the concrete target class over class metadata'() {
        given:
        def method = SecuredService.getMethod('userAnnotated')

        expect:
        metadataSource.getAttributes(method, SecuredServiceImpl)*.attribute == ['ROLE_USER']
    }

    void 'getAttributes falls back to class metadata when the concrete target method has no annotation'() {
        given:
        def method = SecuredService.getMethod('notAnnotated')

        expect:
        metadataSource.getAttributes(method, SecuredServiceImpl)*.attribute == ['ROLE_ADMIN']
    }

    private static interface SecuredService {
        void notAnnotated()
        void userAnnotated()
    }

    @Secured('ROLE_ADMIN')
    private static class SecuredServiceImpl implements SecuredService {
        @Override
        void notAnnotated() {}

        @Override
        @Secured('ROLE_USER')
        void userAnnotated() {}
    }
}
