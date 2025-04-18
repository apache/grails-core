package org.grails.datastore.gorm.neo4j.mapping.config;

import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PropertyMapping;
import org.grails.datastore.mapping.model.types.ToOne;

/**
 * Represents an association that is dynamic and is created at runtime
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public class DynamicToOneAssociation extends ToOne implements DynamicAssociation{

    public DynamicToOneAssociation(PersistentEntity owner, MappingContext context, String name, PersistentEntity associatedType) {
        super(owner, context, name, associatedType.getJavaClass());
        setAssociatedEntity(associatedType);
        setOwningSide(true);
    }

    @Override
    public PropertyMapping getMapping() {
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof DynamicToOneAssociation) {
            DynamicToOneAssociation other = (DynamicToOneAssociation) obj;
            return other.getName().equals(name) && other.getOwner().getJavaClass().equals(getOwner().getJavaClass());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode() + getOwner().getJavaClass().hashCode();
    }
}
