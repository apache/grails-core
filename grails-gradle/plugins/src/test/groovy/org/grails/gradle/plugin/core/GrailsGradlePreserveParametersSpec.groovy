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

    def "GroovyCompile tasks get parameters = true when preserveParameterNames is enabled"() {
        given:
        setupTestResourceProject('preserve-params-enabled')

        when:
        def result = executeTask('inspectPreserveParam')

        then:
        result.output.contains("HAS_PRESERVE_PARAM_ENABLED=true")
    }

}
