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

    // what Gradle publishes for gradle-9.9.2-bin.zip
    static final String SHA256 = '971fcd7150ca4ba04852c0b2ca0d2d0ab9468e14b03473eab19528809914c76b'

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

    void 'the version of Gradle applications are generated with, the SHA-256 of its distribution and the earliest one Grails is built with are written beside the BOM versions'() {
        given:
        WriteGrailsVersionInfoTask task = versionInfoTask()
        task.gradleVersion.set('9.9.2')
        task.gradleDistributionSha256Sum.set(' 971fcd7150ca4ba04852c0b2ca0d2d0ab9468e14b03473eab19528809914c76b ')
        task.gradleMinimumVersion.set(' 9.7 ')

        when:
        task.writeVersionInfo()

        then:
        writtenVersions() == [
                'gradle.minimum'     : '9.7',
                'gradle.sha256'      : '971fcd7150ca4ba04852c0b2ca0d2d0ab9468e14b03473eab19528809914c76b',
                'gradle.version'     : '9.9.2',
                'grails.version'     : '8.1.0-SNAPSHOT',
                'spring-boot.version': '4.1.1'
        ]
    }

    void 'a version of Gradle that is a release candidate is written as it is'() {
        given:
        WriteGrailsVersionInfoTask task = versionInfoTask()
        task.gradleVersion.set('9.10.0-rc-1')
        task.gradleDistributionSha256Sum.set('971fcd7150ca4ba04852c0b2ca0d2d0ab9468e14b03473eab19528809914c76b')
        task.gradleMinimumVersion.set('9.10')

        when:
        task.writeVersionInfo()

        then:
        writtenVersions()['gradle.version'] == '9.10.0-rc-1'
        writtenVersions()['gradle.minimum'] == '9.10'
    }

    @Unroll
    void 'the Gradle versions #version and #minimum fail the build: #message'() {
        given:
        WriteGrailsVersionInfoTask task = versionInfoTask()
        task.gradleVersion.set(version)
        task.gradleDistributionSha256Sum.set(SHA256)
        task.gradleMinimumVersion.set(minimum)

        when:
        task.writeVersionInfo()

        then:
        GradleException e = thrown()
        e.message == message

        where:
        version    | minimum || message
        'latest'   | '9.7'   || 'gradle.version must be a version of Gradle, such as 9.9.2, but was: latest'
        '9.9.2'    | '9'     || 'gradle.minimum must be a version of Gradle, such as 9.9.2, but was: 9'
        '9.9.2'    | '9.10'  || 'The earliest version of Gradle Grails is built with, 9.10, is later than the version applications are generated with, 9.9.2'
        '9.9.2'    | '9.9.3' || 'The earliest version of Gradle Grails is built with, 9.9.3, is later than the version applications are generated with, 9.9.2'
    }

    @Unroll
    void 'the SHA-256 #checksum of the distribution fails the build'() {
        given:
        WriteGrailsVersionInfoTask task = versionInfoTask()
        task.gradleVersion.set('9.9.2')
        task.gradleDistributionSha256Sum.set(checksum)

        when:
        task.writeVersionInfo()

        then:
        GradleException e = thrown()
        e.message == "gradle.sha256 must be the SHA-256 Gradle publishes for the distribution, 64 lowercase hexadecimal digits, but was: ${checksum.trim()}"

        where:
        checksum << [SHA256.toUpperCase(), SHA256.substring(1), SHA256 + '0', 'sha256:' + SHA256, '', 'latest']
    }

    @Unroll
    void 'the version of Gradle and the SHA-256 of its distribution go together: #given alone fails the build'() {
        given:
        WriteGrailsVersionInfoTask task = versionInfoTask()
        if (given == 'gradle.version') {
            task.gradleVersion.set('9.9.2')
        } else {
            task.gradleDistributionSha256Sum.set(SHA256)
        }

        when:
        task.writeVersionInfo()

        then:
        GradleException e = thrown()
        e.message == "gradle.version and gradle.sha256, the SHA-256 of its distribution, are given together or not at all, but only ${given} was given"

        where:
        given << ['gradle.version', 'gradle.sha256']
    }

    @Unroll
    void 'a property #property of the BOM is not taken for one of Gradle'() {
        given:
        WriteGrailsVersionInfoTask task = versionInfoTask()
        new File(tmp, 'pom-default.xml').text = "<project><properties><${property}>1.0</${property}></properties></project>"
        task.gradleVersion.set('9.9.2')
        task.gradleDistributionSha256Sum.set(SHA256)
        task.gradleMinimumVersion.set('9.7')

        when:
        task.writeVersionInfo()

        then:
        GradleException e = thrown()
        e.message == "The BOM has a property ${property}, which this task writes itself from the build"

        where:
        property << ['gradle.version', 'gradle.sha256', 'gradle.minimum']
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
