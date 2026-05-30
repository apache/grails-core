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

import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

import grails.testing.web.UrlMappingsUnitTest

/**
 * Wires up controllers and URL mappings for {@link UrlMappingsUnitTest}-based specs.
 *
 * Acts as both a {@code setupSpec} interceptor (mocking declared controllers once per spec
 * class) and a {@code setup} interceptor (re-registering this spec's URL mappings before
 * every feature method). The per-feature pass guarantees that a spec running after another
 * spec in the same JVM sees only its own URL mappings, regardless of what the previous spec
 * did to the artefact registry or the {@code grailsUrlMappingsHolder} bean.
 */
@CompileStatic
class UrlMappingSetupSpecInterceptor implements IMethodInterceptor {

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        UrlMappingsUnitTest spec = (UrlMappingsUnitTest) invocation.instance
        if (invocation.method.kind.isSpecScopedFixtureMethod()) {
            spec.configuredMockedControllers()
        } else {
            spec.resetUrlMappingsForFeature()
        }
        invocation.proceed()
    }
}
