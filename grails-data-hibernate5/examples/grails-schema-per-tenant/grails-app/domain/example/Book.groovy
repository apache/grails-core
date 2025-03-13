package example

import grails.gorm.MultiTenant
import org.grails.datastore.gorm.GormEntity

class Book implements GormEntity<Book>, MultiTenant<Book> {

    String title
    
    static constraints = {
        title blank:false
    }
}
