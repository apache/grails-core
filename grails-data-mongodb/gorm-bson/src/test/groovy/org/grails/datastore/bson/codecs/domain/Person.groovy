package org.grails.datastore.bson.codecs.domain

import grails.gorm.annotation.Entity

/**
 * Created by graemerocher on 14/06/16.
 */
@Entity
class Person {

    String name
    Integer age
    Date dateOfBirth
    String pattern
}
