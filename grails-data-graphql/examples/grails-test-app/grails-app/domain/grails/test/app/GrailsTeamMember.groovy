package grails.test.app

import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

class GrailsTeamMember {

    String name

    static constraints = {
    }

    static graphql = GraphQLMapping.build {
        operations.list.paginate true
    }
}
