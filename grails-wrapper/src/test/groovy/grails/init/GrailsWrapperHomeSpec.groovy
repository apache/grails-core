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
import uk.org.webcompere.systemstubs.SystemStubs

/**
 * Where the wrapper keeps what it downloads, and that it is never the {@code .grails} directory of a project.
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

    def "the record of a project is refused as the home, named #how"() {
        given:
        File record = new File(dir, 'project/.grails')
        record.mkdirs()
        new File(record, 'project.yml').text = 'format: 1\n'

        when:
        SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_HOME', environment ? record.path : null).execute {
            GrailsWrapperHome.findGrailsHome(environment ? null : record.path)
        }

        then:
        IllegalArgumentException e = thrown()
        e.message == "${description} [${record.path}] is the record of a Grails project (it has project.yml), which is committed " +
                'with the project. Name another directory for the downloads of the wrapper.'
        !new File(record, 'wrapper').exists()

        where:
        how                        | environment | description
        'by GRAILS_WRAPPER_HOME'   | true        | 'GRAILS_WRAPPER_HOME environment variable'
        'to the wrapper directly'  | false       | 'Specified Grails Home'
    }
}
