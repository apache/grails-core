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

import grails.testing.web.interceptor.InterceptorUnitTest
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.grails.plugins.web.interceptors.GrailsInterceptorHandlerInterceptorAdapter
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

@CompileStatic
class InterceptorSetupSpecInterceptor implements IMethodInterceptor {

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        InterceptorUnitTest test = (InterceptorUnitTest)invocation.instance
        setup(test)
        invocation.proceed()
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    void setup(InterceptorUnitTest test) {
        test.defineBeans {
            grailsInterceptorHandlerInterceptorAdapter(GrailsInterceptorHandlerInterceptorAdapter)
        }
    }
}
