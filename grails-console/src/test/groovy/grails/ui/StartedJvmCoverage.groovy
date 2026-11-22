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
package grails.ui

/**
 * What a spec that starts a JVM of its own gives that JVM so that the code it runs is counted as
 * tested. The classes here end the JVM they run in, so their specs start one for each case, and the
 * coverage agent of the test task is in the JVM of the tests only: the build hands the tests the
 * argument that starts the agent in another JVM (the system property {@code grails.test.fork.jacoco},
 * set by the {@code grails-jacoco} convention plugin), with a place for the name of that JVM, which
 * writes a file of its own.
 */
final class StartedJvmCoverage {

    private StartedJvmCoverage() {
    }

    /**
     * @return the JVM arguments that have a started JVM counted: none where the tests are run without coverage
     */
    static List<String> jvmArguments() {
        String agent = System.getProperty('grails.test.fork.jacoco')
        agent ? [agent.replace('@fork@', UUID.randomUUID().toString())] : []
    }
}
