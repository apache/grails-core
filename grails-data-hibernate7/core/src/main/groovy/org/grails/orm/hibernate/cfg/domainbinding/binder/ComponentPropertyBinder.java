package org.grails.orm.hibernate.cfg.domainbinding.binder;

import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.OneToOne;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.datastore.mapping.model.types.Association;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder;
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionType;
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher;

import jakarta.annotation.Nonnull;

public class ComponentPropertyBinder {

    private static final Logger LOG = LoggerFactory.getLogger(ComponentPropertyBinder.class);

    private final MetadataBuildingContext metadataBuildingContext;
    private final CollectionHolder collectionHolder;
    private final EnumTypeBinder enumTypeBinder;
    private final CollectionBinder collectionBinder;
    private final ManyToOneBinder manyToOneBinder;
    private final OneToOneBinder oneToOneBinder;
    private final ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher;
    private final SimpleValueBinder simpleValueBinder;
    private final ComponentUpdater componentUpdater;
    private ComponentBinder componentBinder;

    public ComponentPropertyBinder(MetadataBuildingContext metadataBuildingContext,
                                      CollectionHolder collectionHolder,
                                      EnumTypeBinder enumTypeBinder,
                                      CollectionBinder collectionBinder,
                                      SimpleValueBinder simpleValueBinder,
                                      OneToOneBinder oneToOneBinder,
                                      ManyToOneBinder manyToOneBinder,
                                      ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
                                      ComponentUpdater componentUpdater) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.collectionHolder = collectionHolder;
        this.enumTypeBinder = enumTypeBinder;
        this.collectionBinder = collectionBinder;
        this.simpleValueBinder = simpleValueBinder;
        this.oneToOneBinder = oneToOneBinder;
        this.manyToOneBinder = manyToOneBinder;
        this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher;
        this.componentUpdater = componentUpdater;
    }

    public void setComponentBinder(ComponentBinder componentBinder) {
        this.componentBinder = componentBinder;
    }

    public Value bindComponentProperty(Component component,
                                       GrailsHibernatePersistentProperty componentProperty,
                                       GrailsHibernatePersistentProperty currentGrailsProp,
                                       PersistentClass persistentClass,
                                       String path,
                                       Table table,
                                       @Nonnull InFlightMetadataCollector mappings) {
        Value value;
        // see if it's a collection type
        CollectionType collectionType = collectionHolder.get(currentGrailsProp.getType());
        if (collectionType != null) {
            // create collection
            Collection collection = collectionType.create((HibernateToManyProperty) currentGrailsProp, persistentClass);
            collectionBinder.bindCollection((HibernateToManyProperty) currentGrailsProp, collection, persistentClass, mappings, path);
            mappings.addCollectionBinding(collection);
            value = collection;
        }
        // work out what type of relationship it is and bind value
        else if (currentGrailsProp instanceof org.grails.datastore.mapping.model.types.ManyToOne) {
            if (LOG.isDebugEnabled())
                LOG.debug("[GrailsDomainBinder] Binding property [" + currentGrailsProp.getName() + "] as ManyToOne");

            value = new ManyToOne(metadataBuildingContext, table);
            manyToOneBinder.bindManyToOne((Association) currentGrailsProp, (ManyToOne) value, path);
        } else if (currentGrailsProp instanceof org.grails.datastore.mapping.model.types.OneToOne association) {
            if (LOG.isDebugEnabled())
                LOG.debug("[GrailsDomainBinder] Binding property [" + currentGrailsProp.getName() + "] as OneToOne");

            if (association.canBindOneToOneWithSingleColumnAndForeignKey()) {
                value = new OneToOne(metadataBuildingContext, table, persistentClass);
                oneToOneBinder.bindOneToOne((org.grails.datastore.mapping.model.types.OneToOne) currentGrailsProp, (OneToOne) value, path);
            }
            else {
                value = new ManyToOne(metadataBuildingContext, table);
                manyToOneBinder.bindManyToOne((Association) currentGrailsProp, (ManyToOne) value, path);
            }
        }
        else if (currentGrailsProp instanceof HibernateEmbeddedProperty embedded) {
            value = componentBinder.bindComponent(persistentClass, embedded, mappings);
        }
        else {
            if (LOG.isDebugEnabled())
                LOG.debug("[GrailsDomainBinder] Binding property [" + currentGrailsProp.getName() + "] as SimpleValue");

            value = new BasicValue(metadataBuildingContext, table);
            if (currentGrailsProp.getType().isEnum()) {
                String columnName = columnNameForPropertyAndPathFetcher.getColumnNameForPropertyAndPath(currentGrailsProp, path, null);
                enumTypeBinder.bindEnumType(currentGrailsProp, currentGrailsProp.getType(), (SimpleValue) value, columnName);
            }
            else {
                // set type
                this.simpleValueBinder.bindSimpleValue(currentGrailsProp, componentProperty, (SimpleValue) value, path);
            }
        }
        componentUpdater.updateComponent(component, componentProperty, currentGrailsProp, value);
        return value;
    }

}
