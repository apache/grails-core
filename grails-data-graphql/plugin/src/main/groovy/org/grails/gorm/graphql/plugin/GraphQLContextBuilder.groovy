package org.grails.gorm.graphql.plugin

import org.grails.web.servlet.mvc.GrailsWebRequest

interface GraphQLContextBuilder {

    Object buildContext(GrailsWebRequest request)
}