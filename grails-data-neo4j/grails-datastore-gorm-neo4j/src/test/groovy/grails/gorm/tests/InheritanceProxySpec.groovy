package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.neo4j.Neo4jEntity

class InheritanceProxySpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Child, ChessClub, PrivateChessClub]
    }

    void "test a proxy is not created"() {
        given:
        new PrivateChessClub(name: "x").save()
        session.flush()
        session.clear()

        when:
        PrivateChessClub club = (PrivateChessClub) ChessClub.findByName("x")

        then:
        club.child == null
        club.baseChild == null
    }
}

@Entity
class ChessClub implements Neo4jEntity<ChessClub> {
    String name
    Child baseChild

    static constraints = {
        baseChild nullable: true
    }
}

@Entity
class PrivateChessClub extends ChessClub implements Neo4jEntity<PrivateChessClub> {
    Child child

    static constraints = {
        child nullable: true
    }
}