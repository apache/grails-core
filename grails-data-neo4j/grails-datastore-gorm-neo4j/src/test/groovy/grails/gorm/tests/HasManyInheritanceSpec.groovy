package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.neo4j.Neo4jEntity
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.Neo4jSession
import org.grails.datastore.gorm.neo4j.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.transactions.Transaction
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static grails.neo4j.mapping.MappingBuilder.node

class HasManyInheritanceSpec extends Specification {

    @Shared
    @AutoCleanup
    Neo4jDatastore neo4jDatastore

    @Shared
    Neo4jSession session

    @Shared
    Transaction transaction

    void setupSpec() {
        def tempDir = File.createTempDir()
        tempDir.deleteOnExit()
        Map config = [
                "grails.neo4j.options.encryptionLevel": "NONE",
                (Settings.SETTING_NEO4J_URL)          : "bolt://localhost:7687",
                (Settings.SETTING_NEO4J_TYPE)         : "embedded",
                (Settings.SETTING_NEO4J_LOCATION)     : tempDir
        ]
        this.neo4jDatastore = new Neo4jDatastore(config, getDomainClasses() as Class[])
    }

    void setup() {
        boolean existing = neo4jDatastore.hasCurrentSession()
        session = (Neo4jSession) (existing ? neo4jDatastore.currentSession : DatastoreUtils.bindSession(neo4jDatastore.connect()))
        transaction = session.beginTransaction()
    }

    void cleanup() {
        transaction.rollback()
        if (!neo4jDatastore.hasCurrentSession()) {
            TransactionSynchronizationManager.unbindResource(neo4jDatastore)
            DatastoreUtils.closeSessionOrRegisterDeferredClose(session, neo4jDatastore)
        }
    }

    List getDomainClasses() {
        return [ShoppingCentre, MallShoppingCentre, Television, SmartTelevision, BasicTelevision]
    }

    void "test canary"() { expect: true }

    void "Test hasMany with inheritance should return appropriate class while using custom id generator"() {

        given: "a shopping centre with a smart television"
        SmartTelevision sonySmartTelevision = new SmartTelevision(name: "Sony Android TV", model: "2018", companyName: "Sony")
        SmartTelevision lgSmartTelevision = new SmartTelevision(name: "LG Smart TV", model: "2015", companyName: "LG")
        BasicTelevision basicTelevision = new BasicTelevision(name: "Basic TV", model: "1991", companyName: "BPL")

        ShoppingCentre shoppingCentre = new ShoppingCentre(name: "Reliance World")
        shoppingCentre.addToTelevisions(sonySmartTelevision)
        shoppingCentre.addToTelevisions(lgSmartTelevision)
        shoppingCentre.addToTelevisions(basicTelevision)
        shoppingCentre.save(flush: true)

        when: "get the television from the database"
        Television television = Television.findByName("Sony Android TV")

        then: "the television class is correct"
        television.class == SmartTelevision
        ShoppingCentre.findByName("Reliance World").televisions.size() == 3
    }

    void "Test inheritance hasMany with inheritance should return appropriate class while using custom id generator"() {

        given: "a shopping centre with a smart television"
        SmartTelevision sonySmartTelevision = new SmartTelevision(name: "Sony Android TV", model: "2018", companyName: "Sony")
        SmartTelevision lgSmartTelevision = new SmartTelevision(name: "LG Smart TV", model: "2015", companyName: "LG")
        BasicTelevision basicTelevision = new BasicTelevision(name: "Basic TV", model: "1991", companyName: "BPL")

        MallShoppingCentre shoppingCentre = new MallShoppingCentre(name: "Reliance World")
        shoppingCentre.addToTelevisions(sonySmartTelevision)
        shoppingCentre.addToTelevisions(lgSmartTelevision)
        shoppingCentre.addToTelevisions(basicTelevision)
        shoppingCentre.save(flush: true)

        when: "get the television from the database"
        Television television = Television.findByName("Sony Android TV")

        then: "the television class is correct"
        television.class == SmartTelevision
        ShoppingCentre.findByName("Reliance World") != null
    }

    void "Test saving a television directly"() {
        given:
        new SmartTelevision(name: "Sony Android TV", model: "2018", companyName: "Sony").save(flush: true)

        when:
        Television television = Television.findByName("Sony Android TV")

        then:
        television != null
        television.class == SmartTelevision
    }

}

@Entity
class ShoppingCentre implements Neo4jEntity<ShoppingCentre> {
    String name

    static hasMany = [televisions: Television]

    static mapping = node {
        id {
            generator "snowflake"
        }
    }
}

@Entity
class MallShoppingCentre extends ShoppingCentre implements Neo4jEntity<MallShoppingCentre> {

}

@Entity
@CompileStatic
class Television implements Neo4jEntity<Television> {
    String name
    String model
    String companyName

    static mapping = node {
        id {
            generator "snowflake"
        }
    }
}

@Entity
@CompileStatic
class BasicTelevision extends Television implements Neo4jEntity<BasicTelevision> {

}

@Entity
@CompileStatic
class SmartTelevision extends Television implements Neo4jEntity<SmartTelevision> {

}

