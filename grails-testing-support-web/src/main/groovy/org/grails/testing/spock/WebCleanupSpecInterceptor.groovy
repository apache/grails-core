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

import grails.testing.web.GrailsWebUnitTest
import org.grails.testing.runtime.support.LazyTagLibraryLookup
import org.grails.web.converters.configuration.ConvertersConfigurationHolder

@CompileStatic
class WebCleanupSpecInterceptor implements IMethodInterceptor {

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        try {
            invocation.proceed()
        }
        finally {
            ConvertersConfigurationHolder.clear()

            GrailsWebUnitTest test = (GrailsWebUnitTest) invocation.instance
            if (test.purgeTagLibMetaClass) {
                try {
                    def lookup = test.grailsApplication.mainContext.getBean(LazyTagLibraryLookup)
                    if (lookup) {
                        lookup.cleanTagLibsMetaClass()
                    }
                }
                catch (ignored) {
                    // may not exist
                }
            }
        }
    }
}
