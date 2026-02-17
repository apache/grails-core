package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.KeyValue;

/**
 * Creates primary key value for collection.
 */
public class PrimaryKeyValueCreator {

    private final MetadataBuildingContext metadataBuildingContext;

    public PrimaryKeyValueCreator(MetadataBuildingContext metadataBuildingContext) {
        this.metadataBuildingContext = metadataBuildingContext;
    }

    public DependantValue createPrimaryKeyValue(Collection collection) {
        KeyValue keyValue;
        String propertyRef = collection.getReferencedPropertyName();
        // this is to support mapping by a property
        if (propertyRef == null) {
            keyValue = collection.getOwner().getIdentifier();
        } else {
            keyValue = (KeyValue) collection.getOwner().getProperty(propertyRef).getValue();
        }

        DependantValue key = new DependantValue(metadataBuildingContext, collection.getCollectionTable(), keyValue);
        key.setTypeName(null);
        key.setNullable(true);
        key.setUpdateable(true);

        //JPA now requires to check for sorting
        key.setSorted(collection.isSorted());
        return key;
    }
}
