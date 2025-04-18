package org.grails.gorm.graphql.domain.general.tomany

import grails.gorm.annotation.Entity
import org.grails.gorm.graphql.domain.general.toone.One

@Entity
class Many {
    One one
}
