package myapp

import grails.test.mixin.integration.Integration
import org.grails.datastore.gorm.GormEnhancer
import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import org.grails.orm.hibernate.HibernateDatastore

@Integration
class FooIntegrationSpec implements GraphQLSpec {

    void "test a foo can be created"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                fooCreate(foo: {name: "x"}) {
                    id
                    errors {
                        field
                        message
                    }
                }
            }
        """)
        Map obj = resp.body().data.fooCreate

        then:
        obj.id == 1
        GormEnhancer.findStaticApi(Foo).datastore instanceof HibernateDatastore
    }
}
