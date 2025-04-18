package org.grails.gorm.graphql

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.domain.general.description.Annotation
import org.grails.gorm.graphql.domain.hibernate.description.MappingComment
import org.grails.gorm.graphql.domain.general.description.MappingDescription

/**
 * Created by jameskleeh on 7/25/17.
 */
class GraphQLEntityHelperSpec extends HibernateSpec {

    List<Class> getDomainClasses() {
        [Annotation, MappingComment, MappingDescription]
    }

    void "test description from annotation"() {
        given:
        PersistentEntity entity = mappingContext.getPersistentEntity(Annotation.name)

        expect:
        GraphQLEntityHelper.getDescription(entity) == 'Annotation class'
    }

    void "test description from hibernate comment"() {
        given:
        PersistentEntity entity = mappingContext.getPersistentEntity(MappingComment.name)

        expect:
        GraphQLEntityHelper.getDescription(entity) == 'MappingComment class'
    }

    void "test description from graphql mapping"() {
        given:
        PersistentEntity entity = mappingContext.getPersistentEntity(MappingDescription.name)

        expect:
        GraphQLEntityHelper.getDescription(entity) == 'MappingDescription class'
    }
}
