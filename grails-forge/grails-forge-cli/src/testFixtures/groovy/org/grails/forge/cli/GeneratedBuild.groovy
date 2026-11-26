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
package org.grails.forge.cli

import groovy.transform.CompileStatic

/**
 * What the specs that build a generated application pass to its Gradle build before their own
 * arguments.
 *
 * <p>When the Forge build runs with {@code -PgrailsIndy=false}, the framework it publishes for the
 * generated applications is compiled with classic call sites, which Groovy 6 loads only with
 * {@code groovy-callsite}. The Forge build then names an init script in
 * {@link #INIT_SCRIPT_PROPERTY} (see {@code gradle/test-config.gradle}) that makes each generated
 * application declare the module, as any application that depends on a library compiled that way
 * must. Otherwise there is nothing to add.</p>
 *
 * <p>A spec that runs such a build in its own JVM, with TestKit or the Tooling API, runs it with
 * {@link #asFromACommandLine}: both hand the system properties of the JVM that asks to the build, and
 * the JVM of a spec that started Forge has the Grails environment of Forge, production, which the
 * Grails Gradle plugin would give the tests and the commands of the application as the environment
 * that was asked for with {@code -Dgrails.env}. A build somebody runs from a command line has none.</p>
 */
@CompileStatic
final class GeneratedBuild {

    /** The system property naming the init script every generated build is run with, if any. */
    static final String INIT_SCRIPT_PROPERTY = 'forge.generatedBuild.initScript'

    /** The system property the Grails environment is named by, which Forge sets for itself in the JVM it runs in. */
    static final String ENVIRONMENT_PROPERTY = 'grails.env'

    private GeneratedBuild() {
    }

    /**
     * Runs a build of a generated application without the Grails environment of this JVM, as a build
     * that is run from a command line has none, and gives the JVM its environment back afterwards.
     * An environment the build is to have is named in its arguments, as {@code -Dgrails.env=NAME}.
     *
     * @param build what runs the build, with TestKit or the Tooling API
     * @return what it returns
     */
    static <T> T asFromACommandLine(Closure<T> build) {
        synchronized (GeneratedBuild) {
            String ofThisJvm = System.clearProperty(ENVIRONMENT_PROPERTY)
            try {
                return build.call()
            } finally {
                if (ofThisJvm != null) {
                    System.setProperty(ENVIRONMENT_PROPERTY, ofThisJvm)
                }
            }
        }
    }

    static List<String> arguments() {
        String initScript = System.getProperty(INIT_SCRIPT_PROPERTY)
        return initScript ? ['--init-script', initScript] : []
    }

}
