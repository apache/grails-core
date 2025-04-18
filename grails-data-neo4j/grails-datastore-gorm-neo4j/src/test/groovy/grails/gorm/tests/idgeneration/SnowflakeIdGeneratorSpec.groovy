package grails.gorm.tests.idgeneration

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import grails.neo4j.mapping.MappingBuilder
import org.grails.datastore.gorm.neo4j.IdGenerator
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.identity.SnowflakeIdGenerator
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by stefan on 10.04.14.
 */
class SnowflakeIdGeneratorSpec extends Specification {

    @Shared @AutoCleanup Neo4jDatastore datastore = new Neo4jDatastore(Snowman)

    def "snowflake returns different values"() {

        setup:
            def numberOfInvocations = 100000
            def ids = [] as Set

            IdGenerator generator = new SnowflakeIdGenerator()

        when:
            for (def i=0; i<numberOfInvocations; i++) {
                def id = generator.nextId()
                assert !ids.contains(id)
                ids << id
            }

        then:
            ids.size() == numberOfInvocations

    }

    @Rollback
    void "test snowflake generator to save and retrieve an object"() {
        when:
        Snowman snowman=new Snowman(name: "Bob").save(flush:true)
        datastore.currentSession.clear()

        then:
        snowman.id
        Snowman.get(snowman.id)

    }

}

@Entity
class Snowman {

    String name

    static mapping = MappingBuilder.node {
        id(generator:"snowflake")
    }
}
