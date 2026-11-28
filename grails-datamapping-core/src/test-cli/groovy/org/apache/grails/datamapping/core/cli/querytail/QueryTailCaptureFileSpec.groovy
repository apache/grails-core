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
package org.apache.grails.datamapping.core.cli.querytail

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import org.apache.grails.datastore.core.querytail.QueryTailSettings

/**
 * Where the query-tail command looks for the capture when it is given no file: where the
 * application it is run for writes it, read from the files of that application, which is not
 * started to be asked.
 */
class QueryTailCaptureFileSpec extends Specification {

    @TempDir
    File appDir

    String given

    def setup() {
        given = System.getProperty(QueryTailSettings.SETTING_FILE)
        System.clearProperty(QueryTailSettings.SETTING_FILE)
    }

    def cleanup() {
        if (given == null) {
            System.clearProperty(QueryTailSettings.SETTING_FILE)
        } else {
            System.setProperty(QueryTailSettings.SETTING_FILE, given)
        }
    }

    def "an application that configures nothing writes under its build directory, wherever the build has put that"() {
        expect:
        QueryTailTool.defaultCaptureFile(appDir, new File(appDir, 'build')) == new File(appDir, 'build/query-tail/capture.log')
        QueryTailTool.defaultCaptureFile(appDir, new File(appDir, 'out/moved')) == new File(appDir, 'out/moved/query-tail/capture.log')
    }

    @Unroll
    def "the file #where names is the one that is read, a relative one against the application directory"() {
        given:
        File conf = new File(appDir, 'grails-app/conf')
        conf.mkdirs()
        new File(conf, fileName).text = content

        expect:
        QueryTailTool.defaultCaptureFile(appDir, new File(appDir, 'build')) == new File(appDir, 'captured/statements.log')

        where:
        where                       | fileName             | content
        'application.yml'           | 'application.yml'    | 'grails:\n    gorm:\n        queryTail:\n            file: captured/statements.log\n'
        'application.groovy'        | 'application.groovy' | "grails.gorm.queryTail.file = 'captured/statements.log'\n"
        'a block of the Groovy file' | 'application.groovy' | "grails {\n    gorm {\n        queryTail {\n            file = 'captured/statements.log'\n        }\n    }\n}\n"
    }

    def "an absolute file is read where it is"() {
        given:
        File elsewhere = new File(appDir.parentFile, 'elsewhere/statements.log').absoluteFile
        File conf = new File(appDir, 'grails-app/conf')
        conf.mkdirs()
        new File(conf, 'application.groovy').text = "grails.gorm.queryTail.file = '${elsewhere.path.replace('\\', '\\\\')}'\n"

        expect:
        QueryTailTool.defaultCaptureFile(appDir, new File(appDir, 'build')) == elsewhere
    }

    def "application.groovy is read after application.yml, as the application reads them, and a configuration that names no file leaves the default"() {
        given:
        File conf = new File(appDir, 'grails-app/conf')
        conf.mkdirs()
        new File(conf, 'application.yml').text = 'grails:\n    gorm:\n        queryTail:\n            file: from-yml.log\n            enabled: true\n'

        expect:
        QueryTailTool.defaultCaptureFile(appDir, new File(appDir, 'build')) == new File(appDir, 'from-yml.log')

        when:
        new File(conf, 'application.groovy').text = "grails.gorm.queryTail.file = 'from-groovy.log'\n"

        then:
        QueryTailTool.defaultCaptureFile(appDir, new File(appDir, 'build')) == new File(appDir, 'from-groovy.log')

        when: 'neither names a file'
        new File(conf, 'application.yml').text = 'grails:\n    gorm:\n        queryTail:\n            enabled: true\n'
        new File(conf, 'application.groovy').text = "grails.profile = 'web'\n"

        then:
        QueryTailTool.defaultCaptureFile(appDir, new File(appDir, 'build')) == new File(appDir, 'build/query-tail/capture.log')
    }

    def "the file the build was given, as a system property, is read before the files of the application are"() {
        given: 'a configuration that could not even be read'
        File conf = new File(appDir, 'grails-app/conf')
        conf.mkdirs()
        new File(conf, 'application.groovy').text = 'this is { not Groovy'
        System.setProperty(QueryTailSettings.SETTING_FILE, 'given/statements.log')

        expect:
        QueryTailTool.defaultCaptureFile(appDir, new File(appDir, 'build')) == new File(appDir, 'given/statements.log')
    }
}
