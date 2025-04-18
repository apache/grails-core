package org.grails.gorm.graphql.domain.general.debug

import grails.gorm.annotation.Entity

@Entity
class DebugFoo {

    DebugBar bar

    static hasMany = [items: DebugFooItem]

}
