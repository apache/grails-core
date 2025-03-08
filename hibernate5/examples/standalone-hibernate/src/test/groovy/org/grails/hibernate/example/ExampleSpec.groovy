package org.grails.hibernate.example

import grails.gorm.annotation.Entity
import grails.gorm.services.Service
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 13/07/2016.
 */
class ExampleSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore
    @Shared PlatformTransactionManager transactionManager

    void setupSpec() {
       hibernateDatastore = new HibernateDatastore(Example)
       transactionManager = hibernateDatastore.getTransactionManager()
    }

    @Rollback
    void "test execute Hibernate standalone in a unit test"() {
        when:
        new Example(name: "Fred").save(flush:true)
        ExampleService exampleService = hibernateDatastore.getService(ExampleService)
        then:
        exampleService.count("Fred") == 1
        Example.count() == 1
    }
}

@Service(Example)
interface ExampleService {
    Number count(String name)
}
@Entity
class Example {
    String name
}
