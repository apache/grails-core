package org.grails.gorm.graphql.domain.hibernate.description

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.mapping.MappingBuilder

@Entity
class MappingComment {

    static mapping = MappingBuilder.orm {
        comment('MappingComment class')
    }

}