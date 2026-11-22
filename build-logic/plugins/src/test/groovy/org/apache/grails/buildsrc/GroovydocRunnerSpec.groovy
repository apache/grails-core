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

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

class GroovydocRunnerSpec extends Specification {

    @TempDir
    File tempDir

    void 'a reproducible build refuses a JDK without its sources before groovydoc starts'() {
        given: 'a JDK home with no lib/src.zip'
        Project project = ProjectBuilder.builder().withProjectDir(new File(tempDir, 'project')).build()
        GroovydocRunner runner = project.objects.newInstance(GroovydocRunner)
        File javaHome = new File(tempDir, 'jdk')
        new File(javaHome, 'lib').mkdirs()
        File workingDir = new File(tempDir, 'work')

        when:
        runner.run(project.files(), '1g', workingDir, [:], [], false, javaHome, true)

        then: 'the failure names the JDK and why its sources matter'
        GradleException e = thrown()
        e.message.startsWith("The JDK at ${javaHome} has no sources (lib/src.zip).")
        e.message.contains('this reproducible build (SOURCE_DATE_EPOCH is set) would write different documentation')

        and: 'groovydoc was never started'
        !workingDir.exists()
    }

    void 'the JDK sources are found where groovydoc looks for them: #layout'() {
        given:
        File javaHome = new File(tempDir, home)
        javaHome.mkdirs()
        if (sources?.endsWith('/')) {
            new File(tempDir, sources).mkdirs()
        }
        else if (sources) {
            File archive = new File(tempDir, sources)
            archive.parentFile.mkdirs()
            archive.bytes = [] as byte[]
        }

        expect:
        GroovydocRunner.findJdkSources(javaHome) == (found ? new File(tempDir, found) : null)

        where:
        layout                          | home      | sources            || found
        'in the JDK'                    | 'jdk'     | 'jdk/lib/src.zip'  || 'jdk/lib/src.zip'
        'in the JDK above a nested JRE' | 'jdk/jre' | 'jdk/lib/src.zip'  || 'jdk/lib/src.zip'
        'nowhere'                       | 'jdk'     | null               || null
        'not an archive'                | 'jdk'     | 'jdk/lib/src.zip/' || null
    }
}
