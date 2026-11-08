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
package org.grails.cli

import spock.lang.Specification
import spock.lang.TempDir

/**
 * Covers the project detection that lets {@code --help} include application (project) commands when
 * run inside a Grails project.
 */
class GrailsCliProjectDetectionSpec extends Specification {

    @TempDir
    File dir

    void 'a directory containing #marker is detected as a Grails project'() {
        given:
        create(marker)

        expect:
        GrailsCli.isGrailsProject(dir)

        where:
        marker << ['grails-app', 'Application.groovy', 'profile.yml']
    }

    void 'a directory with none of the project markers is not a Grails project'() {
        expect:
        !GrailsCli.isGrailsProject(dir)
    }

    private void create(String marker) {
        if (marker == 'grails-app') {
            assert new File(dir, marker).mkdirs()
        }
        else {
            new File(dir, marker).text = ''
        }
    }
}
