package org.grails.gorm.graphql.fetcher.impl

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.mapping.reflect.FieldEntityAccess

/**
 * A default data fetcher for persistent properties that
 * uses GORM instead of the standard reflection used by the
 * default {@link graphql.schema.PropertyDataFetcher}
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class PersistentPropertyDataFetcher implements DataFetcher {

    private String name
    private EntityReflector entityReflector

    PersistentPropertyDataFetcher(PersistentProperty property) {
        this.name = property.name
        this.entityReflector = FieldEntityAccess.getOrIntializeReflector(property.owner)
    }

    @Override
    Object get(DataFetchingEnvironment environment) {
        entityReflector.getPropertyReader(name).getter().invoke(environment.source)
    }
}
