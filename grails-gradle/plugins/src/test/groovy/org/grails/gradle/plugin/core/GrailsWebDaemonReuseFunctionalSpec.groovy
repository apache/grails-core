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
package org.grails.gradle.plugin.core

import org.gradle.testkit.runner.TaskOutcome

/**
 * Functional tests for applying {@code org.apache.grails.gradle.grails-web} repeatedly on one
 * Gradle daemon, the way every build after the first does during normal development.
 *
 * <p>A daemon keeps the plugin classes loaded between builds. Once {@code buildProperties} has
 * executed, Gradle has replaced the meta class of the applied plugin class with one that
 * intercepts calls. Under the Groovy 3 runtime of Gradle 8 that meta class dispatches on the
 * runtime class alone, so a private method of {@link GrailsGradlePlugin} called from dynamically
 * compiled code was not found once the applied plugin was a subclass, and every later build on
 * that daemon failed while the plugin was being applied. The Groovy 4 runtime of Gradle 9 resolves
 * such a call, so this covers the reuse itself rather than that one dispatch.</p>
 *
 * @since 8.0
 * @see org.grails.gradle.plugin.web.GrailsWebGradlePlugin
 */
class GrailsWebDaemonReuseFunctionalSpec extends GradleSpecification {

    void 'the web plugin still applies on a daemon that has already executed buildProperties'() {
        given:
        setupTestResourceProject('web-daemon-reuse')

        when:
        def first = executeTask('buildProperties')

        then:
        first.task(':buildProperties').outcome == TaskOutcome.SUCCESS

        when:
        def second = executeTask('help')

        then:
        second.task(':help').outcome == TaskOutcome.SUCCESS
    }
}
