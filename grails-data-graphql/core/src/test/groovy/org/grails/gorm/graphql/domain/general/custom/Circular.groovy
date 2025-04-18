package org.grails.gorm.graphql.domain.general.custom

import grails.gorm.annotation.Entity

@Entity
class Circular {

    Circular otherCircular

    static hasMany = [circulars: Circular]

    static mappedBy = ['circulars': 'parentCircular']

    static belongsTo = [parentCircular: Circular]

    static graphql = true
}
