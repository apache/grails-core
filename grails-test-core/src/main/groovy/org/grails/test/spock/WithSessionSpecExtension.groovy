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
package org.grails.test.spock

import grails.testing.mixin.integration.WithSession
import grails.util.Holders
import groovy.transform.CompileStatic
import org.grails.test.support.GrailsTestSessionInterceptor
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo
import org.springframework.context.ApplicationContext

/**
 * Spock extension for @WithSession annotation.
 * Binds Hibernate sessions without transactions for testing.
 */
@CompileStatic
class WithSessionSpecExtension implements IAnnotationDrivenExtension<WithSession> {

    @Override
    void visitSpecAnnotation(WithSession annotation, SpecInfo spec) {
        final context = Holders.getApplicationContext()
        if (context) {
            for (FeatureInfo info : spec.getAllFeatures()) {
                info.addInterceptor(new WithSessionMethodInterceptor(context, annotation))
            }
        }
    }

    @Override
    void visitFeatureAnnotation(WithSession annotation, FeatureInfo feature) {
        final context = Holders.getApplicationContext()
        if (context) {
            feature.addInterceptor(new WithSessionMethodInterceptor(context, annotation))
        }
    }

    @CompileStatic
    static class WithSessionMethodInterceptor implements IMethodInterceptor {
        private final ApplicationContext applicationContext
        private final WithSession annotation
        
        WithSessionMethodInterceptor(ApplicationContext applicationContext, WithSession annotation) {
            this.applicationContext = applicationContext
            this.annotation = annotation
        }
        
        @Override
        void intercept(IMethodInvocation invocation) {
            final instance = invocation.instance ?: invocation.sharedInstance
            if (instance && applicationContext) {
                GrailsTestSessionInterceptor sessionInterceptor = new GrailsTestSessionInterceptor(applicationContext)
                
                // Configure datasources from annotation
                if (annotation.datasources().length > 0) {
                    // Create a mock test object with datasources property
                    def testWrapper = new Expando()
                    testWrapper.withSession = annotation.datasources() as List
                    sessionInterceptor.shouldBindSessions(testWrapper)
                } else {
                    // Bind all datasources
                    sessionInterceptor.shouldBindSessions([withSession: true])
                }
                
                try {
                    sessionInterceptor.init()
                    invocation.proceed()
                } finally {
                    sessionInterceptor.destroy()
                }
            } else {
                invocation.proceed()
            }
        }
    }
}