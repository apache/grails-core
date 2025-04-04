package grails.gorm.tests

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity

@SuppressWarnings("GrMethodMayBeStatic")
class DetachCriteriaSubquerySpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        return [User, Group, GroupAssignment, Organisation]
    }

    void "test detached associated criteria in subquery"() {

        setup:
        User supVisor = createUser('supervisor@company.com')
        User user1 = createUser('user1@company.com')
        User user2 = createUser('user2@company.com')

        Group group1 = createGroup('Group 1', supVisor)
        Group group2 = createGroup('Group 2', supVisor)

        assignGroup(user1, group1)
        assignGroup(user1, group2)

        when:
        String supervisorEmail = 'supervisor@company.com'
        DetachedCriteria<User> criteria = User.where {
            def u = User
            exists(
                    GroupAssignment.where {
                        def ga0 = GroupAssignment
                        user.id == u.id && group.supervisor.email == supervisorEmail
                    }.id()
            )
        }
        List<User> result = criteria.list()

        then:
        noExceptionThrown()
        result.size() == 1
    }

    void "test executing detached criteria in sub-query multiple times"() {

        setup:
        Organisation orgA = new Organisation(name: "A")
        orgA.addToUsers(email: 'user1@a')
        orgA.addToUsers(email: 'user2@a')
        orgA.addToUsers(email: 'user3@a')
        orgA.save(flush: true)
        Organisation orgB = new Organisation(name: "B")
        orgB.addToUsers(email: 'user1@b')
        orgB.addToUsers(email: 'user2@b')
        orgB.save(flush: true)

        when:
        DetachedCriteria<User> criteria = User.where {
            inList('organisation', Organisation.where { name == 'A' || name == 'B' }.id())
        }
        List<User> result = criteria.list()
        result = criteria.list()

        then:
        result.size() == 5
    }

    void "test that detached criteria subquery should create implicit alias instead of using this_"() {

        setup:
        User supVisor = createUser('supervisor@company.com')
        User user1 = createUser('user1@company.com')
        User user2 = createUser('user2@company.com')

        Group group1 = createGroup('Group 1', supVisor)
        Group group2 = createGroup('Group 2', supVisor)

        assignGroup(user1, group1)
        assignGroup(user1, group2)

        when:
        String supervisorEmail = 'supervisor@company.com'
        DetachedCriteria<User> criteria = User.where {
            def u = User
            exists(
                    GroupAssignment.where {
                        user.id == u.id && group.supervisor.email == supervisorEmail
                    }.id()
            )
        }
        List<User> result = criteria.list()

        then:
        noExceptionThrown()
        result.size() == 1
    }

    private User createUser(String email) {
        User user = new User(email: email)
        Organisation defaultOrg = Organisation.findOrCreateByName("default")
        defaultOrg.addToUsers(user)
        defaultOrg.save(flush: true)
        user
    }

    private Group createGroup(String name, User supervisor) {
        Group group = new Group()
        group.name = name
        group.supervisor = supervisor
        group.save(flush: true)
    }

    private void assignGroup(User user, Group group) {
        GroupAssignment groupAssignment = new GroupAssignment()
        groupAssignment.user = user
        groupAssignment.group = group
        groupAssignment.save(flush: true)
    }

}


@Entity
class User implements HibernateEntity<User> {
    String email
    static belongsTo = [organisation: Organisation]
    static mapping = {
        table 'T_USER'
    }
}

@Entity
class Group implements HibernateEntity<Group> {
    String name
    User supervisor
    static mapping = {
        table 'T_GROUP'
    }
}

@Entity
class GroupAssignment implements HibernateEntity<GroupAssignment> {
    User user
    Group group
    static mapping = {
        table 'T_GROUP_ASSIGNMENT'
    }
}

@Entity
class Organisation implements HibernateEntity<Organisation> {
    String name
    static hasMany = [users: User]
}
