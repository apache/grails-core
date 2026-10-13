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
package org.grails.datastore.gorm.jdbc.sqltail

import org.grails.datastore.mapping.core.DatastoreUtils
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

class SqlTailSettingsSpec extends Specification {

    void "capture is off unless the configuration turns it on"() {
        expect:
        !SqlTailSettings.from(DatastoreUtils.createPropertyResolver([:])).enabled
        !SqlTailSettings.from(null).enabled
        SqlTailSettings.from(null).file == null
    }

    void "the settings are read from grails.gorm.sqlTail"() {
        when:
        SqlTailSettings settings = SqlTailSettings.from(DatastoreUtils.createPropertyResolver(
                'grails.gorm.sqlTail.enabled': 'true',
                'grails.gorm.sqlTail.file': '/tmp/statements.log'))

        then:
        settings.enabled
        settings.file == '/tmp/statements.log'
        settings.resolveFile() == new File('/tmp/statements.log')
    }

    @RestoreSystemProperties
    void "the default file is under the build directory the Gradle plugin names"() {
        given:
        System.clearProperty(SqlTailSettings.TARGET_DIR_OVERRIDE_PROPERTY)
        System.clearProperty(SqlTailSettings.BASE_DIR_PROPERTY)
        System.setProperty(SqlTailSettings.TARGET_DIR_PROPERTY, 'out/custom-build')

        expect:
        SqlTailSettings.defaultFile() == new File('out/custom-build', 'sql-tail/capture.log')
        new SqlTailSettings().resolveFile() == new File('out/custom-build', 'sql-tail/capture.log')
    }

    @RestoreSystemProperties
    void "the default file falls back to the build directory when the plugin has not named one"() {
        given:
        System.clearProperty(SqlTailSettings.TARGET_DIR_PROPERTY)
        System.clearProperty(SqlTailSettings.TARGET_DIR_OVERRIDE_PROPERTY)
        System.clearProperty(SqlTailSettings.BASE_DIR_PROPERTY)

        expect:
        SqlTailSettings.defaultFile() == new File('build', 'sql-tail/capture.log')
    }

    @RestoreSystemProperties
    void "a build directory set by hand wins over the one the plugin passes"() {
        given:
        System.setProperty(SqlTailSettings.TARGET_DIR_PROPERTY, 'out/plugin')
        System.setProperty(SqlTailSettings.TARGET_DIR_OVERRIDE_PROPERTY, 'out/by-hand')

        expect:
        SqlTailSettings.defaultFile() == new File('out/by-hand', 'sql-tail/capture.log')
    }

    @RestoreSystemProperties
    void "a relative build directory is joined to the application directory when that is known"() {
        given:
        System.setProperty(SqlTailSettings.TARGET_DIR_PROPERTY, 'out/custom-build')
        System.setProperty(SqlTailSettings.BASE_DIR_PROPERTY, '/apps/library')

        expect:
        SqlTailSettings.defaultFile() == new File('/apps/library/out/custom-build', 'sql-tail/capture.log')

        when: 'the build directory is absolute'
        System.setProperty(SqlTailSettings.TARGET_DIR_PROPERTY, '/var/build')

        then:
        SqlTailSettings.defaultFile() == new File('/var/build', 'sql-tail/capture.log')
    }
}
