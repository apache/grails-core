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

class GrailsGradlePreserveParametersSpec extends GradleSpecification {

    def "Grails extension is created with default preserveParameterNames = true"() {
        given:
        setupTestResourceProject('preserve-params-default')

        when:
        def result = executeTask('inspectPreserveParam')

        then:
        result.output.contains("HAS_PRESERVE_PARAM_ENABLED=true")
    }

    def "preserveParameterNames can be configured to false via grails block"() {
        given:
        setupTestResourceProject('preserve-params-disabled')

        when:
        def result = executeTask('inspectPreserveParam')

        then:
        result.output.contains("HAS_PRESERVE_PARAM_ENABLED=false")
    }

    def "preserveParameterNames falls back to true convention when assigned null"() {
        given:
        setupTestResourceProject('preserve-params-null')

        when:
        def result = executeTask('inspectPreserveParam')

        then:
        //  For Gradle properties, assigning null does not set the value to null as the final resolved value.
        // It clears/unsets the explicit value. Once the explicit value is absent, getOrNull() resolves to
        // the property’s convention value, which is true.
        result.output.contains("HAS_PRESERVE_PARAM_ENABLED=true")
    }

    def "preserveParameterNames provider with no value leaves GroovyCompile parameters unchanged"() {
        given:
        setupTestResourceProject('preserve-params-provider-null')

        when:
        def result = executeTask('inspectPreserveParam')

        then:
        // when actually assigned via a provider null, the value will not be set and will remain the default value, which
        // is false for GroovyCompile
        result.output.contains("HAS_PRESERVE_PARAM_ENABLED=false")
    }

    def "GroovyCompile tasks get parameters = true when preserveParameterNames is enabled"() {
        given:
        setupTestResourceProject('preserve-params-enabled')

        when:
        def result = executeTask('inspectPreserveParam')

        then:
        result.output.contains("HAS_PRESERVE_PARAM_ENABLED=true")
    }

}
