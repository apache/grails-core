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
package org.grails.forge.features.geb

import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.utils.CommandSpec

class GebWithLocalBrowsersSpec extends CommandSpec {

    void 'test web app with geb-with-local-browsers builds'() {
        given:
        generateProject(OperatingSystem.LINUX, ['geb-with-local-browsers'], ApplicationType.WEB)

        when:
        // integrationTest needs a locally installed browser, so only compile the functional spec
        final String output = executeGradle('build', 'compileIntegrationTestGroovy', '-x', 'integrationTest').output

        then:
        output.contains('BUILD SUCCESSFUL')
    }

    @Override
    String getTempDirectoryPrefix() {
        'testgeblocalbrowsers'
    }
}
