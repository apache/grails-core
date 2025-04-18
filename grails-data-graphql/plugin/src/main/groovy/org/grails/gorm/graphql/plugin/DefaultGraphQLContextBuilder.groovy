package org.grails.gorm.graphql.plugin

import groovy.transform.CompileStatic
import org.grails.web.servlet.mvc.GrailsWebRequest

@CompileStatic
class DefaultGraphQLContextBuilder implements GraphQLContextBuilder {

    @Override
    Map buildContext(GrailsWebRequest request) {
        [locale: request.locale]
    }
}
