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
package org.grails.gradle.plugin.verification

import groovy.transform.CompileStatic

import org.gradle.api.GradleException
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputFile
import org.gradle.process.CommandLineArgumentProvider

/**
 * Checks the Grails plugins of a project as its application checks them when it starts, so the build fails
 * on what would stop the application: a plugin built for an earlier Grails, a broken descriptor, an unmet
 * {@code dependsOn}, a plugin order that is circular.
 *
 * <p>It runs the discovery of the project's own Grails, in a JVM of the project's toolchain, with the
 * project's runtime class path, once for each of {@link #getEnvironments() the environments}, with the
 * {@code grails.plugin.includes} or {@code grails.plugin.excludes} of the project's {@code application.yml}
 * and {@code application.groovy}. The plugin order of each environment is written to
 * {@link #getReport() the report}.</p>
 *
 * @since 9.0
 */
@CompileStatic
@CacheableTask
abstract class VerifyGrailsPluginsTask extends JavaExec {

    /** The class that checks the plugins, in the project's Grails. */
    public static final String VERIFICATION_CLASS = 'org.apache.grails.core.plugins.PluginVerification'

    /** The Grails environments checked: {@code development}, {@code test} and {@code production} unless set. */
    @Input
    abstract ListProperty<String> getEnvironments()

    /** The plugin order of each environment, one line each. */
    @OutputFile
    abstract RegularFileProperty getReport()

    /** Where the check writes why the plugins would stop the application. */
    @Internal
    abstract RegularFileProperty getFailure()

    VerifyGrailsPluginsTask() {
        mainClass.set(VERIFICATION_CLASS)
        argumentProviders.add(new VerificationArguments(report, failure, environments))
    }

    @Override
    void exec() {
        File failureFile = failure.get().asFile
        failureFile.delete()
        failureFile.parentFile.mkdirs()
        super.exec()
        if (failureFile.exists()) {
            String reason = failureFile.getText('UTF-8')
            failureFile.delete()
            throw new GradleException('The Grails plugins of this project would stop its application from ' +
                    'starting:' + System.lineSeparator() + reason)
        }
    }

    /** The report, the failure file and the environments, as the check takes them; each is an input of the task. */
    private static final class VerificationArguments implements CommandLineArgumentProvider {

        private final Provider<RegularFile> report
        private final Provider<RegularFile> failure
        private final Provider<List<String>> environments

        VerificationArguments(Provider<RegularFile> report, Provider<RegularFile> failure,
                              Provider<List<String>> environments) {
            this.report = report
            this.failure = failure
            this.environments = environments
        }

        @Override
        Iterable<String> asArguments() {
            [report.get().asFile.absolutePath, failure.get().asFile.absolutePath] + environments.get()
        }
    }
}
