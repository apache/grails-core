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
package org.apache.grails.data.core.querytail

import org.grails.datastore.mapping.core.DatastoreUtils
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

class QueryTailSettingsSpec extends Specification {

    void "capture is off unless the configuration turns it on"() {
        expect:
        !QueryTailSettings.from(DatastoreUtils.createPropertyResolver([:])).enabled
        !QueryTailSettings.from(null).enabled
        QueryTailSettings.from(null).file == null
    }

    void "the settings are read from grails.gorm.queryTail"() {
        when:
        QueryTailSettings settings = QueryTailSettings.from(DatastoreUtils.createPropertyResolver(
                'grails.gorm.queryTail.enabled': 'true',
                'grails.gorm.queryTail.file': '/tmp/statements.log'))

        then:
        settings.enabled
        settings.file == '/tmp/statements.log'
        settings.resolveFile() == new File('/tmp/statements.log')
    }

    void "a configuration that says nothing about query-tail takes the settings it falls back to"() {
        given: 'the configuration of a connection source added at runtime, which carries only its own settings'
        QueryTailSettings application = new QueryTailSettings(enabled: true, file: '/tmp/statements.log')
        def runtime = DatastoreUtils.createPropertyResolver(url: 'jdbc:h2:mem:runtime')

        when:
        QueryTailSettings settings = QueryTailSettings.from(runtime, application)

        then:
        settings.enabled
        settings.file == '/tmp/statements.log'
        !settings.is(application)
    }

    void "a configuration that sets query-tail wins over the settings it falls back to"() {
        given:
        QueryTailSettings application = new QueryTailSettings(enabled: true, file: '/tmp/statements.log')

        when:
        QueryTailSettings settings = QueryTailSettings.from(DatastoreUtils.createPropertyResolver(
                'grails.gorm.queryTail.enabled': 'false',
                'grails.gorm.queryTail.file': '/tmp/other.log'), application)

        then:
        !settings.enabled
        settings.file == '/tmp/other.log'

        and: 'without a fallback capture is off, and without a configuration the fallback holds'
        !QueryTailSettings.from(DatastoreUtils.createPropertyResolver([:]), null).enabled
        QueryTailSettings.from(null, application).enabled
    }

    @RestoreSystemProperties
    void "the default file is under the build directory the Gradle plugin names"() {
        given:
        System.clearProperty(QueryTailSettings.TARGET_DIR_OVERRIDE_PROPERTY)
        System.clearProperty(QueryTailSettings.BASE_DIR_PROPERTY)
        System.setProperty(QueryTailSettings.TARGET_DIR_PROPERTY, 'out/custom-build')

        expect:
        QueryTailSettings.defaultFile() == new File('out/custom-build', 'query-tail/capture.log')
        new QueryTailSettings().resolveFile() == new File('out/custom-build', 'query-tail/capture.log')
    }

    @RestoreSystemProperties
    void "the default file falls back to the build directory when the plugin has not named one"() {
        given:
        System.clearProperty(QueryTailSettings.TARGET_DIR_PROPERTY)
        System.clearProperty(QueryTailSettings.TARGET_DIR_OVERRIDE_PROPERTY)
        System.clearProperty(QueryTailSettings.BASE_DIR_PROPERTY)

        expect:
        QueryTailSettings.defaultFile() == new File('build', 'query-tail/capture.log')
    }

    @RestoreSystemProperties
    void "a build directory set by hand wins over the one the plugin passes"() {
        given:
        System.setProperty(QueryTailSettings.TARGET_DIR_PROPERTY, 'out/plugin')
        System.setProperty(QueryTailSettings.TARGET_DIR_OVERRIDE_PROPERTY, 'out/by-hand')

        expect:
        QueryTailSettings.defaultFile() == new File('out/by-hand', 'query-tail/capture.log')
    }

    @RestoreSystemProperties
    void "a relative build directory is joined to the application directory when that is known"() {
        given: 'directories that are absolute on this platform, with a drive on Windows'
        File applicationDir = new File('/apps/library').absoluteFile
        File absoluteBuildDir = new File('/var/build').absoluteFile
        System.setProperty(QueryTailSettings.TARGET_DIR_PROPERTY, 'out/custom-build')
        System.setProperty(QueryTailSettings.BASE_DIR_PROPERTY, applicationDir.path)

        expect:
        QueryTailSettings.defaultFile() == new File(new File(applicationDir, 'out/custom-build'), 'query-tail/capture.log')

        when: 'the build directory is absolute'
        System.setProperty(QueryTailSettings.TARGET_DIR_PROPERTY, absoluteBuildDir.path)

        then:
        QueryTailSettings.defaultFile() == new File(absoluteBuildDir, 'query-tail/capture.log')
    }
}
