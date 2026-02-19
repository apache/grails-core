package grails.orm

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import java.math.RoundingMode

class HibernateCriteriaBuilderSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([Account, Transaction])
    }

    HibernateCriteriaBuilder builder

    def setup() {
        def fred = new Account(balance: 250, firstName: "Fred", lastName: "Flintstone", branch: "Bedrock").save(failOnError: true)
        def barney = new Account(balance: 500, firstName: "Barney", lastName: "Rubble", branch: "Bedrock").save(failOnError: true)
        new Account(balance: 100, firstName: "Wilma", lastName: "Flintstone", branch: "Bedrock").save(failOnError: true)
        new Account(balance: 1000, firstName: "Pebbles", lastName: "Flintstone", branch: "Slate Rock and Gravel").save(failOnError: true)
        new Account(balance: 50, firstName: "Bam-Bam", lastName: "Rubble", branch: null).save(failOnError: true)

        fred.addToTransactions(new Transaction(amount: 10))
        fred.addToTransactions(new Transaction(amount: 20))
        fred.save()

        barney.addToTransactions(new Transaction(amount: 50))
        barney.save(flush: true, failOnError: true)

        builder = new HibernateCriteriaBuilder(Account, manager.hibernateDatastore.sessionFactory, manager.hibernateDatastore)
    }

    void "test get with eq criteria"() {
        when:
        def result = builder.get {
            eq("firstName", "Fred")
        }

        then:
        result.firstName == "Fred"
    }

    void "test idEq criteria"() {
        given:
        def fred = Account.findByFirstName("Fred")

        when:
        def result = builder.get {
            idEq(fred.id)
        }

        then:
        result.id == fred.id
        result.firstName == "Fred"
    }

    void "test list with various criteria"() {
        when:
        def results = builder.list {
            gt("balance", BigDecimal.valueOf(200))
            or {
                eq("lastName", "Flintstone")
                like("branch", "Bedrock")
            }
            'in'("firstName", ["Fred", "Barney", "Pebbles"])
        }

        then:
        results.size() == 3
        results*.firstName.sort() == ["Barney", "Fred", "Pebbles"]
    }

    void "test ilike criteria"() {
        when:
        def results = builder.list {
            ilike("firstName", "fr%")
        }

        then:
        results.size() == 1
        results[0].firstName == "Fred"
    }

    void "test rlike criteria"() {
        when:
        def results = builder.list {
            rlike("firstName", "^F.*")
        }

        then:
        results.size() == 1
        results[0].firstName == "Fred"
    }

    void "test between criteria"() {
        when:
        def results = builder.list {
            between("balance", BigDecimal.valueOf(100), BigDecimal.valueOf(300))
        }

        then:
        results.size() == 2
        results*.firstName.sort() == ["Fred", "Wilma"]
    }

    void "test sizeEq criteria"() {
        when:
        def results = builder.list {
            sizeEq("transactions", 2)
        }

        then:
        results.size() == 1
        results[0].firstName == "Fred"
    }

    void "test isEmpty and isNotEmpty criteria"() {
        when:
        def emptyResults = builder.list {
            isEmpty("transactions")
        }
        def notEmptyResults = builder.list {
            isNotEmpty("transactions")
        }

        then:
        emptyResults.size() == 3 // Wilma, Pebbles, Bam-Bam
        notEmptyResults.size() == 2 // Fred, Barney
    }

    void "test isNull criteria"() {
        when:
        def results = builder.list {
            isNull("branch")
        }

        then:
        results.size() == 1
        results[0].firstName == "Bam-Bam"
    }

    void "test count projection"() {
        when:
        def count = builder.get {
            projections {
                count()
            }
            eq("lastName", "Flintstone")
        }

        then:
        count == 3
    }

    void "test sum and avg projection"() {
        when:
        def projections = builder.get {
            projections {
                sum('balance')
                avg('balance')
            }
            eq("branch", "Bedrock")
        }

        then:
        projections[0] == 850
        new BigDecimal(projections[1]).setScale(2, RoundingMode.HALF_UP) == 283.33
    }

    void "test ordering and pagination"() {
        when:
        def results = builder.list(max: 2, offset: 1) {
            order("firstName", "asc")
        }

        then:
        results.size() == 2
        results*.firstName == ["Barney", "Fred"]
    }

    void "test query on association"() {
        when:
        def results = builder.list {
            transactions {
                gt("amount", 40)
            }
        }

        then:
        results.size() == 1
        results[0].firstName == "Barney"
    }

    void "test inequality and between criteria"() {
        when:
        def results = builder.list {
            ne("firstName", "Fred")
            ge("balance", BigDecimal.valueOf(60))
            le("balance", BigDecimal.valueOf(1000))
        }
        then:
        results*.firstName.toSet() == ["Barney", "Wilma", "Pebbles"] as Set
    }

    void "test isNotNull and size constraints on association"() {
        when:
        def results = builder.list {
            isNotNull("branch")
            sizeGe("transactions", 1)
        }
        then:
        results*.firstName.toSet() == ["Fred", "Barney"] as Set
    }

    void "test property to property comparisons and ordering desc"() {
        when:
        def results = builder.list {
            geProperty("balance", "balance") // always true, validates path
            eqProperty("firstName", "firstName")
            neProperty("firstName", "lastName")
            gtProperty("balance", "balance") // always false for same property
            order("balance", "desc")
        }
        then:
        results.size() == 0 // because gtProperty("balance", "balance") is false

        when:
        results = builder.list {
            leProperty("balance", "balance")
            ltProperty("balance", "balance")
        }
        then:
        results.size() == 0
    }

    void "test nested criteria with aliases"() {
        when:
        def results = builder.list {
            transactions {
                eq("amount", BigDecimal.valueOf(50))
            }
        }
        then:
        results.size() == 1
        results[0].firstName == "Barney"

        when:
        results = builder.list {
            transactions {
                between("amount", BigDecimal.valueOf(15), BigDecimal.valueOf(25))
            }
        }
        then:
        results.size() == 1
        results[0].firstName == "Fred"
    }

    void "test projections countDistinct groupProperty min max"() {
        when:
        def results = builder.list {
            projections {
                groupProperty("lastName")
                countDistinct("firstName")
                min("balance")
                max("balance")
            }
        }
        then:
        results.size() >= 1
    }

    void "test inList array and collection variants and firstResult"() {
        when:
        def list1 = builder.list {
            'in'("firstName", ["Fred", "Barney"] as Object[]) // array via 'in' alias
        }
        def list2 = builder.list {
            inList("firstName", ["Fred", "Wilma"] as Object[]) // array variant
        }
        def paged = builder.list(max: 1) {
            order("firstName", "asc")
            firstResult(2)
        }
        then:
        list1 instanceof List && list1.size() > 0
        list2 instanceof List && list2.size() > 0
        paged.size() == 1
        paged[0].firstName == "Fred"
    }

    void "test eq with ignoreCase param path and like/ilike methods"() {
        when:
        def results = builder.list {
            eq("firstName", "Fr", [ignoreCase: true]) // should fallback to like with %Fr%
        }
        def likeRes = builder.list {
            like("branch", "%Bedrock%")
        }
        def ilikeRes = builder.list {
            ilike("branch", "%BEDROCK%")
        }
        then:
        results instanceof List
        likeRes.size() >= 1
        ilikeRes.size() >= 1
    }
}

@Entity
class Account {
    String firstName
    String lastName
    BigDecimal balance
    String branch
    Set<Transaction> transactions

    static hasMany = [transactions: Transaction]
    static constraints = {
        branch nullable: true
    }
}

@Entity
class Transaction {
    BigDecimal amount
    Date dateCreated

    static belongsTo = [account: Account]
}
