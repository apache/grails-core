package grails.test.app

import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

class SoftDelete {

    String name
    boolean active = true

    static constraints = {
    }

    static graphql = GraphQLMapping.build {
        exclude('active')
    }
}
