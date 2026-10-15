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
import spock.lang.Unroll

class WriteGrailsVersionInfoTaskSpec extends Specification {

    @TempDir
    File tmp

    void 'parse failure throws GradleException instead of falling through to NPE'() {
        given:
        Project project = ProjectBuilder.builder().withProjectDir(tmp).build()
        WriteGrailsVersionInfoTask task = project.tasks.register('writeGrailsVersionInfo', WriteGrailsVersionInfoTask).get()
        File pom = new File(tmp, 'pom-default.xml')
        pom.text = '<not-xml'
        File outDir = new File(tmp, 'versions')
        task.projectVersion.set('8.1.0-SNAPSHOT')
        task.bomPublicationFile.set(pom)
        task.versionsDirectory.set(outDir)

        when:
        task.writeVersionInfo()

        then:
        GradleException e = thrown()
        e.message.contains('Unable to parse BOM publication file')
        !(e instanceof NullPointerException)
        e.cause != null
    }

    void 'end of support date is written beside the BOM versions'() {
        given:
        WriteGrailsVersionInfoTask task = versionInfoTask()
        task.endOfSupport.set(' 2027-07-31 ')

        when:
        task.writeVersionInfo()

        then:
        writtenVersions() == [
                'grails.endOfSupport': '2027-07-31',
                'grails.version'     : '8.1.0-SNAPSHOT',
                'spring-boot.version': '4.1.1'
        ]
    }

    void 'no end of support date is written when none is configured'() {
        given:
        WriteGrailsVersionInfoTask task = versionInfoTask()

        when:
        task.writeVersionInfo()

        then:
        writtenVersions() == [
                'grails.version'     : '8.1.0-SNAPSHOT',
                'spring-boot.version': '4.1.1'
        ]
    }

    @Unroll
    void 'end of support date #date fails the build'() {
        given:
        WriteGrailsVersionInfoTask task = versionInfoTask()
        task.endOfSupport.set(date)

        when:
        task.writeVersionInfo()

        then:
        GradleException e = thrown()
        e.message == "grailsEndOfSupport must be an ISO date (yyyy-MM-dd) but was: ${date}"

        where:
        date << ['2027-13-01', '31/07/2027', '20207-07-31']
    }

    private WriteGrailsVersionInfoTask versionInfoTask() {
        Project project = ProjectBuilder.builder().withProjectDir(tmp).build()
        WriteGrailsVersionInfoTask task = project.tasks.register('writeGrailsVersionInfo', WriteGrailsVersionInfoTask).get()
        File pom = new File(tmp, 'pom-default.xml')
        pom.text = """<project>
    <properties>
        <spring-boot.version>4.1.1</spring-boot.version>
    </properties>
</project>
"""
        task.projectVersion.set('8.1.0-SNAPSHOT')
        task.bomPublicationFile.set(pom)
        task.versionsDirectory.set(new File(tmp, 'versions'))
        task
    }

    private Map<String, String> writtenVersions() {
        Properties properties = new Properties()
        new File(tmp, 'versions/grails-versions.properties').withInputStream { properties.load(it) }
        new TreeMap<String, String>(properties as Map<String, String>)
    }
}
