package org.grails.orm.hibernate.cfg.domainbinding.collectionType;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

/**
 * A Collection type, for the moment only Set is supported
 *
 * @author Graeme
 */
public abstract class CollectionType {

    protected final Class<?> clazz;
    protected final MetadataBuildingContext buildingContext;

    public abstract Collection createCollection(PersistentClass owner);


    public Collection create(HibernateToManyProperty property
            , PersistentClass owner) throws MappingException {
        Collection coll = createCollection(owner);
        coll.setCollectionTable(owner.getTable());
        coll.setTypeName(getTypeName(property));
        return coll;
    }

    protected CollectionType(Class<?> clazz, MetadataBuildingContext buildingContext) {
        this.clazz = clazz;
        this.buildingContext = buildingContext;
    }

    @Override
    public String toString() {
        return clazz.getName();
    }



    public String getTypeName(HibernateToManyProperty property) {
        return property.getTypeName();
    }

}
