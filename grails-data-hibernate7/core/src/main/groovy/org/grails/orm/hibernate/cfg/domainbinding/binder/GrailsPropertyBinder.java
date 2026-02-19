package org.grails.orm.hibernate.cfg.domainbinding.binder;

import jakarta.annotation.Nonnull;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator;
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher;

import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrailsPropertyBinder {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsPropertyBinder.class);

    private final MetadataBuildingContext metadataBuildingContext;
    private final EnumTypeBinder enumTypeBinder;
    private final ComponentBinder componentBinder;
    private final CollectionBinder collectionBinder;
    private final SimpleValueBinder simpleValueBinder;
    private final ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher;
    private final OneToOneBinder oneToOneBinder;
    private final ManyToOneBinder manyToOneBinder;
    private final PropertyFromValueCreator propertyFromValueCreator;

    public GrailsPropertyBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            EnumTypeBinder enumTypeBinder,
            ComponentBinder componentBinder,
            CollectionBinder collectionBinder,
            SimpleValueBinder simpleValueBinder,
            ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
            OneToOneBinder oneToOneBinder,
            ManyToOneBinder manyToOneBinder,
            PropertyFromValueCreator propertyFromValueCreator) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.enumTypeBinder = enumTypeBinder;
        this.componentBinder = componentBinder;
        this.collectionBinder = collectionBinder;
        this.simpleValueBinder = simpleValueBinder;
        this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher;
        this.oneToOneBinder = oneToOneBinder;
        this.manyToOneBinder = manyToOneBinder;
        this.propertyFromValueCreator = propertyFromValueCreator;
    }

    public Value bindProperty(PersistentClass persistentClass
            , Table table
            , String path
            , GrailsHibernatePersistentProperty parentProperty
            , @Nonnull GrailsHibernatePersistentProperty currentGrailsProp
            , @Nonnull InFlightMetadataCollector mappings) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[GrailsPropertyBinder] Binding persistent property [" + currentGrailsProp.getName() + "]");
        }

        Value value = null;

        // 1. Create Value and apply binders (consolidated block)
        if (currentGrailsProp.isEnumType()) {
            //HibernateEnumTypeProperty
            value = enumTypeBinder.bindEnumType(currentGrailsProp, currentGrailsProp.getType(), table, path);
        } else if (currentGrailsProp instanceof HibernateOneToOneProperty oneToOne) {
            //HibernateOneToOneProperty
            if (oneToOne.isHibernateOneToOne()) {
                value = oneToOneBinder.bindOneToOne((org.grails.datastore.mapping.model.types.OneToOne) currentGrailsProp, persistentClass, table, path);
            } else {
                value = manyToOneBinder.bindManyToOne((Association) currentGrailsProp, table, path);
            }
        } else if (currentGrailsProp instanceof HibernateManyToOneProperty manyToOne) {
            value = manyToOneBinder.bindManyToOne((Association) currentGrailsProp, table, path);
        } else if (currentGrailsProp instanceof HibernateToManyProperty toMany && !currentGrailsProp.isSerializableType()) {
            //HibernateToManyProperty
            value = collectionBinder.bindCollection(toMany, persistentClass, mappings, path);
        } else if (currentGrailsProp instanceof HibernateEmbeddedProperty embedded) {
            value = componentBinder.bindComponent(persistentClass, embedded, mappings, path);
        } else {
            //HibernateSimpleProperty
            value = simpleValueBinder.bindSimpleValue(currentGrailsProp, parentProperty, table, path);
        }

        return value;
    }
}
