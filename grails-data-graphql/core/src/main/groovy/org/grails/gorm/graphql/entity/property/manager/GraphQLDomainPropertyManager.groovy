package org.grails.gorm.graphql.entity.property.manager

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping
import org.grails.gorm.graphql.entity.property.GraphQLDomainProperty

/**
 * An interface to describe a class creates builder instances that retrieve
 * {@link GraphQLDomainProperty} instances based on conditions
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLDomainPropertyManager {

    /**
     * @return A new builder instance
     */
    Builder builder()

    interface Builder {

        /**
         * Exclude identifier properties from being returned
         */
        Builder excludeIdentifiers()

        /**
         * Exclude identifier properties from being returned
         *
         * @param exceptComposite If true, composite identifiers will be included
         */
        Builder excludeIdentifiers(boolean exceptComposite)

        /**
         * Exclude the version property from being returned
         */
        Builder excludeVersion()

        /**
         * Exclude 'dateCreated' and 'lastUpdated' from being returned
         */
        Builder excludeTimestamps()

        /**
         * Exclude properties from being returned
         *
         * @param props One or more property names
         */
        Builder exclude(String... props)

        /**
         * Exclude properties based on the return returnType of the provided
         * closure. If the closure returns false, the property will not
         * be returned.
         *
         * @param closure The closure to execute. The {@link org.grails.datastore.mapping.model.PersistentProperty}
         * instance will be passed as the first argument.
         */
        Builder condition(Closure closure)

        /**
         * Whether or not properties should allow nulls should be overridden
         * so that properties are nullable, even if they otherwise would not be.
         */
        Builder alwaysNullable()

        /**
         * Retrieves the desired properties based on the conditions previously applied
         * The mapping will be retrieved from the entity `static graphql = ..`
         *
         * @param entity The entity to retrieve properties from
         * @return The list of GraphQL domain properties
         */
        List<GraphQLDomainProperty> getProperties(PersistentEntity entity)

        /**
         * Retrieves the desired properties based on the conditions previously applied
         *
         * @param entity The entity to retrieve properties from
         * @param mapping The entity mapping to build domain properties with
         * @return The list of GraphQL domain properties
         */
        List<GraphQLDomainProperty> getProperties(PersistentEntity entity, GraphQLMapping mapping)
    }
}
