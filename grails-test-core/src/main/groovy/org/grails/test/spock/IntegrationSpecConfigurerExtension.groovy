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

import grails.util.Holders
import groovy.transform.CompileStatic
import org.grails.test.support.GrailsTestInterceptor
import org.grails.test.support.GrailsTestMode
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo
import org.springframework.context.ApplicationContext

import java.lang.annotation.Annotation

/**
 * Spock extension that can be applied to Integration tests to make them Grails aware
 *
 * @author Graeme Rocher
 * @since 2.3
 *
 */
@CompileStatic
class IntegrationSpecConfigurerExtension implements IAnnotationDrivenExtension<Annotation> {

    void visitSpecAnnotation(Annotation annotation, SpecInfo spec) {
        final context = Holders.getApplicationContext()
        if (context) {
            for (FeatureInfo info in spec.getAllFeatures()) {
                info.addInterceptor(new IntegrationSpecMethodInterceptor(context))
            }
        }

    }


    @CompileStatic
    class IntegrationSpecMethodInterceptor implements IMethodInterceptor {

        ApplicationContext applicationContext
        GrailsTestMode mode


        IntegrationSpecMethodInterceptor(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext
            this.mode = new GrailsTestMode(autowire: true, wrapInTransaction: true, wrapInRequestEnvironment: true)

        }

        @Override
        void intercept(IMethodInvocation invocation) {
            final instance = invocation.instance ?: invocation.sharedInstance
            if (instance) {
                GrailsTestInterceptor interceptor = new GrailsTestInterceptor(instance, mode, applicationContext, ["Spec", "Specification", "Tests", "Test"] as String[])
                interceptor.wrap {
                    invocation.proceed()
                }
            } else {
                invocation.proceed()
            }
        }
    }
}
