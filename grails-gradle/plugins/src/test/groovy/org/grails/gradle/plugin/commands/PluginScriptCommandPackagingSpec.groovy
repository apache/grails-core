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
package org.grails.gradle.plugin.commands

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

import org.grails.gradle.plugin.core.GradleSpecification

/**
 * Verifies that {@code src/main/scripts} command resources and {@code src/main/templates} are
 * packaged according to whether the plugin publishes a companion {@code -cli} artifact (issue
 * #16035).
 *
 * <p>With {@code grails-plugin-cli}, Groovy/YAML command scripts must leave the runtime plugin jar
 * and land only in the companion. Without a companion, the historical runtime-jar packaging is
 * preserved so unmigrated Grails 7 plugins keep working under {@code legacyCommandSupport}.
 * {@code copyCommands} and {@code copyTemplates} stay {@code Copy} tasks, but the owning
 * {@code process*Resources} task composes their {@code CopySpec} instead of copying a staged
 * directory. One task writes each directory, so a deleted source stops being packaged without a
 * forced clean task, and the GSP exclusion applied to plugin views does not reach templates.</p>
 */
class PluginScriptCommandPackagingSpec extends GradleSpecification {

    def "companion plugins package src/main/scripts only in the -cli jar"() {
        given:
        setupTestResourceProject('plugin-script-commands')

        when:
        BuildResult result = executeTask(':plugin-with-cli:inspectCommandPackaging')

        then:
        result.output.contains('RUNTIME_HAS_SCRIPT=false')
        result.output.contains('RUNTIME_HAS_YAML=false')
        result.output.contains('RUNTIME_HAS_HAND_AUTHORED=true')
        result.output.contains('CLI_HAS_SCRIPT=true')
        result.output.contains('CLI_HAS_YAML=true')

        and: 'the runtime resources output holds only the hand-authored resource, nothing staged'
        result.output.contains('RUNTIME_RESOURCES_COMMANDS=hand-authored.yml')

        and: 'templates remain a runtime concern'
        result.output.contains('RUNTIME_HAS_TEMPLATE=true')
    }

    def "plugins without a companion keep src/main/scripts in the runtime jar"() {
        given:
        setupTestResourceProject('plugin-script-commands')

        when:
        BuildResult result = executeTask(':plugin-without-cli:inspectCommandPackaging')

        then:
        result.output.contains('RUNTIME_HAS_SCRIPT=true')
        result.output.contains('RUNTIME_HAS_TEMPLATE=true')

        and: 'the script is packaged once, not once per contributing task'
        result.output.contains('RUNTIME_SCRIPT_COUNT=1')
    }

    def "GSP templates are packaged even though plugin views are excluded from resources"() {
        given: 'a .gsp under src/main/templates, a view contributed to copyTemplates, and a plugin view'
        setupTestResourceProject('plugin-script-commands')

        when:
        BuildResult result = executeTask(':plugin-with-cli:inspectCommandPackaging')

        then: 'templates keep their GSPs - they are staged outside processResources'
        result.output.contains('RUNTIME_HAS_GSP_TEMPLATE=true')
        result.output.contains('RUNTIME_HAS_VIEW_TEMPLATE=true')

        and: 'the plugin views themselves are still kept out of the runtime resources'
        result.output.contains('RUNTIME_HAS_RAW_VIEW=false')

        and: 'a plugin without a companion packages its GSP templates too'
        executeTask(':plugin-without-cli:inspectCommandPackaging')
                .output.contains('RUNTIME_HAS_GSP_TEMPLATE=true')
    }

    def "neither copy task writes into a process*Resources output directory"() {
        given:
        setupTestResourceProject('plugin-script-commands')

        when:
        BuildResult result = executeTask(':plugin-with-cli:inspectCommandPackaging')

        then:
        result.output.contains('STAGING_DIRS_DISTINCT=true')
        result.output.contains('COMMANDS_OUTSIDE_RESOURCES=true')
        result.output.contains('TEMPLATES_OUTSIDE_RESOURCES=true')
    }

    def "deleting a script or template drops it from the next build without a clean"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-script-commands')
        File projectDir = runner.projectDir
        executeTask(':plugin-with-cli:inspectCommandPackaging')

        when: 'the sources are removed and the project is rebuilt in place'
        assert new File(projectDir, 'plugin-with-cli/src/main/scripts/example-script.groovy').delete()
        assert new File(projectDir, 'plugin-with-cli/src/main/templates/example.txt').delete()
        BuildResult result = executeTask(':plugin-with-cli:inspectCommandPackaging')

        then: 'the stale entries are gone from both jars'
        result.output.contains('CLI_HAS_SCRIPT=false')
        result.output.contains('RUNTIME_HAS_TEMPLATE=false')

        and: 'the untouched resources still ship'
        result.output.contains('CLI_HAS_YAML=true')
        result.output.contains('RUNTIME_HAS_GSP_TEMPLATE=true')
        result.output.contains('RUNTIME_HAS_HAND_AUTHORED=true')
    }
}
