package org.grails.gorm.graphql.domain.general.toone

import grails.gorm.annotation.Entity

@Entity
class CircularOne {

    CircularOne one
}
