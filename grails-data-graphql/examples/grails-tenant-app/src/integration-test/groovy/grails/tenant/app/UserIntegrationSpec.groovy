package grails.tenant.app

import grails.testing.mixin.integration.Integration
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import spock.lang.Specification
import spock.lang.Stepwise

@Integration
@Stepwise
class UserIntegrationSpec extends Specification implements GraphQLSpec {

    void "test creating a user without a company"() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '1')

        when:
        def resp = graphQL.graphql("""
            mutation {
                userCreate(user: {
                    name: "Sally"
                }) {
                    id
                    name
                    companyId
                }
            }
        """)
        Map obj = resp.body().data.userCreate

        then: "The company is supplied via multi-tenancy"
        obj.id == 1
        obj.name == "Sally"
        obj.companyId == '1'
    }

    void "test creating other users with a different company"() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '2')

        when:
        def resp = graphQL.graphql("""
            mutation {
                john: userCreate(user: {
                    name: "John"
                }) {
                    id
                    name
                    companyId
                }
                
                joe: userCreate(user: {
                    name: "Joe"
                }) {
                    id
                    name
                    companyId
                }
            }
        """)
        Map obj = resp.body().data

        then: "The company is supplied via multi-tenancy"
        obj.john.name == 'John'
        obj.john.companyId == '2'
        obj.joe.name == 'Joe'
        obj.joe.companyId == '2'
    }

    void "test retrieving a list of users in company 1"() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '1')

        when:
        def resp = graphQL.graphql("""
            {
                userList {
                    name
                }
            }
        """)
        List obj = resp.body().data.userList

        then: "The list is filtered by the company"
        obj.size() == 1
        obj[0].name == 'Sally'
    }

    void "test retrieving a list of users in company 2"() {
        given:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '2')

        when:
        def resp = graphQL.graphql("""
            {
                userList {
                    name
                }
            }
        """)
        List obj = resp.body().data.userList

        then: "The list is filtered by the company"
        obj.size() == 2
        obj.find { it.name == 'Joe' }
        obj.find { it.name == 'John' }
    }

}
