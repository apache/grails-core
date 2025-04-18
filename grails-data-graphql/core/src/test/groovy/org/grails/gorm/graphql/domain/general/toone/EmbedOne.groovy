package org.grails.gorm.graphql.domain.general.toone

import grails.gorm.annotation.Entity

@Entity
class EmbedOne {

    Embed embed
    EmbedNonEntity embedNonEntity

    static embedded = ['embed', 'embedNonEntity']

    static graphql = true
}
