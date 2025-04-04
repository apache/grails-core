package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class ManyToManySpec extends GormDatastoreSpec {

    void "Test save and load many-to-many association"() {
        given:"A many-to-many association"
            Account account = new Account().save()
            assert account

            account.addToInvoices(new Invoice())
            account.save(flush:true)
            session.clear()

        when:"The association is loaded"
            account = Account.get(account.id)

        then:"The results are correct"
            account != null
            account.invoices.size() == 1
            account.invoices.iterator().next().accounts.size() == 1
    }

    @Override
    List getDomainClasses() {
        [Account, Invoice]
    }
}

@Entity
class Account {
    Long id
    Set invoices
    static hasMany = [invoices: Invoice]
}

@Entity
class Invoice {
    Long id
    static belongsTo = [Account]
    Set accounts
    static hasMany = [accounts: Account]
}
