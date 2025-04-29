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
package grails.plugin.geb

import groovy.transform.CompileStatic
import org.opentest4j.IncompleteExecutionException
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

/**
 * This class is a direct clone of {@link geb.spock.OnFailureReporter OnFailureReporter}, except it works for the
 * {@link grails.plugin.geb.ContainerGebSpec ContainerGebSpec}.
 */
@CompileStatic
class GebOnFailureReporter implements IMethodInterceptor {
    void intercept(IMethodInvocation invocation) throws Throwable {
        try {
            invocation.proceed()
        } catch (IncompleteExecutionException notACauseForReporting) {
            throw notACauseForReporting
        } catch (Throwable throwable) {
            ContainerGebSpec spec = invocation.instance as ContainerGebSpec
            if (spec.testManager.reportingEnabled) {
                try {
                    spec.testManager.reportFailure()
                } catch (ignored) {
                    //ignore
                }
            }
            throw throwable
        }
    }
}
