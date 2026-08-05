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

package app2

import org.grails.exceptions.reporting.StackTraceFilterer

/**
 * Test-only {@link StackTraceFilterer} configured via {@code grails.logging.stackTraceFiltererClass}
 * by {@code GrailsUtilStackFiltererIntegrationSpec} to prove that the class named in config is the one
 * that actually ends up wired into {@code GrailsUtil} by {@code GrailsBootstrapRegistryInitializer}
 * during a real application bootstrap.
 */
class RecordingStackTraceFilterer implements StackTraceFilterer {

    static volatile boolean invoked = false

    @Override
    Throwable filter(Throwable source, boolean recursive) {
        invoked = true
        source
    }

    @Override
    Throwable filter(Throwable source) {
        invoked = true
        source
    }

    @Override
    void addInternalPackage(String name) {
    }

    @Override
    void setCutOffPackage(String cutOffPackage) {
    }

    @Override
    void setShouldFilter(boolean shouldFilter) {
    }
}
