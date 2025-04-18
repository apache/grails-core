package org.grails.gorm.graphql.domain.general.toone

import grails.gorm.annotation.Entity

@Entity
class BelongsToHasOne {

    static belongsTo = [one: HasOne]

    static graphql = true
}
