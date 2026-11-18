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
package org.grails.forge.features.asciidoctor

import org.gradle.testkit.runner.BuildResult
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.utils.CommandSpec

class AsciidoctorBuildSpec extends CommandSpec {

    void 'an app with asciidoctor builds its guide, without the configuration cache the plugin does not support'() {
        given:
        generateProject(OperatingSystem.LINUX, ['asciidoctor'], ApplicationType.WEB)

        when:
        BuildResult result = executeGradle('asciidoctor')

        then:
        result.output.contains('BUILD SUCCESSFUL')
        // the plugin's task is marked as not compatible, so its problems discard the entry instead of failing the build
        result.output.contains('Configuration cache entry discarded')
        new File(dir, 'build/docs/index.html').file || new File(dir, 'build/docs/asciidoc/index.html').file
    }

    @Override
    String getTempDirectoryPrefix() {
        'testasciidoctor'
    }
}
