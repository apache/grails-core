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

package org.grails.testing.spock

import groovy.transform.CompileStatic

import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.spockframework.runtime.model.SpecInfo

import grails.testing.web.GrailsWebUnitTest
import grails.testing.web.UrlMappingsUnitTest
import grails.testing.web.interceptor.InterceptorUnitTest

@CompileStatic
class WebTestingSupportExtension extends AbstractGlobalExtension {

    WebSetupSpecInterceptor webSetupSpecInterceptor = new WebSetupSpecInterceptor()
    WebSetupInterceptor webSetupInterceptor = new WebSetupInterceptor()
    WebCleanupInterceptor webCleanupInterceptor = new WebCleanupInterceptor()
    WebCleanupSpecInterceptor webCleanupSpecInterceptor = new WebCleanupSpecInterceptor()
    UrlMappingSetupSpecInterceptor urlMappingSetupSpecInterceptor = new UrlMappingSetupSpecInterceptor()
    InterceptorSetupSpecInterceptor interceptorSetupSpecInterceptor = new InterceptorSetupSpecInterceptor()

    void visitSpec(SpecInfo spec) {
        if (GrailsWebUnitTest.isAssignableFrom(spec.reflection)) {
            spec.addSetupSpecInterceptor(webSetupSpecInterceptor)
            spec.addSetupInterceptor(webSetupInterceptor)
            spec.addCleanupInterceptor(webCleanupInterceptor)
            spec.addCleanupSpecInterceptor(webCleanupSpecInterceptor)
        }

        if (UrlMappingsUnitTest.isAssignableFrom(spec.reflection)) {
            spec.addSetupSpecInterceptor(urlMappingSetupSpecInterceptor)
        }

        if (InterceptorUnitTest.isAssignableFrom(spec.reflection)) {
            spec.addSetupSpecInterceptor(interceptorSetupSpecInterceptor)
        }
    }
}
