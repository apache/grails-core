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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import grails.util.BuildSettings

/**
 * Unit tests for {@link GrailsGormIdTypeProvider}, verifying that the nested
 * {@code grails { gorm { defaultIdType } }} setting is translated into the expected {@code -D} system
 * property for the Groovy compiler worker JVM.
 *
 * @since 8.0
 */
class GrailsGormIdTypeProviderSpec extends Specification {

    private static GrailsGormOptions options() {
        Project project = ProjectBuilder.builder().build()
        new GrailsExtension(project).gorm
    }

    void 'the default is a Long id'() {
        expect:
        options().defaultIdType.get() == BuildSettings.GORM_DEFAULT_ID_TYPE_LONG
    }

    void 'no argument is emitted for the default, which is what the compiler already does'() {
        expect:
        new GrailsGormIdTypeProvider(options()).asArguments().toList() == []
    }

    void 'no argument is emitted where the default is stated explicitly'() {
        given:
        GrailsGormOptions gorm = options()
        gorm.defaultIdType.set(BuildSettings.GORM_DEFAULT_ID_TYPE_LONG)

        expect:
        new GrailsGormIdTypeProvider(gorm).asArguments().toList() == []
    }

    void 'native identity types are published as a system property'() {
        given:
        GrailsGormOptions gorm = options()
        gorm.defaultIdType.set(BuildSettings.GORM_DEFAULT_ID_TYPE_NATIVE)

        expect:
        new GrailsGormIdTypeProvider(gorm).asArguments().toList() ==
                ["-D${BuildSettings.GORM_DEFAULT_ID_TYPE}=${BuildSettings.GORM_DEFAULT_ID_TYPE_NATIVE}".toString()]
    }

    void 'identity type values use the same case-insensitive contract as the compiler'() {
        given:
        GrailsGormOptions gorm = options()
        gorm.defaultIdType.set(configured)

        expect:
        new GrailsGormIdTypeProvider(gorm).asArguments().toList() == arguments

        where:
        configured || arguments
        'Long'     || []
        'Native'   || ["-D${BuildSettings.GORM_DEFAULT_ID_TYPE}=${BuildSettings.GORM_DEFAULT_ID_TYPE_NATIVE}".toString()]
    }

    void 'the effective value is exposed as a task input so that changing it recompiles'() {
        given:
        GrailsGormOptions gorm = options()
        gorm.defaultIdType.set(BuildSettings.GORM_DEFAULT_ID_TYPE_NATIVE)

        expect:
        new GrailsGormIdTypeProvider(gorm).defaultIdType == BuildSettings.GORM_DEFAULT_ID_TYPE_NATIVE
    }

    void 'a value GORM does not recognise fails the build rather than compiling a Long id'() {
        given:
        GrailsGormOptions gorm = options()
        gorm.defaultIdType.set('objectid')

        when:
        new GrailsGormIdTypeProvider(gorm).asArguments()

        then:
        InvalidUserDataException e = thrown()
        e.message.contains('objectid')
        e.message.contains('defaultIdType')
    }

    void 'the setting is configurable through the nested gorm block'() {
        given:
        Project project = ProjectBuilder.builder().build()
        GrailsExtension grails = new GrailsExtension(project)

        when:
        grails.gorm {
            defaultIdType = BuildSettings.GORM_DEFAULT_ID_TYPE_NATIVE
        }

        then:
        grails.gorm.defaultIdType.get() == BuildSettings.GORM_DEFAULT_ID_TYPE_NATIVE
    }
}
