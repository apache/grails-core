package grails.gorm.services.multitenancy.database

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class Book implements MultiTenant<Book> {
    String title
}
