package grails.gorm.tests

import grails.gorm.annotation.Entity
import groovy.transform.ToString

/**
 * Created by graemerocher on 21/10/16.
 */
@Entity
@ToString(includes = 'name')
class Team {
    Club club
    String name
    static hasMany = [players: Player]
}
