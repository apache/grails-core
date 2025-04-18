package org.grails.gorm.graphql

import grails.gorm.annotation.Entity
import graphql.schema.GraphQLSchema
import org.grails.datastore.gorm.GormEntity
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping
import spock.lang.Ignore

class DisableReadOpSpec extends HibernateSpec {

    @Override
    List<Class> getDomainClasses() {
        [ReadDisabledEntity]
    }

    // As of graphql-java 15.0, it appears a queryType is required
    @Ignore
    void "test that disable all operation in clean way"() {

        when:
        GraphQLSchema schema = new Schema(hibernateDatastore.mappingContext)
                .generate()

        then:
        schema.queryType.fieldDefinitions.isEmpty()

        and:
        schema.mutationType.fieldDefinitions.size() == 3
        schema.mutationType.fieldDefinitions.find {it.name == "readDisabledEntityCreate"}
        schema.mutationType.fieldDefinitions.find {it.name == "readDisabledEntityDelete"}
        schema.mutationType.fieldDefinitions.find {it.name == "readDisabledEntityUpdate"}
    }
}

@Entity
class ReadDisabledEntity implements GormEntity<ReadDisabledEntity> {

    String prop

    static graphql = GraphQLMapping.build {
        operations.query.enabled false
    }
}
