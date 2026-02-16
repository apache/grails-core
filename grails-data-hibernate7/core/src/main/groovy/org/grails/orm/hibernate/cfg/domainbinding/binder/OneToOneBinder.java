package org.grails.orm.hibernate.cfg.domainbinding.binder;

import org.hibernate.FetchMode;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.OneToOne;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.type.ForeignKeyDirection;

import org.grails.datastore.mapping.model.types.Association;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;

public class OneToOneBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final SimpleValueBinder simpleValueBinder;

    public OneToOneBinder(MetadataBuildingContext metadataBuildingContext, PersistentEntityNamingStrategy namingStrategy, SimpleValueBinder simpleValueBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.namingStrategy = namingStrategy;
        this.simpleValueBinder = simpleValueBinder;
    }

    public OneToOneBinder(MetadataBuildingContext metadataBuildingContext, PersistentEntityNamingStrategy namingStrategy, JdbcEnvironment jdbcEnvironment) {
        this(metadataBuildingContext, namingStrategy, new SimpleValueBinder(namingStrategy, jdbcEnvironment));
    }

    public OneToOne bindOneToOne(final org.grails.datastore.mapping.model.types.OneToOne property
            , PersistentClass owner
            , org.hibernate.mapping.Table table
            , String path) {
        OneToOne oneToOne = new OneToOne(metadataBuildingContext, table, owner);
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
            simpleValueBinder.bindSimpleValue((GrailsHibernatePersistentProperty) property, null, oneToOne, path);
        }
        else {
            oneToOne.setReferencedPropertyName(otherSide.getName());
        }
        return oneToOne;
    }
}