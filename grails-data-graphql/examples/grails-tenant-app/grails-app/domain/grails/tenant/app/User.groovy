package grails.tenant.app

import grails.gorm.MultiTenant
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

class User implements MultiTenant<User> {

    String name
    String companyId

    static constraints = {
    }

    static mapping = {
        tenantId name: 'companyId'
    }
    static graphql = GraphQLMapping.build {
        property('companyId') {
            input false
        }
    }
}
