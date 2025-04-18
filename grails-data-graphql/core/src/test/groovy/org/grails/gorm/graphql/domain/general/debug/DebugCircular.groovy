package org.grails.gorm.graphql.domain.general.debug

import grails.gorm.annotation.Entity

@Entity
class DebugCircular {

    DebugCircular otherCircular

    static hasMany = [circulars: DebugCircular]

    static belongsTo = [parentCircular: DebugCircular]

}
