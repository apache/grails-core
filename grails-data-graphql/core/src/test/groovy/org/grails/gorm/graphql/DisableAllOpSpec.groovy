package org.grails.gorm.graphql

import grails.gorm.annotation.Entity
import graphql.schema.GraphQLSchema
import org.grails.datastore.gorm.GormEntity
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

class DisableAllOpSpec extends HibernateSpec {

    @Override
    List<Class> getDomainClasses() {
        [AllDisabledOpEntity]
    }

    void "test that disable all operation in clean way"() {

        when:
        GraphQLSchema schema = new Schema(hibernateDatastore.mappingContext)
                .generate()

        then:
        !schema
    }
}

@Entity
class AllDisabledOpEntity implements GormEntity<AllDisabledOpEntity> {

    String prop

    static graphql = GraphQLMapping.build {
        operations.all.enabled false
    }
}
