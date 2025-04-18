package grails.test.app

import grails.databinding.BindUsing
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

class Role {

    String authority

    static constraints = {
    }

    static graphql = GraphQLMapping.build {
        property('authority') {
            name 'name'
        }
    }
}
