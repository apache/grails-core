package org.grails.gorm.graphql.domain.general.toone

import grails.gorm.annotation.Entity
import org.grails.gorm.graphql.domain.general.tomany.Many

@Entity
class Embed {

    One one

    static hasMany = [many: Many]
}
