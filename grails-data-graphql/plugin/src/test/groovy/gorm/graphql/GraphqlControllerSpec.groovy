package gorm.graphql

import grails.testing.web.controllers.ControllerUnitTest
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import org.grails.gorm.graphql.plugin.DefaultGraphQLContextBuilder
import org.grails.gorm.graphql.plugin.GrailsGraphQLConfiguration
import org.grails.gorm.graphql.plugin.GraphqlController
import spock.lang.Specification

class GraphqlControllerSpec extends Specification implements ControllerUnitTest<GraphqlController> {

    def setup() {
    }

    def cleanup() {
    }

    Closure doWithSpring() {{->
        graphQLContextBuilder(DefaultGraphQLContextBuilder)
    }}

    private ExecutionResult mockExecutionResult() {
        Mock(ExecutionResult) {
            1 * getErrors() >> []
            1 * getData() >> ''
            1 * getExtensions() >> [:]
        }
    }

    private GrailsGraphQLConfiguration mockConfiguration() {
        Stub(GrailsGraphQLConfiguration) {
            getEnabled() >> true
        }
    }
      
    void "test graphql with invalid request"() {
        when:
        controller.grailsGraphQLConfiguration = mockConfiguration()
        controller.index()

        then:
        view == '/graphql/invalidRequest'
        model.error == 'Invalid GraphQL request'
    }

    void "test graphql with GET request"() {
        given:
        GraphQL graphQL = Mock(GraphQL)
        controller.graphQL = graphQL
        controller.grailsGraphQLConfiguration = mockConfiguration()

        when:
        params.query = 'query'
        controller.index()

        then:
        1 * graphQL.execute(_) >> { ExecutionInput ei ->
            assert ei.query == "query"
            assert ei.operationName == null
            assert ei.context == [locale: request.locale]
            assert ei.variables.isEmpty()
            mockExecutionResult()
        }

        when:
        params.query = 'query2'
        params.operationName = 'operationName'
        controller.index()

        then:
        1 * graphQL.execute(_) >> { ExecutionInput ei ->
            assert ei.query == "query2"
            assert ei.operationName == "operationName"
            assert ei.context == [locale: request.locale]
            assert ei.variables.isEmpty()
            mockExecutionResult()
        }

        when:
        params.query = 'query2'
        params.operationName = 'operationName'
        params.variables = '{"foo": 2}'
        controller.index()

        then:
        1 * graphQL.execute(_) >> { ExecutionInput ei ->
            assert ei.query == "query2"
            assert ei.operationName == "operationName"
            assert ei.context == [locale: request.locale]
            assert ei.variables == [foo: 2]
            mockExecutionResult()
        }
    }

    void "test graphql with POST body application/json (query only)"() {
        given:
        GraphQL graphQL = Mock(GraphQL)
        controller.graphQL = graphQL
        controller.grailsGraphQLConfiguration = mockConfiguration()

        when:
        request.setJson('{"query": "query"}')
        request.method = 'POST'
        controller.index()

        then:
        1 * graphQL.execute(_) >> { ExecutionInput ei ->
            assert ei.query == "query"
            assert ei.operationName == null
            assert ei.context == [locale: request.locale]
            assert ei.variables.isEmpty()
            mockExecutionResult()
        }
    }

    void "test graphql with POST body application/json (query and operationName)"() {
        given:
        GraphQL graphQL = Mock(GraphQL)
        controller.graphQL = graphQL
        controller.grailsGraphQLConfiguration = mockConfiguration()

        when:
        request.setJson('{"query": "query2", "operationName": "operationName"}')
        request.method = 'POST'
        controller.index()

        then:
        1 * graphQL.execute(_) >> { ExecutionInput ei ->
            assert ei.query == "query2"
            assert ei.operationName == "operationName"
            assert ei.context == [locale: request.locale]
            assert ei.variables.isEmpty()
            mockExecutionResult()
        }
    }

    void "test graphql with POST body application/json (all data)"() {
        given:
        GraphQL graphQL = Mock(GraphQL)
        controller.graphQL = graphQL
        controller.grailsGraphQLConfiguration = mockConfiguration()

        when:
        request.setJson('{"query": "query2", "operationName": "operationName", "variables": {"foo": 2}}')
        request.method = 'POST'
        controller.index()

        then:
        1 * graphQL.execute(_) >> { ExecutionInput ei ->
            assert ei.query == "query2"
            assert ei.operationName == "operationName"
            assert ei.context == [locale: request.locale]
            assert ei.variables == [foo: 2]
            mockExecutionResult()
        }
    }

    void "test graphql with POST body application/graphql"() {
        given:
        GraphQL graphQL = Mock(GraphQL)
        controller.graphQL = graphQL
        controller.grailsGraphQLConfiguration = mockConfiguration()

        when:
        request.setJson('{"query": "query"}')
        request.setContentType("application/graphql; charset=UTF-8")
        request.method = 'POST'
        controller.index()

        then:
        1 * graphQL.execute(_) >> { ExecutionInput ei ->
            assert ei.query == '{"query": "query"}'
            assert ei.operationName == null
            assert ei.context == [locale: request.locale]
            assert ei.variables.isEmpty()
            mockExecutionResult()
        }
    }

    void "test browser when disabled"() {
        when:
        controller.grailsGraphQLConfiguration = new GrailsGraphQLConfiguration(browser: false)
        controller.browser()

        then:
        status == 404
    }

    void "test browser"() {
        when:
        controller.grailsGraphQLConfiguration = new GrailsGraphQLConfiguration(browser: true)
        controller.browser()

        then:
        !response.text.empty
        response.contentType == "text/html;charset=utf-8"
    }

}
