package grails.test.app.manyToMany

import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

class Classes {
    String title
    String description

    static hasMany = [students: Student]

    static graphQL = GraphQLMapping.build {
        operations.all.enabled false
    }
}
