package example

import grails.neo4j.Node
import grails.gorm.annotation.Entity

@Entity
class Book implements Node<Book> {
    String title
}
