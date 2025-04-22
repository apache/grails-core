/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.test.support

import org.springframework.context.ApplicationContext
import groovy.transform.CompileStatic

@CompileStatic
class GrailsTestInterceptor {

    private test
    private GrailsTestMode mode
    private ApplicationContext appCtx
    private String[] testClassSuffixes

    private GrailsTestTransactionInterceptor transactionInterceptor
    private GrailsTestRequestEnvironmentInterceptor requestEnvironmentInterceptor

    GrailsTestInterceptor(Object test, GrailsTestMode mode, ApplicationContext appCtx, String[] testClassSuffixes) {
        this.test = test
        this.mode = mode
        this.appCtx = appCtx
        this.testClassSuffixes = testClassSuffixes
    }

    void init() {
        autowireIfNecessary()
        initTransactionIfNecessary()
        initRequestEnvironmentIfNecessary()
    }

    void destroy() {
        destroyTransactionIfNecessary()
        requestEnvironmentInterceptor?.destroy()
    }

    void wrap(Closure body) {
        init()
        try {
            body()
        } finally {
            destroy()
        }
    }

    protected autowireIfNecessary() {
        if (mode.autowire) createAutowirer().autowire(test)
    }

    protected initTransactionIfNecessary() {
        def localTransactionInterceptor = createTransactionInterceptor()
        if (mode.wrapInTransaction && localTransactionInterceptor.isTransactional(test)) {
            transactionInterceptor = localTransactionInterceptor
            transactionInterceptor.init()
        }
    }

    protected destroyTransactionIfNecessary() {
        transactionInterceptor?.destroy()
        transactionInterceptor = null
    }

    protected String getControllerName() {
        ControllerNameExtractor.extractControllerNameFromTestClassName(test.class.name, testClassSuffixes)
    }

    protected initRequestEnvironmentIfNecessary() {
        if (mode.wrapInRequestEnvironment) {
            requestEnvironmentInterceptor = createRequestEnvironmentInterceptor()
            def controllerName = getControllerName()
            controllerName ? requestEnvironmentInterceptor.init(controllerName) : requestEnvironmentInterceptor.init()
        }
    }

    protected destroyRequestEnvironmentIfNecessary() {
        requestEnvironmentInterceptor?.destroy()
        requestEnvironmentInterceptor = null
    }

    protected GrailsTestAutowirer createAutowirer() {
        new GrailsTestAutowirer(appCtx)
    }

    protected GrailsTestTransactionInterceptor createTransactionInterceptor() {
        new GrailsTestTransactionInterceptor(appCtx)
    }

    protected GrailsTestRequestEnvironmentInterceptor createRequestEnvironmentInterceptor() {
        new GrailsTestRequestEnvironmentInterceptor(appCtx)
    }
}
