package grails.test.app

import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

class Comment {

    String text

    static belongsTo = [parentComment: Comment]

    static hasMany = [replies: Comment] //circular toMany

    static constraints = {
        parentComment nullable: true
    }

    static graphql = GraphQLMapping.build {
        property('replies', input: false)
    }
}
