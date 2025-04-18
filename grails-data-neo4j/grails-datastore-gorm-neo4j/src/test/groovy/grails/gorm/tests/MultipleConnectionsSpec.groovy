package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.neo4j.Neo4jEntity
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.config.Settings
import org.neo4j.driver.exceptions.ClientException
import org.neo4j.driver.exceptions.ServiceUnavailableException
import org.springframework.util.SocketUtils
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 05/07/2016.
 */
@Ignore
// as if Neo4j bolt driver 1.4 it is no longer possible to create the driver
// when the Neo4j server is down so this test is no longer possible
class MultipleConnectionsSpec extends Specification {

    @Shared int port1 = SocketUtils.findAvailableTcpPort(7700)
    @Shared int port2 = SocketUtils.findAvailableTcpPort(7700)
    @Shared Map config = [
            (Settings.SETTING_NEO4J_URL) : "bolt://localhost:7687",
            (Settings.SETTING_NEO4J_BUILD_INDEX) :false,
            (Settings.SETTING_CONNECTIONS): [
                    test1: [
                            url: "bolt://localhost:${port1}"
                    ],
                    test2: [
                            url: "bolt://localhost:${port2}"
                    ]
            ]
    ]
    @Shared @AutoCleanup Neo4jDatastore datastore

    void setupSpec() {
        datastore = new Neo4jDatastore(config, getDomainClasses() as Class[])
    }


    void "Test query multiple data sources"() {

        when:"An entity is saved"
        new CompanyA(name:"One").save(flush:true)

        then:
        def error = thrown(ServiceUnavailableException)
        error.message.contains("Unable to connect to localhost:$port1")

        when:"An entity is saved"
        CompanyA.withConnection("test2") {
            save(new CompanyA(name:"One"), [flush: true])
        }

        then:
        def error2 = thrown(ServiceUnavailableException)
        error2.message.contains("Unable to connect to localhost:$port2")
    }

    List getDomainClasses() {
        [CompanyA]
    }
}

/**
 * Created by graemerocher on 30/06/16.
 */
@Entity
class CompanyA implements Neo4jEntity<CompanyA> {
    Long id
    String name
    static mapping = {
        connections "test1", "test2"
    }
}


