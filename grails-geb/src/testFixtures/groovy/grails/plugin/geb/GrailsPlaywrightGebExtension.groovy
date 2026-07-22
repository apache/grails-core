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
import groovy.transform.TailRecursive

import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.SpecInfo
import org.spockframework.runtime.model.parallel.ExclusiveResource
import org.spockframework.runtime.model.parallel.ResourceAccessMode

import grails.testing.mixin.integration.Integration

@CompileStatic
class GrailsPlaywrightGebExtension implements IGlobalExtension {

    private final PlaywrightBrowserHolder holder = new PlaywrightBrowserHolder()
    private final ExclusiveResource exclusiveResource = new ExclusiveResource(
            PlaywrightGebSpec.name,
            ResourceAccessMode.READ_WRITE
    )

    @Override
    void start() {
        addShutdownHook {
            holder.stop()
        }
    }

    @Override
    void stop() {
        holder.stop()
    }

    @Override
    void visitSpec(SpecInfo spec) {
        if (isPlaywrightGebSpec(spec)) {
            validatePlaywrightGebSpec(spec)
            spec.addExclusiveResource(exclusiveResource)
            spec.addSharedInitializerInterceptor { invocation ->
                holder.initialize()
                PlaywrightGebSpec gebSpec = invocation.sharedInstance as PlaywrightGebSpec
                gebSpec.testManager = holder.testManager
                holder.testManager.beforeTestClass(invocation.spec.reflection)
                invocation.proceed()
            }
            spec.addSetupInterceptor { invocation ->
                holder.setupBrowserUrl(invocation)
                invocation.proceed()
            }
            spec.addInterceptor { invocation ->
                try {
                    invocation.proceed()
                } finally {
                    holder.testManager.afterTestClass()
                    holder.stop()
                }
            }
            spec.allFeatures*.addIterationInterceptor { invocation ->
                holder.testManager.beforeTest(invocation.instance.class, invocation.iteration.displayName)
                try {
                    invocation.proceed()
                } finally {
                    holder.testManager.afterTest()
                }
            }
        }
    }

    @TailRecursive
    private static boolean isPlaywrightGebSpec(SpecInfo spec) {
        if (!spec) {
            return false
        }
        if (spec.filename.startsWith("${PlaywrightGebSpec.simpleName}.")) {
            return true
        }
        isPlaywrightGebSpec(spec.superSpec)
    }

    private static void validatePlaywrightGebSpec(SpecInfo spec) {
        if (!spec.annotations.any { it.annotationType() == Integration }) {
            throw new IllegalArgumentException('PlaywrightGebSpec classes must be annotated with @Integration.')
        }
    }
}
