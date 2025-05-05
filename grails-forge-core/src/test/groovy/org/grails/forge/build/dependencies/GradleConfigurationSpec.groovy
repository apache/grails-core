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

package org.grails.forge.build.dependencies

import org.grails.forge.build.gradle.GradleConfiguration
import org.grails.forge.options.Language
import org.grails.forge.options.TestFramework
import spock.lang.Specification
import spock.lang.Unroll

class GradleConfigurationSpec extends Specification {
    void "GradleConfiguration::toString() returns the gradle configuration"() {
        expect:
        'runtimeOnly' == GradleConfiguration.RUNTIME_ONLY.toString()
    }

    @Unroll("#description")
    void "it is possible to adapt from source and phases to Gradle configuration"(Source source,
                                                                                  List<Phase> phases, GradleConfiguration configuration,
                                                                                  String description) {
        configuration == GradleConfiguration.of(new Scope(source, phases), Language.GROOVY, TestFramework.JUNIT).get()

        where:
        source           | phases                             || configuration
        Source.MAIN      | [Phase.RUNTIME, Phase.COMPILATION] || GradleConfiguration.IMPLEMENTATION
        Source.MAIN      | [Phase.RUNTIME]                    || GradleConfiguration.CONSOLE
        Source.MAIN      | [Phase.RUNTIME]                    || GradleConfiguration.RUNTIME_ONLY
        Source.TEST      | [Phase.RUNTIME]                    || GradleConfiguration.TEST_RUNTIME_ONLY
        Source.MAIN      | [Phase.COMPILATION]                || GradleConfiguration.COMPILE_ONLY
        Source.TEST      | [Phase.COMPILATION]                || GradleConfiguration.TEST_COMPILE_ONLY
        Source.TEST      | [Phase.RUNTIME, Phase.COMPILATION] || GradleConfiguration.TEST_IMPLEMENTATION
        Source.MAIN      | [Phase.ANNOTATION_PROCESSING]      || GradleConfiguration.ANNOTATION_PROCESSOR
        Source.TEST      | [Phase.ANNOTATION_PROCESSING]      || GradleConfiguration.TEST_ANNOTATION_PROCESSOR
        Source.BUILD_SRC | [Phase.BUILD]                      || GradleConfiguration.BUILD
        description = "$source ${phases.join(",")} should return ${configuration.toString()}"
    }
}
