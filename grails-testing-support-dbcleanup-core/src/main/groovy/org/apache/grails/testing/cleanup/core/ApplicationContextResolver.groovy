/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.grails.testing.cleanup.core

import groovy.transform.CompileStatic

import org.spockframework.runtime.extension.IMethodInvocation

import org.springframework.context.ApplicationContext

/**
 * Strategy interface for resolving an {@link ApplicationContext} from a Spock test invocation.
 *
 * <p>The {@link DatabaseCleanup#resolver()} attribute allows users to specify a custom
 * implementation of this interface for tests that store the application context in a
 * non-standard location. The default implementation, {@link DefaultApplicationContextResolver},
 * covers the common cases (Groovy property, field reflection, and Spring TestContextManager).</p>
 *
 * <p>Implementations must have a no-arg constructor so they can be instantiated by the framework.</p>
 *
 * @see DatabaseCleanup#resolver()
 * @see DefaultApplicationContextResolver
 */
@CompileStatic
interface ApplicationContextResolver {

    /**
     * Resolves the {@link ApplicationContext} from the given Spock method invocation.
     *
     * @param invocation the current Spock method invocation, providing access to the test instance
     * @return the resolved {@link ApplicationContext}, or {@code null} if it cannot be resolved
     */
    ApplicationContext resolve(IMethodInvocation invocation)
}
