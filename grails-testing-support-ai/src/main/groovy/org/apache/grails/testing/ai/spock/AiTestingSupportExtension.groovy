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
package org.apache.grails.testing.ai.spock

import groovy.transform.CompileStatic
import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.SpecInfo

import grails.testing.ai.AiUnitTest

/**
 * Gives every specification that implements {@link AiUnitTest} its stub beans before the specification
 * runs, and a clean script before every feature method.
 *
 * @since 8.1
 */
@CompileStatic
class AiTestingSupportExtension implements IGlobalExtension {

    private final AiSetupSpecInterceptor setupSpecInterceptor = new AiSetupSpecInterceptor()
    private final AiSetupInterceptor setupInterceptor = new AiSetupInterceptor()

    @Override
    void visitSpec(SpecInfo spec) {
        if (AiUnitTest.isAssignableFrom(spec.reflection)) {
            spec.addSetupSpecInterceptor(setupSpecInterceptor)
            spec.addSetupInterceptor(setupInterceptor)
        }
    }

}
