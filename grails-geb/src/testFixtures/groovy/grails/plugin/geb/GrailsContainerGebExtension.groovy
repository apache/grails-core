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

import java.time.LocalDateTime

import groovy.transform.CompileStatic
import groovy.transform.TailRecursive
import groovy.util.logging.Slf4j

import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.MethodInfo
import org.spockframework.runtime.model.SpecInfo
import org.spockframework.runtime.model.parallel.ExclusiveResource
import org.spockframework.runtime.model.parallel.ResourceAccessMode

import grails.plugin.geb.support.LocalhostDownloadSupport
import grails.testing.mixin.integration.Integration

/**
 * A Spock Extension that manages the Testcontainers lifecycle for a {@link grails.plugin.geb.ContainerGebSpec}
 *
 * <p> ContainerGebSpec cannot be a {@link geb.test.ManagedGebTest ManagedGebTest} because it would cause the test manager
 * to be initialized out of sequence of the container management.  Instead, we initialize the same interceptors
 * as the {@link geb.spock.GebExtension GebExtension} does.
 *
 * @author James Daugherty
 * @since 4.1
 */
@Slf4j
@CompileStatic
class GrailsContainerGebExtension implements IGlobalExtension {

    ExclusiveResource exclusiveResource
    WebDriverContainerHolder holder

    @Override
    void start() {
        exclusiveResource = new ExclusiveResource(
                ContainerGebSpec.name,
                ResourceAccessMode.READ_WRITE
        )
        holder = new WebDriverContainerHolder(
                new GrailsGebSettings(LocalDateTime.now())
        )
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
        if (isContainerGebSpec(spec) && validateContainerGebSpec(spec)) {
            // Do not allow parallel execution since there's only 1 set of containers in testcontainers
            spec.addExclusiveResource(exclusiveResource)

            // Always initialize the container requirements first so the GebTestManager can properly configure the browser
            spec.addSharedInitializerInterceptor { invocation ->
                holder.reinitialize(invocation)

                ContainerGebSpec gebSpec = invocation.sharedInstance as ContainerGebSpec
                gebSpec.container = holder.currentContainer
                gebSpec.testManager = holder.testManager
                gebSpec.downloadSupport = new LocalhostDownloadSupport(
                        holder.currentBrowser,
                        holder.hostNameFromHost
                )

                // code below here is from the geb.spock.GebExtension since there can only be 1 shared initializer per extension
                holder.testManager.beforeTestClass(invocation.spec.reflection)
                invocation.proceed()
            }

            spec.addSetupInterceptor { invocation ->
                // Grails will be initialized by this point, so setup the browser url correctly
                holder.setupBrowserUrl(invocation)

                invocation.proceed()
            }

            spec.addInterceptor { invocation ->
                try {
                    invocation.proceed()
                } finally {
                    holder.testManager.afterTestClass()
                }
            }

            spec.allFeatures*.addIterationInterceptor { invocation ->
                holder.testManager.beforeTest(invocation.instance.getClass(), invocation.iteration.displayName)
                try {
                    invocation.proceed()
                } finally {
                    holder.testManager.afterTest()
                }
            }

            addGebExtensionOnFailureReporter(spec)

            GebRecordingTestListener recordingListener = new GebRecordingTestListener(
                holder
            )
            spec.addListener(recordingListener)
        }
    }

    @TailRecursive
    private boolean isContainerGebSpec(SpecInfo spec) {
        if (spec != null) {
            if (spec.filename.startsWith("${ContainerGebSpec.simpleName}." as String)) {
                return true
            }
            return isContainerGebSpec(spec.superSpec)
        }
        return false
    }

    private static boolean validateContainerGebSpec(SpecInfo specInfo) {
        if (!specInfo.annotations.find { it.annotationType() == Integration }) {
            throw new IllegalArgumentException('ContainerGebSpec classes must be annotated with @Integration')
        }

        return true
    }

    private static void addGebExtensionOnFailureReporter(SpecInfo spec) {
        List<MethodInfo> methods = spec.allFeatures*.featureMethod + spec.allFixtureMethods.toList()
        methods.each { MethodInfo method ->
            method.addInterceptor(new GebOnFailureReporter())
        }
    }
}

