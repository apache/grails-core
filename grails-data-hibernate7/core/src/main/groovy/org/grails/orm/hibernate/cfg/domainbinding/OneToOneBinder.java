package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.FetchMode;
import org.hibernate.mapping.OneToOne;
import org.hibernate.type.ForeignKeyDirection;

import org.grails.datastore.mapping.model.types.Association;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;

public class OneToOneBinder {

    private final PersistentEntityNamingStrategy namingStrategy;
    private final SimpleValueBinder simpleValueBinder;

    public OneToOneBinder(PersistentEntityNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
        this.simpleValueBinder = new SimpleValueBinder(namingStrategy);
    }

    protected OneToOneBinder(PersistentEntityNamingStrategy namingStrategy,
                             SimpleValueBinder simpleValueBinder) {
        this.namingStrategy = namingStrategy;
        this.simpleValueBinder = simpleValueBinder;
    }

    public void bindOneToOne(final org.grails.datastore.mapping.model.types.OneToOne property, OneToOne oneToOne,
                              String path) {
        PropertyConfig config = ((GrailsHibernatePersistentProperty) property).getMappedForm();
        final Association otherSide = property.getInverseSide();

        final boolean hasOne = otherSide != null && otherSide.isHasOne();
        oneToOne.setConstrained(hasOne);
        oneToOne.setForeignKeyType(oneToOne.isConstrained() ?
                ForeignKeyDirection.FROM_PARENT :
                ForeignKeyDirection.TO_PARENT);
        oneToOne.setAlternateUniqueKey(true);

        if (config != null && config.getFetchMode() != null) {
            oneToOne.setFetchMode(config.getFetchMode());
        }
        else {
            oneToOne.setFetchMode(FetchMode.DEFAULT);
        }

        oneToOne.setReferencedEntityName(otherSide != null ? otherSide.getOwner().getName() : property.getAssociatedEntity().getName());
        oneToOne.setPropertyName(property.getName());
        oneToOne.setReferenceToPrimaryKey(false);

        if (hasOne || otherSide == null) {
            simpleValueBinder.bindSimpleValue(property, null, oneToOne, path);
        }
        else {
            oneToOne.setReferencedPropertyName(otherSide.getName());
        }
    }
}