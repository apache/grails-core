package org.grails.gorm.graphql.entity

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.types.GraphQLPropertyType

/**
 * A class to return the names of class types and query/mutation names
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class GraphQLEntityNamingConvention {

    /**
     * @param entity The persistent entity
     * @return The name to use. Ex: "person"
     */
    String getGet(PersistentEntity entity) {
        entity.decapitalizedName
    }

    /**
     * @param entity The persistent entity
     * @return The name to use. Ex: "personList"
     */
    String getList(PersistentEntity entity) {
        entity.decapitalizedName + 'List'
    }

    /**
     * @param entity The persistent entity
     * @return The name to use. Ex: "personCount"
     */
    String getCount(PersistentEntity entity) {
        entity.decapitalizedName + 'Count'
    }

    /**
     * @param entity The persistent entity
     * @return The name to use. Ex: "personCreate"
     */
    String getCreate(PersistentEntity entity) {
        entity.decapitalizedName + 'Create'
    }

    /**
     * @param entity The persistent entity
     * @return The name to use. Ex: "personUpdate"
     */
    String getUpdate(PersistentEntity entity) {
        entity.decapitalizedName + 'Update'
    }

    /**
     * @param entity The persistent entity
     * @return The name to use. Ex: "personDelete"
     */
    String getDelete(PersistentEntity entity) {
        entity.decapitalizedName + 'Delete'
    }

    private String normalizeType(GraphQLPropertyType type) {
        type.name().split('_').collect { String name ->
            name.toLowerCase().capitalize()
        }.join('').replace('Output', '')
    }

    /**
     * @param entity The persistent entity
     * @param type The property returnType
     * @return The name to use. Ex: "Person", "PersonCreate", "PersonUpdate", "PersonCreateNested"
     */
    String getType(PersistentEntity entity, GraphQLPropertyType type) {
        getType(entity.javaClass.simpleName, type)
    }

    /**
     * @param typeName The custom type name
     * @param type The property returnType
     * @return The name to use. Ex: "Person", "PersonCreate", "PersonUpdate", "PersonCreateNested"
     */
    String getType(String typeName, GraphQLPropertyType type) {
        typeName + normalizeType(type)
    }

    /**
     * @param entity The persistent entity
     * @return The name to use. Ex: "PersonPagedResult"
     */
    String getPagination(PersistentEntity entity) {
        entity.javaClass.simpleName + 'PagedResult'
    }
}
