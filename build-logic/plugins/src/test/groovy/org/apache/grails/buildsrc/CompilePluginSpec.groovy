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
package org.apache.grails.buildsrc

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class CompilePluginSpec extends Specification {

    void 'automatic module name is derived from group and project name'() {
        given:
        def project = ProjectBuilder.builder().withName('grails-web-common').build()
        project.group = 'org.apache.grails.web'

        expect:
        CompilePlugin.automaticModuleName(project) == 'org.apache.grails.web.grails.web.common'
    }

    void 'automatic module name can be overridden per project'() {
        given:
        def project = ProjectBuilder.builder().withName('grails-web-common').build()
        project.ext.automaticModuleName = 'org.apache.grails.web.common'

        expect:
        CompilePlugin.automaticModuleName(project) == 'org.apache.grails.web.common'
    }
}
