package gorm.graphql

import org.grails.gorm.graphql.plugin.GraphQLRequest
import spock.lang.Specification
import spock.lang.Subject

class GraphQLRequestConstraintsSpec extends Specification {

    @Subject
    GraphQLRequest graphQLRequest = new GraphQLRequest()

    void "GraphQLExecutionRequest.query cannot be null"() {
        when:
        graphQLRequest.query = null

        then:
        !graphQLRequest.validate(['query'])
    }

    void "GraphQLExecutionRequest.operationName can be null"() {
        when:
        graphQLRequest.operationName = null

        then:
        graphQLRequest.validate(['operationName'])
    }

    void "GraphQLExecutionRequest.variables cannot be null"() {
        when:
        graphQLRequest.variables = null

        then:
        !graphQLRequest.validate(['variables'])
    }

    void "GraphQLExecutionRequest.variables can be an empty Map"() {
        when:
        graphQLRequest.variables = [:]

        then:
        graphQLRequest.validate(['variables'])
    }
}
