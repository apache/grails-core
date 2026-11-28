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
package grails.init

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll
import uk.org.webcompere.systemstubs.SystemStubs

/**
 * Where the wrapper keeps what it downloads: a project is not looked at unless its directory is named.
 */
class GrailsWrapperHomeSpec extends Specification {

    @TempDir
    File dir

    def "the home is the directory GRAILS_WRAPPER_HOME names, and is created"() {
        given:
        File home = new File(dir, 'downloads')

        when:
        File found = null
        SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_HOME', home.path).execute {
            found = GrailsWrapperHome.findGrailsHome(null)
        }

        then:
        found == home
        home.isDirectory()
    }

    def "without an override the home is .grails of the home directory, whatever the project has"() {
        given: 'a project with a record of its own, and the wrapper started in it'
        File project = new File(dir, 'project')
        new File(project, '.grails').mkdirs()
        new File(project, '.grails/project.yml').text = 'format: 1\n'
        File userHome = new File(dir, 'user')
        userHome.mkdirs()

        when:
        File found = null
        SystemStubs.restoreSystemProperties {
            System.setProperty('user.home', userHome.path)
            System.setProperty('user.dir', project.path)
            SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_HOME', null).execute {
                found = GrailsWrapperHome.findGrailsHome(null)
            }
        }

        then:
        found == new File(userHome.canonicalFile, '.grails')
        !new File(project, '.grails/wrapper').exists()
    }

    def "the .grails directory of a project can be named as the home"() {
        given:
        File record = new File(dir, 'project/.grails')
        record.mkdirs()
        new File(record, 'project.yml').text = 'format: 1\n'

        when:
        GrailsWrapperHome home = null
        SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_HOME', record.path).execute {
            home = new GrailsWrapperHome(new LinkedHashSet<>([GrailsReleaseType.RELEASE]), null)
        }

        then:
        home.home == record.canonicalFile
        home.wrapperDirectory == new File(record.canonicalFile, 'wrapper')
        new File(record, 'project.yml').text == 'format: 1\n'
    }

    def "a directory that is given is the home, before the one GRAILS_WRAPPER_HOME names"() {
        given:
        File given = new File(dir, 'given/home')
        File environment = new File(dir, 'environment')

        when:
        File found = null
        SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_HOME', environment.path).execute {
            found = GrailsWrapperHome.findGrailsHome(given.path)
        }

        then:
        found == given
        given.isDirectory()
        !environment.exists()
    }

    @Unroll
    def "a home that #situation is refused, and says where it was named"() {
        given:
        File file = new File(dir, 'file')
        file.text = 'not a directory'
        String named = new File(dir, path).path

        when:
        SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_HOME', fromEnvironment ? named : null).execute {
            GrailsWrapperHome.findGrailsHome(fromEnvironment ? null : named)
        }

        then:
        IllegalArgumentException e = thrown()
        e.message == "${description} [${named}] is not a directory"
        file.text == 'not a directory'

        where:
        situation                         | path        | fromEnvironment | description
        'is a file'                       | 'file'      | true            | 'GRAILS_WRAPPER_HOME environment variable'
        'cannot be created, under a file' | 'file/home' | true            | 'GRAILS_WRAPPER_HOME environment variable'
        'is given and is a file'          | 'file'      | false           | 'Specified Grails Home'
        'is given and cannot be created'  | 'file/home' | false           | 'Specified Grails Home'
    }

    def "a file where the default home would be is refused"() {
        given:
        File userHome = new File(dir, 'user')
        userHome.mkdirs()
        new File(userHome, '.grails').text = 'not a directory'

        when:
        SystemStubs.restoreSystemProperties {
            System.setProperty('user.home', userHome.path)
            SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_HOME', null).execute {
                GrailsWrapperHome.findGrailsHome(null)
            }
        }

        then:
        IllegalStateException e = thrown()
        e.message == "Grails Wrapper Home [${new File(userHome.canonicalFile, '.grails')}] is not a directory."
        new File(userHome, '.grails').text == 'not a directory'
    }

    def "a home whose wrapper is a file is refused"() {
        given:
        new File(dir, 'wrapper').text = 'not a directory'

        when:
        new GrailsWrapperHome(new LinkedHashSet<>([GrailsReleaseType.RELEASE]), dir.path)

        then:
        IllegalStateException e = thrown()
        e.message == "GRAILS_WRAPPER_HOME must contain a wrapper directory. File exists instead at ${new File(dir.canonicalFile, 'wrapper').absolutePath}"
    }

    def "a directory in the wrapper directory that is not named by a version is refused, a file is passed over"() {
        given:
        new File(dir, 'wrapper/9.0.0').mkdirs()
        new File(dir, 'wrapper/notes.txt').text = 'a file'

        when:
        GrailsWrapperHome home = new GrailsWrapperHome(new LinkedHashSet<>([GrailsReleaseType.RELEASE]), dir.path)

        then:
        home.versions == [new GrailsVersion('9.0.0')]

        when:
        File other = new File(dir, 'wrapper/latest')
        other.mkdirs()
        new GrailsWrapperHome(new LinkedHashSet<>([GrailsReleaseType.RELEASE]), dir.path)

        then:
        IllegalStateException e = thrown()
        e.message == "Grails Version [latest] at [${new File(dir.canonicalFile, 'wrapper/latest').absolutePath}] is not a valid Grails version."
    }

    @Unroll
    def "of the versions in the home the latest of the allowed types #allowed is #latest"() {
        given:
        ['9.0.0', '9.0.1', '9.1.0-RC1', '9.1.0-M2', '9.1.0-SNAPSHOT', '8.0.5'].each { String version ->
            new File(dir, "wrapper/${version}").mkdirs()
        }

        when:
        GrailsWrapperHome home = new GrailsWrapperHome(allowed == null ? null : new LinkedHashSet<>(allowed), dir.path)

        then:
        home.latestVersion == new GrailsVersion(latest)
        home.versions.size() == count
        home.latestWrapperImplementation == new File(dir.canonicalFile, "wrapper/${latest}/grails-cli-${latest}-all.jar")

        where:
        allowed                                           | latest           | count
        [GrailsReleaseType.RELEASE]                       | '9.0.1'          | 3
        [GrailsReleaseType.RELEASE, GrailsReleaseType.RC] | '9.1.0-RC1'      | 4
        [GrailsReleaseType.SNAPSHOT]                      | '9.1.0-SNAPSHOT' | 1
        null                                              | '9.1.0-RC1'      | 6
    }

    def "a home that has no version has no latest version and no command line"() {
        when:
        GrailsWrapperHome home = new GrailsWrapperHome(new LinkedHashSet<>([GrailsReleaseType.RELEASE]), new File(dir, 'new').path)

        then:
        new File(dir, 'new/wrapper').isDirectory()
        home.versions.empty
        home.latestVersion == null
        home.latestWrapperImplementation == null
        home.getVersionDirectory(null) == null
    }
}
