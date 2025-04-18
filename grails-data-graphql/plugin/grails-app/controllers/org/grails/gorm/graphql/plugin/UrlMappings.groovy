package org.grails.gorm.graphql.plugin

class UrlMappings {

    static mappings = {
        "/graphql/$action?"(controller: 'graphql')
    }
}
