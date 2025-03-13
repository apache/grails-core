package functional.tests

import grails.gorm.MultiTenant
import grails.mongodb.MongoEntity

class Book implements MongoEntity<Book>, MultiTenant<Book> {

    String title

    static constraints = {
        title blank:false
    }
}
