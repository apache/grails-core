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
package org.grails.gradle.plugin.core

/**
 * Functional tests for {@code grails { gorm { defaultIdType } }}, driven through the public build
 * configuration rather than the argument provider behind it.
 *
 * <p>Covers the wiring itself: a consumer project states the setting the way an application does, and
 * the compiler worker JVM arguments are read back off {@code compileGroovy}. Those arguments are how
 * the setting reaches GORM's entity transformation, so a project whose {@code compileGroovy} does not
 * carry them compiles every domain class with a {@code Long} id however the build is configured -
 * which the unit tests of the provider in isolation cannot detect.</p>
 *
 * @since 8.0
 * @see GrailsGradlePlugin#configureGroovy
 * @see GrailsGormIdTypeProvider
 */
class GormDefaultIdTypeFunctionalSpec extends GradleSpecification {

    void 'native identity types reach the Groovy compiler worker JVM'() {
        given:
        setupTestResourceProject('gorm-default-id-type')

        when:
        def result = executeTask('inspectGormIdType', ['-PidType=native'])

        then:
        result.output.contains('GORM_ID_TYPE_ARGS=[-Dgrails.gorm.defaultIdType=native]')
        result.output.contains('PACKAGED_GORM_ID_TYPE=native')
    }

    void 'a project that states nothing sends nothing, leaving the compiler on its Long default'() {
        given:
        setupTestResourceProject('gorm-default-id-type')

        when:
        def result = executeTask('inspectGormIdType')

        then:
        result.output.contains('GORM_ID_TYPE_ARGS=[]')
        result.output.contains('PACKAGED_GORM_ID_TYPE=long')
    }

    void 'stating the default explicitly also sends nothing'() {
        given:
        setupTestResourceProject('gorm-default-id-type')

        when:
        def result = executeTask('inspectGormIdType', ['-PidType=long'])

        then:
        result.output.contains('GORM_ID_TYPE_ARGS=[]')
    }

    void 'a value GORM does not recognise fails the build'() {
        given:
        setupTestResourceProject('gorm-default-id-type')

        when:
        executeTask('inspectGormIdType', ['-PidType=objectid'])

        then:
        Exception e = thrown()
        e.message.contains('objectid')
        e.message.contains('defaultIdType')
    }

    void 'a separately compiled plugin can preserve a portable identity for its consumer'() {
        given:
        setupTestResourceProject('gorm-default-id-type-plugin-consumer')

        when:
        def result = executeTask('inspectGormIdType')

        then:
        result.output.contains('PLUGIN_GORM_ID_TYPE_ARGS=[]')
        result.output.contains('APP_GORM_ID_TYPE_ARGS=[-Dgrails.gorm.defaultIdType=native]')
        result.output.contains('PLUGIN_DECLARED_ID_TYPE=java.io.Serializable')
    }
}
