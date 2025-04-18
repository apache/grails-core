package org.grails.gorm.graphql.domain.general.debug

import grails.gorm.annotation.Entity

@Entity
class DebugFooItem {

    static belongsTo = [foo: DebugFoo]
}
