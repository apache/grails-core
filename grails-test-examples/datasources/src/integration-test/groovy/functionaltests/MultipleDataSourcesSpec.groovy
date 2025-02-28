package functionaltests

import datasources.Application
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import spock.lang.*
import ds1.Book
import ds2.Book as SecondBook

@Integration(applicationClass = Application)
@Rollback
class MultipleDataSourcesSpec extends Specification {


    void "Test multiple data source persistence"() {
        when:
            new Book(title:"One").save(flush:true)
            new Book(title:"Two").save(flush:true)
            SecondBook.withTransaction {
                new SecondBook(title:"Three").save(flush:true)    
            }
            
        then:
            Book.count() == 2
            SecondBook.withTransaction { SecondBook.count() } == 1
            SecondBook.secondary.withTransaction { SecondBook.secondary.count() } == 1
    }
}
