package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.gorm.dirty.checking.DirtyCheck
import grails.neo4j.Neo4jEntity
import grails.neo4j.mapping.MappingBuilder
import groovy.beans.Bindable
import groovyx.gpars.GParsPool

import org.grails.datastore.gorm.neo4j.util.IteratorUtil
import org.neo4j.driver.exceptions.ClientException
import org.neo4j.graphdb.Label
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Issue

import java.util.concurrent.TimeUnit

/**
 * some more unrelated testcases, in more belong together logically, consider refactoring them into a seperate spec
 */
class MiscSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [ Club, Team, Tournament, User, Role, Pet, TestEntity, Plant, PlantCategory, Task, TestEntity, CommonTypes, Timestamped ]
    }

    def "test object identity, see if cache is being used"() {
        setup:
            new User(username: 'user1').save()
            new User(username: 'user2').save(flush:true)
            session.clear()

        when:  "retrieve the same object twice"
            def user = User.findByUsername('user1')
            def user2 = User.findByUsername('user1')

        then: "see if the same instance is returned"
            user.is(user2)
            2 == User.count()
            user in User.list()
    }

    def "test object identity in relationships"() {
        setup:
            def user = new User(username: 'user1')
            user.addToRoles new Role(role: 'role1')
            user.addToRoles new Role(role: 'role2')
            user.save(flush:true)
            session.clear()

        when:
            user = User.findByUsername('user1')
            def role = Role.findByRole('role1')

        then:
            !user.roles.isInitialized()
    }

    def "test unique constraint"() {
        setup:
            setupValidator(Role)

        when:
        Role.withNewTransaction {
            def role1 = new Role(role: 'role')
            role1.save()
        }

        then:
        noExceptionThrown()

        when:
        Role.withNewTransaction {
            new Role(role: 'role').save()
        }

        then:
        thrown(ClientException)

        and:
        Role.withNewTransaction(readOnly: true) {
            Role.findAllByRole('role').size()
        } == 1
    }

    void "Test modification of a persistent instance with explicit save"() {
        given:
            def t = new TestEntity(name:"Bob")
            t.save(flush:true)
            session.clear()
        when:
            t = TestEntity.get(t.id)
            t.name = "Sam"
            t.save()  // explicit save necessary
            session.flush()
            session.clear()
        then:
            TestEntity.findByName("Bob") == null
            TestEntity.findByName("Sam") != null
    }

    void "test if addtoXXXX gets persisted correctly"() {
        given:
        new PlantCategory(name: 'category').save(flush:true)
        session.clear()

        when:
        def category = PlantCategory.findByName('category')
        session.clear()

        category = PlantCategory.get(category.id)
        def plant1 = new Plant(name:'plant1')
        category.addToPlants(plant1).save()
        category.save(flush:true)
        session.clear()
        category = PlantCategory.get(category.id)

        then:
        category
        category.name =='category'
        category.plants.size() == 1
        category.plants*.name == ['plant1']

    }

    // this test belongs semantically to grails.gorm.tests.CircularOneToManySpec but will fail in some existing
    // implementations, that's why it's located here
    void "test circular one-to-many using addToXX"() {
        setup:
            def user1 = new User(username: 'user1')
            def user2 = new User(username: 'user2')
            user1.addToFriends( user2)
            user2.addToFriends( user1)
            user1.save()
            user2.save()
            session.flush()
            session.clear()

        when:
            user1 = User.get(user1.id)
            user2 = User.get(user2.id)

        then:
            new ArrayList(user1.friends) == [ user2 ]
            new ArrayList(user2.friends) == [ user1 ]
    }

    void "test multiple relations with the same name"() {
        setup:
        def team = new Team(name: 'team')
        def club = new Club(name: 'club')
        club.addToTeams(team).save()
        def tournament = new Tournament(name:'tournament')
        tournament.addToTeams(team).save(flush:true)
        session.clear()

        when:
        tournament = Tournament.get(tournament.id)

        then:
        tournament.teams.size() == 1
        tournament.teams*.name == ['team']
        tournament.teams[0].club.name == 'club'
    }

//    @Ignore
    // This test is failing due to a bug in the driver
    void "test concurrent native accesses"() {
        when:
        GParsPool.withPool(concurrency) {
            (1..count).eachParallel { counter ->
                def session = boltDriver.session()
                def tx = session.beginTransaction()

                tx.run("CREATE (n1:Team \$props)", [props:[name:"Team $count".toString()]])
                tx.commit()
                session.close()
            }
        }

        then:
        Team.count() == count

        where:
        count | concurrency
        100   | 4
        100   | 16
        100   | 100
    }

//    @Ignore
    // This test is failing due to a bug in the driver
    void "test concurrent accesses"() {
        when:
        GParsPool.withPool(concurrency) {
            (1..count).eachParallel { counter ->
                Team.withTransaction {
                    new Team(name: "Team $counter").save(flush:true, failOnError: true,validate:false)
                }
            }
        }

        then:
        Team.count() == count

        where:
        count | concurrency
        100   | 4
        100   | 16
        100   | 100
    }

    void "test indexing"() {
        setup: "by default test suite runs without indexes, so we need to build them"

        Thread.start {
            def tx = serverControls.graph().beginTx()
            try {
                session.datastore.setupIndexing()
                tx.success()
            } finally {
                tx.close()
            }
            tx = serverControls.graph().beginTx()
            try {
                serverControls.graph().schema().awaitIndexesOnline(10, TimeUnit.SECONDS)
                tx.success()
            } finally {
                tx.close()
            }
        }.join()


        def task1 = new Task(name: 'task1')
        task1.save()
//        new Task(name: 'task2').save(flush: true)
        session.clear()

        when:

        def indexedProperties
        def tx = serverControls.graph().beginTx()
        try {
            indexedProperties = serverControls.graph().schema().getIndexes(Label.label("Task")).collect {
                IteratorUtil.single(it.propertyKeys)
            }
            tx.success()
        } finally {
            tx.close()
        }

        then:
        indexedProperties.containsAll(["name"])

    }

    void "verify correct behaviour of version incrementing"() {
        setup:
        def club = new Club(name: 'club')
        club.save(flush: true)
        session.clear()

        expect:
        club.version == 0

        when:
        club = Club.get(club.id)

        then:
        club.version == 0

        when:
        session.flush()

        then:
        club.version == 0

        when:
        club = Club.findByName('club')
        club.save()
        session.flush()
        session.clear()

        then: //non timestamped domains won't increment the version because no properties are dirty
        Club.findByName('club').version == 0
    }

    @Ignore("temporaritly removed since this seems to had side effect")
    def "verify concurrent adding does not cause LockingExceptions"() {
        when:
        GParsPool.withPool(numberOfThreads) {
            (1..numberOfTeams).eachParallel { counter ->
                Team.withNewTransaction {

                    new Team(name: "Team $counter").save(failOnError: true)
                }
            }
        }

        then: "correct number of teams has been created"
        Team.count() == numberOfTeams

        where:
        numberOfThreads | numberOfTeams
        1               | 20
        2               | 20
        4               | 20
        8               | 20

    }

    @Ignore
    def "do peformance tests"() {
        when:
        def start = System.currentTimeMillis()
        Team.withNewTransaction {
            for (i in 1..10000) {
                new Team(name: "Team $i").save()
            }
        }
        def delta = System.currentTimeMillis() - start
        println "create 10000 in $delta msec"

        then:
        delta > 0

        when:
        start = System.currentTimeMillis()
        def count = Team.count()
        delta = System.currentTimeMillis() - start
        println "count is $count, delta $delta"

        then:
        delta > 0

    }

    @Issue("https://github.com/SpringSource/grails-data-mapping/issues/52")
    def "check that date properties are stored natively as longs"() {
        when:
            def pet = new Pet(birthDate: new Date(), name: 'Cosima').save(flush: true)
        then:
            IteratorUtil.single(session.transaction.nativeTransaction.run("MATCH (p:Pet {name:\$1}) RETURN p.birthDate as birthDate", ["1":'Cosima'])).birthDate.asNumber() instanceof Long
    }

    @Issue("https://github.com/SpringSource/grails-data-mapping/issues/52")
    @IgnoreIf({System.getenv('CI')}) // fails randomly on Travis
    def "verify backward compatibility, check that date properties stored as string can be read"() {
        setup: "create a instance with a date property and manually assign a string to it"
            def date = new Date()
            def pet = new Pet(birthDate: date, name:'Cosima').save(flush: true)

        when: "write birthDate as a String"
            session.transaction.nativeTransaction.run("MATCH (p:Pet {name:\$1}) SET p.birthDate=\$2",
                ['1':'Cosima', '2':date.time.toString()])
            pet = Pet.get(pet.id)
        then: "the string stored date gets parsed correctly"
            pet.birthDate == date
    }

    @Ignore("this test no longer makes sense as we're storing base64 encoded strings for byte[] props")
    def "byte arrays work as domain class properties"() {
        when:
        def team = new Team(name: 'name', binaryData: 'abc'.bytes)
        team.save(flush: true)
        def value = IteratorUtil.single(session.transaction.nativeTransaction.run("MATCH (p:Team {name:\$1}) RETURN p.binaryData as binaryData",
            ["1":'name'])).binaryData

        then:
        value.class == byte[].class
        value == 'abc'.bytes
    }

    @Ignore("we care about serialization later on")
    def "serialization should work with proxies"() {
        setup:
        Team team = new Team(name: "team",
                club: new Club(name: 'club')
        ).save(flush: true)
        session.clear()
        team = Team.get(team.id)

        def bos = new ByteArrayOutputStream()

        bos.withObjectOutputStream {
            it.writeObject(team)
        }

        when:
        Team deserializedTeam = new ByteArrayInputStream(bos.toByteArray()).withObjectInputStream {
            it.readObject()
        }

        then:
        deserializedTeam instanceof Team
//        team.club.metaClass.getMetaMethod("isProxy", null) != null
        team.name == deserializedTeam.name
        team.club.name == deserializedTeam.club.name
    }

    @Ignore("we care about serialization later on")
    def "operations on deserialized instance with hasMany works"() {
        setup:
        Tournament tournament = new Tournament(name: "tournament",
                teams: [new Team(name: 'team1'), new Team(name: 'team2')]
        ).save(flush: true)
        session.clear()
        tournament = Tournament.get(tournament.id)

        def bos = new ByteArrayOutputStream()
        bos.withObjectOutputStream {
            it.writeObject(tournament)
        }
        Tournament deserializedTournament = new ByteArrayInputStream(bos.toByteArray()).withObjectInputStream {
            it.readObject()
        }

        when:
        def firstTeam = deserializedTournament.teams[0]
        deserializedTournament.teams.remove(firstTeam)
        session.flush()

        tournament = Tournament.get(tournament.id)

        then:
        deserializedTournament.teams.size()==1
        tournament.teams.size()==1

    }

    def "null values on complex properties work on save"() {
        when:
        def c = new CommonTypes()

        then:
        c.save(flush: true, validate:false)
    }

    def "changing one2many is consistent"() {
        setup:
        def argentina = new Team(name: 'Argentina').save(validate:false)
        def germany = new Team(name: 'Germany').save(validate:false)
        session.flush()
        session.clear()

        when: "by error we put Manual Neuer to Argentina"
        def manuel = new Player(name: 'Manuel Neuer', team: argentina).save(flush:true)

        then:
        Team.findByName('Argentina').players.size()==1

        when:
        session.flush()
        session.clear()
        manuel = Player.findByName('Manuel Neuer')
        manuel.team = germany
        manuel.save(validate:false)

        session.flush()
        session.clear()

        then:
        Team.findByName('Germany').players.size()==1
        Team.findByName('Argentina').players.size()==0
    }

    def "lastUpdated is updated"() {
        setup:
        new Timestamped(name: "test").save()
        session.flush()
        session.clear()

        when:
        def ts = Timestamped.findByName("test")

        then:
        ts.dateCreated != null
        ts.lastUpdated != null
        ts.dateCreated == ts.lastUpdated
        ts.version == 0

        when: //domains with timestamps will persist the last updated
              //even if nothing is dirty
        ts.save()
        session.flush()
        session.clear()
        def newTs = Timestamped.findByName("test")

        then:
        newTs.lastUpdated > newTs.dateCreated
        newTs.version == 1
    }
}

@Entity
class Tournament implements Neo4jEntity<Tournament> {
    Long id
    Long version
    String name
    List teams
    static hasMany = [teams: Team ]
    static mapping = {
        teams(lazy: true)
    }
}

//@Bindable
@Entity
class Team implements Neo4jEntity<Team> {
    Long id
    Long version
    String name
    Club club
    byte[] binaryData
    Set players
    static hasMany = [players: Player]

    static belongsTo = Club

    static constraints = {
        binaryData nullable: true
    }
}

@Entity
class Club implements Neo4jEntity<Club> {
    Long id
    Long version
    String name
    List teams
    League league
    static belongsTo = [league:League]
    static hasMany = [teams: Team,attributes: Attribute ]
    static constraints = {
        attributes nullable: true
        league nullable: true
    }
    // TODO: maybe refactor this into a AST
    protected Object writeReplace()
                           throws ObjectStreamException {
        return get(id)
    }
}

@Entity
class Attribute {

    String name
    String description

    static constraints = {
        description nullable: true
    }
}


@Entity
@DirtyCheck
class Player implements Neo4jEntity<Player> {
    Long id
    Long version
    String name
    Team team

    Team getTeam() {
        return team
    }

    void setTeam(Team team) {
        this.team = team
    }
}


@Entity
class Timestamped implements Neo4jEntity<Timestamped> {

    String name

    Date dateCreated
    Date lastUpdated
}