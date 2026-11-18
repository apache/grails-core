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
package org.apache.grails.datastore.core.querytail

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettingsBuilder
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

class QueryTailSettingsSpec extends Specification {

    void "every connection source's settings are bound from grails.gorm.queryTail along with the rest"() {
        when:
        ConnectionSourceSettings settings = new ConnectionSourceSettingsBuilder(DatastoreUtils.createPropertyResolver(
                'grails.gorm.queryTail.enabled': 'true',
                'grails.gorm.queryTail.file': '/tmp/statements.log')).build()
        ConnectionSourceSettings plain = new ConnectionSourceSettingsBuilder(DatastoreUtils.createPropertyResolver([:])).build()

        then:
        settings.queryTail.enabled
        settings.queryTail.file == '/tmp/statements.log'
        !plain.queryTail.enabled
        plain.queryTail.file == null

        and: 'a copy of the settings carries them as well'
        settings.clone().queryTail.enabled
        settings.clone().queryTail.file == '/tmp/statements.log'
    }

    void "the query-tail settings alone are bound as every connection source binds them, and nothing else of grails.gorm is read"() {
        given: 'a setting of grails.gorm that fails to bind, as a class of another datastore that cannot be loaded does'
        Map<String, Object> config = ['grails.gorm.queryTail.enabled'                : 'true',
                                      'grails.gorm.queryTail.file'                   : '/tmp/statements.log',
                                      'grails.gorm.multiTenancy.tenantResolverClass' : 'does.not.Exist']

        when: 'every setting of grails.gorm is bound'
        new ConnectionSourceSettingsBuilder(DatastoreUtils.createPropertyResolver(config)).build()

        then:
        thrown(ConfigurationException)

        when: 'only those of query-tail are'
        QueryTailSettings settings = QueryTailSettings.of(DatastoreUtils.createPropertyResolver(config))
        QueryTailSettings plain = QueryTailSettings.of(DatastoreUtils.createPropertyResolver([:]))

        then:
        settings.enabled
        settings.file == '/tmp/statements.log'
        !plain.enabled
        plain.file == null
    }

    @RestoreSystemProperties
    void "a relative capture file is joined to the application directory when that is known, as the command joins it"() {
        given: 'directories that are absolute on this platform, with a drive on Windows'
        File applicationDir = new File('/apps/library').absoluteFile
        File absoluteFile = new File('/var/statements.log').absoluteFile
        System.setProperty(QueryTailSettings.BASE_DIR_PROPERTY, applicationDir.path)

        expect:
        new QueryTailSettings(file: 'tmp/statements.log').resolveFile() == new File(applicationDir, 'tmp/statements.log')
        new QueryTailSettings(file: absoluteFile.path).resolveFile() == absoluteFile
    }

    void "a capture file is found from the directories whoever asks gives, as the command gives them from its project"() {
        given: 'directories that are absolute on this platform, with a drive on Windows'
        File applicationDir = new File('/apps/library').absoluteFile
        File buildDir = new File(applicationDir, 'build')
        File absoluteFile = new File('/var/statements.log').absoluteFile

        expect:
        QueryTailSettings.captureFile(null, applicationDir, buildDir) == new File(buildDir, 'query-tail/capture.log')
        QueryTailSettings.captureFile('', applicationDir, buildDir) == new File(buildDir, 'query-tail/capture.log')
        QueryTailSettings.captureFile('tmp/statements.log', applicationDir, buildDir) == new File(applicationDir, 'tmp/statements.log')
        QueryTailSettings.captureFile(absoluteFile.path, applicationDir, buildDir) == absoluteFile
        QueryTailSettings.captureFile('tmp/statements.log', null, buildDir) == new File('tmp/statements.log')
    }

    @RestoreSystemProperties
    void "an application finds its capture file as the command finds it from the same directories"() {
        given:
        File applicationDir = new File('/apps/library').absoluteFile
        File buildDir = new File(applicationDir, 'out/custom-build')
        System.clearProperty(QueryTailSettings.TARGET_DIR_OVERRIDE_PROPERTY)
        System.setProperty(QueryTailSettings.TARGET_DIR_PROPERTY, 'out/custom-build')
        System.setProperty(QueryTailSettings.BASE_DIR_PROPERTY, applicationDir.path)

        expect:
        new QueryTailSettings(file: 'tmp/statements.log').resolveFile() ==
                QueryTailSettings.captureFile('tmp/statements.log', applicationDir, buildDir)
        new QueryTailSettings().resolveFile() == QueryTailSettings.captureFile(null, applicationDir, buildDir)
        QueryTailSettings.defaultFile() == QueryTailSettings.captureFile(null, applicationDir, buildDir)
    }

    @RestoreSystemProperties
    void "a relative capture file is relative to the working directory when the application directory is not known"() {
        given:
        System.clearProperty(QueryTailSettings.BASE_DIR_PROPERTY)

        expect:
        new QueryTailSettings(file: 'tmp/statements.log').resolveFile() == new File('tmp/statements.log')
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
