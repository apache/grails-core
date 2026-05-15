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
import groovy.util.logging.Slf4j

import org.spockframework.runtime.extension.IMethodInvocation

import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestContext

/**
 * Default implementation of {@link ApplicationContextResolver} that resolves the
 * {@link ApplicationContext} from the Spring {@link TestContext} captured by
 * {@link TestContextHolderListener}.
 *
 * <p>The {@code @Integration} AST transformation adds {@code @SpringBootTest} and
 * {@code @ContextConfiguration} to the test class, so Spring's test infrastructure
 * manages the {@link ApplicationContext} lifecycle. The {@link TestContextHolderListener}
 * is auto-registered as a {@link org.springframework.test.context.TestExecutionListener}
 * and captures the current {@link TestContext} on a {@link ThreadLocal} before each test
 * method. This resolver simply reads from that {@link ThreadLocal}.</p>
 *
 * @see ApplicationContextResolver
 * @see TestContextHolderListener
 * @see DatabaseCleanup#resolver()
 */
@Slf4j
@CompileStatic
class DefaultApplicationContextResolver implements ApplicationContextResolver {

    @Override
    ApplicationContext resolve(IMethodInvocation invocation) {
        def testContext = TestContextHolderListener.CURRENT.get()
        if (testContext) {
            def ctx = testContext.applicationContext
            if (ctx) {
                log.debug('Resolved ApplicationContext via TestContextHolderListener')
                return ctx
            }
        }

        throw new IllegalStateException(
                'Could not resolve ApplicationContext. ' +
                'Ensure the spec is annotated with @Integration ' +
                'and that TestContextHolderListener is registered ' +
                'as a TestExecutionListener.'
        )
    }
}
