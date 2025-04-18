package org.grails.gorm.graphql

import grails.gorm.annotation.Entity
import graphql.schema.GraphQLSchema
import org.grails.datastore.gorm.GormEntity
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping
import spock.lang.Ignore

class ReadOnlyOperationsSpec extends HibernateSpec {

    @Override
    List<Class> getDomainClasses() {
        [ReadOpOnlyEntity]
    }

    @Ignore
    void "test that only read operation are enabled in clean way"() {

        when:
        GraphQLSchema schema = new Schema(hibernateDatastore.mappingContext)
                .generate()

        then:
        !schema.mutationType

        and:
        schema.queryType.fieldDefinitions.size() == 3
        schema.queryType.fieldDefinitions.find {it.name == "readOpOnlyEntity"}
        schema.queryType.fieldDefinitions.find {it.name == "readOpOnlyEntityList"}
        schema.queryType.fieldDefinitions.find {it.name == "readOpOnlyEntityCount"}
    }
}

@Entity
class ReadOpOnlyEntity implements GormEntity<ReadOpOnlyEntity> {
    String prop

    static graphql = GraphQLMapping.build {
        operations.mutation.enabled false
    }
}
