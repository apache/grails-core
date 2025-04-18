package org.grails.gorm.graphql.domain.general.custom.operation

import grails.gorm.annotation.Entity
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

@Entity
class PaginatedTwo {

    static graphql = GraphQLMapping.build {
        operations.list.paginate true
    }
}
