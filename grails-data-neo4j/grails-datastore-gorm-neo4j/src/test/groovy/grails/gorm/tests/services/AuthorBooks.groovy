package grails.gorm.tests.services

import grails.gorm.annotation.Entity
import grails.neo4j.Relationship

/**
 * Created by graemerocher on 09/03/2017.
 */
@Entity
class AuthorBooks implements Relationship<Author, Book>{
}
