package org.grails.gorm.graphql.fetcher.impl

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Used to soft delete entity instances. Alternative to
 * {@link DeleteEntityDataFetcher} to allow users to register
 * their own instances.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class SoftDeleteEntityDataFetcher<T> extends DeleteEntityDataFetcher<T> {

    final String propertyName
    final Object value
    final MappingContext mappingContext

    SoftDeleteEntityDataFetcher(PersistentEntity entity, String propertyName, Object value) {
        super(entity)
        this.mappingContext = entity.mappingContext
        this.propertyName = propertyName
        this.value = value
    }

    @Override
    protected void deleteInstance(GormEntity instance) {
        EntityAccess entityAccess = mappingContext.createEntityAccess(entity, instance)
        entityAccess.setProperty(propertyName, value)
        instance.markDirty(propertyName)
        instance.save()
    }
}
