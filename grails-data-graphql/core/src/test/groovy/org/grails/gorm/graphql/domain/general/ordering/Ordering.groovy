package org.grails.gorm.graphql.domain.general.ordering

import grails.gorm.annotation.Entity
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

@Entity
class Ordering {

    String order0
    String orderNeg
    String order2
    String order1a
    
    // Checks default property order 
    String orderNullc
    String orderNulld
    String order8
    
    static constraints = {
        order2 order: 4
        order1a order: 1
        order0 order: 0
        orderNeg order: -21
    }
    
    static graphql = GraphQLMapping.build {
        add("order1",String.class,{
            order(1) // same order as 'a'
        })
        property("order8",[order:8])
        property("order2",[order:2])
    }
}
