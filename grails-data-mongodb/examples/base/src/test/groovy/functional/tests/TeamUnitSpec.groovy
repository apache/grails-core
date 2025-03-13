package functional.tests

import grails.test.mongodb.MongoSpec
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared

class TeamUnitSpec extends MongoSpec implements EmbeddedMongoClient {

    @Shared
    final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:latest"))

    void "get() doesn't throw NPE"() {
        when:
        Team team = Team.get("123")

        then:
        !team
    }
}
