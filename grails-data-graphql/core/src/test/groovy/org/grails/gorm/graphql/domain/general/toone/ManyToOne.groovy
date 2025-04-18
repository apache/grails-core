package org.grails.gorm.graphql.domain.general.toone

import grails.gorm.annotation.Entity
import org.grails.gorm.graphql.domain.general.tomany.ToMany

@Entity
class ManyToOne {

    One one

    static belongsTo = [toMany: ToMany]

    static graphql = true
}
