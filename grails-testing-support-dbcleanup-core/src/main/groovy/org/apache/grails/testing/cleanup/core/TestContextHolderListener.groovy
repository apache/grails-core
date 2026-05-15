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

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.test.context.TestContext
import org.springframework.test.context.TestExecutionListener

/**
 * A Spring {@link TestExecutionListener} that captures the current {@link TestContext}
 * in a {@link ThreadLocal} so that it can be accessed by the database cleanup framework
 * during test execution.
 *
 * <p>This listener is auto-registered via {@code META-INF/spring.factories}.
 * It sets the {@link TestContext} before each test method. The {@link ThreadLocal} is
 * cleared by the {@link DatabaseCleanupInterceptor} after database cleanup completes,
 * rather than in {@code afterTestMethod}, to ensure the context remains available
 * throughout the entire cleanup phase.</p>
 *
 * @see DefaultApplicationContextResolver
 * @see DatabaseCleanupInterceptor
 */
@CompileStatic
@Order(Ordered.HIGHEST_PRECEDENCE)
final class TestContextHolderListener implements TestExecutionListener {

    /**
     * Holds the current {@link TestContext} for the executing test thread.
     * Set before each test method by this listener and cleared by
     * {@link DatabaseCleanupInterceptor} after cleanup completes.
     */
    public static final ThreadLocal<TestContext> CURRENT = new ThreadLocal<>()

    @Override
    void beforeTestMethod(TestContext testContext) {
        // spock doesn't support ordering interceptors - https://github.com/spockframework/spock/issues/817
        // since we only need the context at the cleanup, use beforeTestExecution instead of beforeTestMethod
        CURRENT.set(testContext)
    }
}
