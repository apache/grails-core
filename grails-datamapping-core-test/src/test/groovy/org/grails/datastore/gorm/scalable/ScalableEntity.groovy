package org.grails.datastore.gorm.scalable

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity
import grails.gorm.MultiTenant

@Entity
class ScalableEntity implements GormEntity<ScalableEntity>, MultiTenant<ScalableEntity> {
    String name
}
