package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import spock.lang.Issue

/**
 * @author Graeme Rocher
 */
class NestedCriteriaWithNamedQuerySpec extends GormDatastoreSpec{

    @Issue('GRAILS-9497')
    void "Test that nested criteria work with named queries"() {
        given:"A domain model with 3 levels of association"
            Seller seller = new Seller()
            Ticket ticket = new Ticket()
            seller.addToTickets(ticket)
            seller.save(flush:true)
            Purchase purchase = new Purchase()
            ticket.addToPurchases(purchase)
            purchase.save(flush: true)
            ticket.save(flush: true)
            session.clear()
        when:"The data is queried"
            def results = Purchase.myNamedQuery(seller).list()

        then:"Results are returned"
            results != null
    }

    @Override
    List getDomainClasses() {
        [Seller, Ticket, Purchase]
    }
}
@Entity
class Seller {
    Long id
    Set tickets
    static hasMany = [tickets:Ticket]
}
@Entity
class Ticket {
    Long id
    Seller seller
    Set purchases
    static belongsTo = [seller:Seller]
    static hasMany = [purchases:Purchase]
}
@Entity
class Purchase {
    Long id
    Ticket ticket
    static belongsTo = [ticket:Ticket]
    static namedQueries = {
        myNamedQuery { Seller seller ->
            ticket{
                eq('seller',seller)
            }
        }
    }
}