package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.gorm.dirty.checking.DirtyCheck
import org.grails.datastore.gorm.neo4j.util.IteratorUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Issue

class ManyToManySpec extends GormDatastoreSpec {

    private static Logger log = LoggerFactory.getLogger(ManyToManySpec.class);

    @Override
    List getDomainClasses() {
        [Role, User, MBook, MBookworm, BidirectionalFriends]
    }

    /*def setupSpec() {
        new BasicConfigurator().configure()
    }*/

    def "check if manytomany relationships are persistent correctly"() {
        setup:
            def user = new User(username: 'user1').addToRoles(new Role(role:'role1'))
            user.save(flush:true)
            session.clear()

        when:
            user = User.findByUsername('user1')

        then:
            user.roles
            1 == user.roles.size()
            user.roles.every { it instanceof Role }

        when:
            def role = Role.findByRole('role1')

        then:
            role.people
            1 == role.people.size()
            role.people.every { it instanceof User }
    }

    def "insert multiple instances with many-to-many"() {

        setup:
            ['ROLE_USER', 'ROLE_ADMIN'].each {
                new Role(role:it).save()
            }

            def user = new User(username: 'initial')
            user.addToRoles(Role.findByRole('ROLE_ADMIN'))
            user.save(flush:true)
            session.clear()

        when:
            ['user1': ['ROLE_USER'],
             'user2': ['ROLE_ADMIN', 'ROLE_USER']].each { username, roles ->
                user = new User(username: username)
                roles.each {
                    user.addToRoles(Role.findByRole(it))
                }
                user.save()
            }

            session.flush()
            session.clear()

        then:
            3 == User.count()
            1 == User.findByUsername('user1').roles.size()
            2 == User.findByUsername('user2').roles.size()
            2 == Role.findByRole('ROLE_USER').people.size()
            2 == Role.findByRole('ROLE_ADMIN').people.size()
    }

    def "test if setter on m2m property also updates reverse collection"() {
        setup:
            def roleAdmin = new Role(role:'ROLE_ADMIN').save()
            def roleUser = new Role(role:'ROLE_USER').save()
            def roleSpecial = new Role(role:'ROLE_SPECIAL').save()
            def user = new User(username: 'user', roles: [roleUser]).save()
            session.flush()
            session.clear()

        when:
            user = User.get(user.id)
            roleAdmin = Role.get(roleAdmin.id)
            roleUser = Role.get(roleUser.id)
            roleSpecial = Role.get(roleSpecial.id)
        then:
            user.roles.size()==1

        when: "using setter for a bidi collection"
            user.roles = [ roleAdmin, roleUser, roleSpecial ]  // should be tracked by dirtycheckable
            session.flush()
            session.clear()
            user = User.get(user.id)

        then:
            user.roles.size() == 3
            user.roles.every { it.people.size()==1 }

    }

    // TODO: this testcase belongs semantically into OneToManySpec
    def "check multiple one-to-many relationships of the same type"() {
        setup:
        def user = new User(username: 'person1')
        user.save(flush:true)
        session.clear()

        when: "creating a lonely user"
        user = User.findByUsername('person1')

        then: "user has no friends and foes"
        user
        user.friends.size() == 0
        user.foes.size() == 0
        user.bestBuddy == null

        when: "adding some friend and foe"
        user.addToFriends(username:'friend1')
        user.addToFoes(username:'foe1')
        user.save()
        session.flush()
        session.clear()
        user = User.findByUsername('person1')

        then: "friends and foes are found"
        user
        user.friends.size() == 1
        user.friends.every { it.username =~ /friend\d/ }
        user.foes.size() == 1
        user.foes.every { it.username =~ /foe\d/ }
        user.bestBuddy == null

        when: "setting bestbuddy"
        user.bestBuddy = User.findByUsername('friend1') // new User(username:'bestBuddy')
        user.save()
        session.flush()
        session.clear()
        user = User.findByUsername('person1')

        then: "bestBuddy is there"
        user.bestBuddy.username == 'friend1'

        and: 'friends and foes are not modified'
        user.friends.size() == 1
        user.friends.every { it.username =~ /friend\d/ }
        user.foes.size() == 1
        user.foes.every { it.username =~ /foe\d/ }

    }

    def "test if addToXXX modifies the nodespace even if it's the only operation in a session"() {
        when:
        def friend = new User(username: 'friend').save()
        def user = new User(username: 'user').save(flush:true)
        user.addToFriends(friend)
        session.flush()
        session.clear()
        user = User.get(user.id)

        then:
        user.friends.size()==1

    }

    def "should two one-to-one relationships be independent"() {

        setup:
        def randy = new MBookworm(name: 'Randy', favoriteBook: null)
        randy.save(failOnError: true)
        def encyclopedia = new MBook(name: 'Encyclopedia Volume 1', checkedOutBy: randy)
        encyclopedia.save(failOnError: true)
        session.flush()
        session.clear()

        when:
        randy = MBookworm.findByName('Randy')

        then:
        randy.favoriteBook==null

    }

    def "should adding many2many create relationships when non owningside is added"() {
        setup:
        def foo = new User(username: 'foo').save()
        def bar = new User(username: 'bar').save()
        session.flush()
        session.clear()

        when: "we change a object after save"
        def role = new Role(role:'myRole').save(flush:true)
        role = Role.get(role.id)
        role.people = [foo, bar]
        session.flush()
        session.clear()

        then:
        Role.findById(role.id).people.size() == 2

    }

    @Issue("GPNEO4J-20")
    def "should version not increase when adding relationships"() {
        setup:
        def felix = new BidirectionalFriends(name: 'felix').save(flush: true)
        session.clear()

        when: "adding 100 people being friend with felix"
        (0..<100).each {
            new BidirectionalFriends(name: "buddy$it", friends: [felix]).save()
        }
        session.flush()
        session.clear()
        def fetchedFelix = BidirectionalFriends.findByName("felix")

        then:
        fetchedFelix.version == felix.version

        and: "inverse side has correct number of friends"
        fetchedFelix.friends.size() == 100

        when: "we have 100 relationships"
        def result = session.transaction.nativeTransaction.run("MATCH (:BidirectionalFriends {name:\$1})<-[:FRIENDS]-(o) return count(o) as c", ["1":"felix"])

        then:
        IteratorUtil.single(result)["c"].asNumber() == 100

    }

}

@DirtyCheck
@Entity
class User {
    Long id
    Long version
    String username
    User bestBuddy
    Set roles = []
    Set friends = []
    Set foes = []

    boolean equals(other) {
        if (!(other instanceof User)) {
            return false
        }
        other?.username == username
    }

    int hashCode() {
        username ? username.hashCode() : 0
    }

    static hasMany = [ roles: Role, friends: User, foes: User ]
    static mappedBy = [ bestBuddy:null, friends: null, foes: null ]
    static belongsTo = Role

    static constraints = {
        bestBuddy nullable:true
    }
}

@DirtyCheck
@Entity
class Role {
    Long id
    Long version
    String role
    Set people = []

    boolean equals(other) {
        if ((other==null) || !(other instanceof Role)) {
            return false
        }
        other?.role == role
    }

    int hashCode() {
        role ? role.hashCode() : 0
    }

    static hasMany = [people: User]

    static constraints = {
        role(blank: false, unique: true)
    }
}

@Entity
class MBook implements Serializable {
    Long id
    Long version

    String name
    MBookworm checkedOutBy

    static constraints = {
        name(nullable: false)
        checkedOutBy(nullable: false)
    }

    public String toString() {
        return "Book [name: ${this.name}, checkedOutBy: ${this.checkedOutBy?.name}]"
    }

}

@Entity
class MBookworm implements Serializable {
    Long id
    Long version

    String name
    MBook favoriteBook

    void setFavoriteBook(MBook book) {
        favoriteBook = book
    }

    static constraints = {
        name(nullable: false, blank: false)
        favoriteBook(nullable: true)
    }

    public String toString() {
        return "Bookworm: [name: ${this.name}, favoriteBook: ${this.favoriteBook?.name}]"
    }
}

@DirtyCheck
@Entity
class BidirectionalFriends {
    Long id
    Long version
    String name
    Set friends

    static hasMany = [ friends: BidirectionalFriends ]
    static mappedBy = [ friends: "friends" ]
}
