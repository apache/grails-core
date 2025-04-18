package org.grails.gorm.graphql.domain.general.tomany

import grails.gorm.annotation.Entity
import org.grails.gorm.graphql.domain.general.toone.Enum
import org.grails.gorm.graphql.domain.general.toone.ManyToOne

@Entity
class ToMany {

    static hasMany = [many: Many,
                      manyToOne: ManyToOne,
                      manyToMany: ManyToMany,
                      enums: Enum,
                      strings: String]

    static graphql = true
}
