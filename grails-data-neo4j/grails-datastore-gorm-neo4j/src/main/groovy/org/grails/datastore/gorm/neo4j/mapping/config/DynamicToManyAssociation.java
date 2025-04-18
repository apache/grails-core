package org.grails.datastore.gorm.neo4j.mapping.config;

import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PropertyMapping;
import org.grails.datastore.mapping.model.types.ToMany;

/**
 * Represents an association that is dynamic and is created at runtime
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public class DynamicToManyAssociation extends ToMany implements DynamicAssociation{

    public DynamicToManyAssociation(PersistentEntity owner, MappingContext context, String name, PersistentEntity associatedType) {
        super(owner, context, name, associatedType.getJavaClass());
        setAssociatedEntity(associatedType);
        setOwningSide(true);
    }

    @Override
    public PropertyMapping getMapping() {
        return null;
    }
}
