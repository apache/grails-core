package myapp

import grails.test.mixin.integration.Integration
import org.bson.types.ObjectId
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.gorm.graphql.plugin.testing.GraphQLSpec


@Integration
class BarIntegrationSpec implements GraphQLSpec {

    void "test a bar can be created"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                barCreate(bar: {name: "x"}) {
                    id
                    errors {
                        field
                        message
                    }
                }
            }
        """)
        Map obj = resp.body().data.barCreate

        then:
        new ObjectId((String) obj.id)
        GormEnhancer.findStaticApi(Bar).datastore instanceof MongoDatastore
    }
}
