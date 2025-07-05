package org.grails.orm.hibernate

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import groovy.transform.EqualsAndHashCode
import org.hibernate.ScrollableResults
import org.hibernate.transform.ToListResultTransformer
import spock.lang.Ignore


class HibernateGormStaticApiTest extends HibernateGormDatastoreSpec{

    def setupSpec() {
        manager.addAllDomainClasses([Account, Transaction, Whatever])
    }

    Account fredAccount
    Account barneyAccount
    Account jamesAccount
    Account maryAccount
    def accountCriteria

    def setup() {
        fredAccount = new Account(balance: 250, firstName: "Fred", lastName: "Flintstone", branch: "Bedrock")
        fredAccount.transactions << new Transaction(amount: 50, account: fredAccount)
        fredAccount.save(flush:true)
        barneyAccount = new Account(balance: 500, firstName: "Barney", lastName: "Rubble", branch: "Bedrock").save(flush: true)
        jamesAccount = new Account(balance: 750, firstName: "James", lastName: "Bond", branch: "London").save(flush: true)
        maryAccount = new Account(balance: 1000, firstName: "Mary", lastName: "Poppins", branch: "London").save(flush: true)
        accountCriteria = Account.createCriteria()
    }

    def "equalsCriteria"() {
        when:
        def results   =  accountCriteria {
                eq("branch","London")
                maxResults(1)
            }
        then:
            results.size() == 1
            results[0] == jamesAccount
    }

    def "between"() {
        when:
        def results   =  accountCriteria {
            between("balance", 500, 1000)
            order("balance", "asc")
        }
        then:
        results.size() == 3
        results[0] == barneyAccount
        results[1] == jamesAccount
        results[2] == maryAccount
    }

    def "or"() {
        when:
        def results   =  accountCriteria {
            or {
                like("firstName", "Fred%")
                like("firstName", "Barney%")

            }
            order("balance", "asc")
        }
        then:
        results.size() == 2
        results[0] == fredAccount
        results[1] == barneyAccount
    }

    @Ignore("JPA Criteria requires a single predicate behind a not")
    def "not"() {
        when:
        def results   =  accountCriteria {
            not {
                between("balance", 500, 1000)
                eq("branch", "London")

            }
            order("balance", "asc")
        }
        then:
        results.size() == 1
        results[0] == fredAccount
    }

    def transactions() {
        when:
        def results = accountCriteria.list {
            between("balance", 0, 500)
            transactions {
                between("amount", 50, 100)
            }
        }
        then:
            results.size() == 1
            results[0] == fredAccount
    }

    def projectionsList() {
        when:
        def results = accountCriteria.get {
            projections {
                countDistinct('branch')
                countDistinct('balance')
            }
        }
        then:
        results.size() == 2
        results[0] == 2
        results[1] == 4
    }

    def projections() {
        when:
        def results = accountCriteria.get {
            projections {
                countDistinct('branch')
            }
        }
        then:
        results == 2
    }

    def scroll() {
        when:
        ScrollableResults results = accountCriteria.scroll {
            order("balance", "asc")
        }
        then:
        results.scroll(1)
        fredAccount == results.get()
        results.last()
        maryAccount == results.get()
        results.previous()
        jamesAccount == results.get()
        results.first()
        fredAccount == results.get()
        results.next()
        barneyAccount == results.get()
    }

    def "transformer"() {
        when:
        List results = accountCriteria.get {
            resultTransformer(ToListResultTransformer.INSTANCE)
            projections {
                countDistinct('branch')
                countDistinct('balance')
            }
        }
        then:
            results[0] == 2
            results[1] == 4
    }

    def eagerFetching() {
        when:
        def results = accountCriteria.list {
            eq("branch", "Bedrock")
            join("transactions")
        }
        then:
            results.size() == 1
            results[0] == fredAccount
            results[0].transactions.size() == 1
            results[0].transactions[0].amount == 50
    }

    def listDistinct() {
        new Account(balance: 250, firstName: "Fred", lastName: "Flintstone", branch: "Bedrock").save(flush:true)
        when:
        def results = accountCriteria.listDistinct {
            property("branch")
            order("branch")
        }
        then:
        results.size() == 2
        results[0] == "Bedrock"
        results[1] == "London"
    }

//TODO: Chained Criteria
    def multipleCriteria() {
        given:
        def accountCriteria = {
            eq "branch", "Bedrock"
        }
        when:
        def results = Account.withCriteria {
            accountCriteria.delegate = accountCriteria
            accountCriteria()
            transactions {
                between("amount", 50, 100)
            }
        }
        then:
        results.size() == 1
        results[0] == fredAccount
        results[0].transactions.size() == 1
        results[0].transactions[0].amount == 50

    }




}

@Entity
@EqualsAndHashCode
class Whatever {
    Integer branch
    Integer balance
}

@Entity
class Transaction {
    BigDecimal amount
    static belongsTo = [account: Account]
}

@Entity
@EqualsAndHashCode
class Account {
    BigDecimal balance
    String branch
    String firstName
    String lastName
    Set<Transaction> transactions = new HashSet<>()
    static hasMany = [transactions:Transaction]

}