package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.neo4j.Neo4jEntity

class MarkDirtyFalseSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [ ClubB, TimestampedB ]
    }

    Map getConfiguration() {
        ['grails.gorm.markDirty': false]
    }

    void "verify correct behaviour of version incrementing"() {
        setup:
        def club = new ClubB(name: 'club')
        club.save(flush: true)
        session.clear()

        expect:
        club.version == 0

        when:
        club = ClubB.get(club.id)

        then:
        club.version == 0

        when:
        session.flush()

        then:
        club.version == 0

        when:
        club = ClubB.findByName('club')
        club.save()
        session.flush()
        session.clear()

        then: //non timestamped domains won't increment the version because no properties are dirty
        ClubB.findByName('club').version == 0
    }

    def "lastUpdated is updated"() {
        setup:
        new TimestampedB(name: "test").save()
        session.flush()
        session.clear()

        when:
        def ts = TimestampedB.findByName("test")

        then:
        ts.dateCreated != null
        ts.lastUpdated != null
        ts.dateCreated == ts.lastUpdated
        ts.version == 0

        when:
        ts.save()
        session.flush()
        session.clear()
        def newTs = TimestampedB.findByName("test")

        then: //nothing is persisted because nothing was changed
        newTs.lastUpdated == newTs.dateCreated
        newTs.version == 0
    }
}


@Entity
class ClubB implements Neo4jEntity<ClubB> {
    String name
}


@Entity
class TimestampedB implements Neo4jEntity<TimestampedB> {

    String name

    Date dateCreated
    Date lastUpdated
}
