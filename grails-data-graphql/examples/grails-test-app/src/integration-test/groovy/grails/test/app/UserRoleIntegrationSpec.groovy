package grails.test.app

import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import org.spockframework.util.StringMessagePrintStream
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@Integration
@Stepwise
class UserRoleIntegrationSpec extends Specification implements GraphQLSpec {

    @Shared Long userId
    @Shared Long roleId

    @OnceBefore
    void createUserAndRole() {
        def resp = graphQL.graphql("""
            mutation {
                userCreate(user: {
                    firstNumber: 2,
                    secondNumber: 3,
                    profile: {
                        email: "admin@email.com",
                        firstName: "First",
                        lastName: "Last"
                    }
                    address: {
                        city: "Youngstown",
                        state: "OH",
                        zip: 44512
                    }
                }) {
                    id
                }
            }
        """)
        Map obj = resp.body().data.userCreate
        userId = obj.id

        resp = graphQL.graphql("""
            mutation {
                roleCreate(role: {
                    name: "ROLE_ADMIN"
                }) {
                    id
                }
            }
        """)
        obj = resp.body().data.roleCreate
        roleId = obj.id
    }

    void "test creating an entity with a complex composite id"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                userRoleCreate(userRole: {
                    user: {
                        id: ${userId}
                    },
                    role: {
                        id: ${roleId}
                    }
                }) {
                    user {
                        profile {
                            email
                        }
                    }
                    role {
                        name
                    }
                }
            }
        """)
        Map obj = resp.body().data.userRoleCreate

        then:
        obj.user.profile.email == 'admin@email.com'
        obj.role.name == 'ROLE_ADMIN'
    }

    void "test reading an entity with a complex composite id"() {
        given:
        PrintStream originalOut = System.out
        String query
        int outCount = 0
        System.setOut(new StringMessagePrintStream() {
            @Override
            protected void printed(String message) {
                query = message
                outCount++
            }
        })

        when:
        def resp = graphQL.graphql("""
            {
                userRole(role: ${roleId}, user: ${userId}) {
                    user {
                        id
                    }
                    role {
                        id
                    }
                }
            }
        """.toString())
        Map obj = resp.body().data.userRole

        then:
        obj.user.id == userId
        obj.role.id == roleId
        outCount == 1
        query ==~ 'Hibernate: select this_.user_id as user_id[0-9]+_[0-9]+_[0-9]+_, this_.role_id as role_id[0-9]+_[0-9]+_[0-9]+_ from user_role this_ where this_.user_id=\\? and this_.role_id=\\? limit \\?\n'

        when:
        outCount = 0
        resp = graphQL.graphql("""
            {
                userRole(role: ${roleId}, user: ${userId}) {
                    user {
                        profile {
                            email
                        }
                    }
                    role {
                        name
                    }
                }
            }
        """.toString())
        obj = resp.body().data.userRole

        then: 'The user and role will be fetched with the same query'
        obj.user.profile.email == 'admin@email.com'
        obj.role.name == 'ROLE_ADMIN'
        outCount == 1
        query ==~ 'Hibernate: select this_.user_id as user_id[0-9]+_[0-9]{2}_[0-9]_, this_.role_id as role_id[0-9]+_[0-9]+_[0-9]+_, user2_.id as id[0-9]+_[0-9]+_[0-9]+_, user2_.version as version[0-9]+_[0-9]+_[0-9]+_, user2_.manager_id as manager_[0-9]+_[0-9]+_[0-9]+_, user2_.added_numbers as added_nu[0-9]+_[0-9]+_[0-9]+_, user2_.address_zip as address_[0-9]+_[0-9]+_[0-9]+_, user2_.address_city as address_[0-9]+_[0-9]+_[0-9]+_, user2_.address_state as address_[0-9]+_[0-9]+_[0-9]+_, user2_.profile_first_name as profile_[0-9]+_[0-9]+_[0-9]+_, user2_.profile_last_name as profile_[0-9]+_[0-9]+_[0-9]+_, user2_.profile_email as profile[0-9]+_[0-9]+_[0-9]+_, role3_.id as id[0-9]+_[0-9]+_[0-9]+_, role3_.version as version[0-9]+_[0-9]+_[0-9]+_, role3_.authority as authorit[0-9]+_[0-9]+_[0-9]+_ from user_role this_ inner join user user2_ on this_.user_id=user2_.id inner join role role3_ on this_.role_id=role3_.id where this_.user_id=\\? and this_.role_id=\\?\n'

        cleanup:
        System.setOut(originalOut)
    }

    void "test we cannot update a UserRole because it is turned off in the mapping"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                userRoleUpdate(role: ${roleId}, user: ${userId}) {
                    user {
                        id
                    }
                    role {
                        id
                    }
                }
            }
        """.toString())
        Map result = resp.body()

        then:
        result.errors.size() == 1
        result.errors[0].message == "Validation error (FieldUndefined@[userRoleUpdate]) : Field 'userRoleUpdate' in type 'Mutation' is undefined"
    }

    void "test listing entities with a complex composite id"() {
        when:
        def resp = graphQL.graphql("""
            {
                userRoleList {
                    user {
                        profile {
                            email
                        }
                    }
                    role {
                        name
                    }
                }
            }
        """.toString())
        List obj = resp.body().data.userRoleList

        then:
        obj.size() == 1
        obj[0].user.profile.email == 'admin@email.com'
        obj[0].role.name == 'ROLE_ADMIN'
    }

    void "test custom query operation added in the mapping"() {
        when:
        def resp = graphQL.graphql("""
            {
                usersByRole(role: ${roleId}) {
                    profile {
                        email
                    }
                }
            }
        """.toString())
        List obj = resp.body().data.usersByRole

        then:
        obj.size() == 1
        obj[0].profile.email == 'admin@email.com'
    }

    void "test custom mutation operation added in the mapping"() {
        setup: 'Add another role to the existing user'
        def resp = graphQL.graphql("""
            mutation {
                roleCreate(role: {
                    name: "ROLE_USER"
                }) {
                    id
                }
            }
        """)
        Long newRoleId = resp.body().data.roleCreate.id
        graphQL.graphql("""
            mutation {
                userRoleCreate(userRole: {
                    user: {
                        id: ${userId}
                    },
                    role: {
                        id: ${newRoleId}
                    }
                }) {
                    user {
                        id
                    }
                    role {
                        id
                    }
                }
            }
        """.toString())

        when:
        resp = graphQL.graphql("""
            {
                userRoleList {
                    role {
                        name
                    }
                }
            }
        """.toString())
        List list = resp.body().data.userRoleList

        then:
        list.size() == 2

        when:
        resp = graphQL.graphql("""
            mutation {
                revokeAllRoles(user: ${userId}) {
                    success
                }
            }
        """.toString())
        Map obj = resp.body().data.revokeAllRoles

        then:
        obj.success

        when:
        resp = graphQL.graphql("""
            {
                userRoleList {
                    user {
                        id
                    }
                    role {
                        id
                    }
                }
            }
        """.toString())
        list = resp.body().data.userRoleList

        then: 'Check if the delete worked'
        list.empty

        cleanup: 'Re-create the user role so the next test can delete it'
        graphQL.graphql("""
            mutation {
                userRoleCreate(userRole: {
                    user: {
                        id: ${userId}
                    },
                    role: {
                        id: ${roleId}
                    }
                }) {
                    user {
                        id
                    }
                    role {
                        id
                    }
                }
            }
        """.toString())
    }

    void "test deleting an entity with a complex composite id"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                userRoleDelete(role: ${roleId}, user: ${userId}) {
                    success
                }
            }
        """.toString())
        Map obj = resp.body().data.userRoleDelete

        then:
        obj.success
    }
}
