package example

import grails.gorm.annotation.Entity
import grails.mongodb.MongoEntity

/**
 * Implement MongoEntity is optional
 */
@Entity
class Book implements MongoEntity<Book> {
    String title
}
