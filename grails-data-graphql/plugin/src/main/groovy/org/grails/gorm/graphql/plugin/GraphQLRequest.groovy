package org.grails.gorm.graphql.plugin

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import graphql.ErrorType
import graphql.GraphQL
import graphql.GraphQLError
import org.springframework.context.MessageSource
import org.springframework.validation.ObjectError

@GrailsCompileStatic
class GraphQLRequest implements Validateable {
    String query
    String operationName
    Map<String, Object> variables = [:]

    static constraints = {
        query nullable: false
        operationName nullable: true
        variables nullable: false
    }
}
