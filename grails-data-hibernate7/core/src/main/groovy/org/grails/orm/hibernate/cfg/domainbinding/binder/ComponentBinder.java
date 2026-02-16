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

import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.MappingCacheHolder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder;
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionType;
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher;

import jakarta.annotation.Nonnull;

public class ComponentBinder {

    private static final Logger LOG = LoggerFactory.getLogger(ComponentBinder.class);

    private final MetadataBuildingContext metadataBuildingContext;
    private final MappingCacheHolder mappingCacheHolder;
    private final CollectionHolder collectionHolder;
    private final EnumTypeBinder enumTypeBinder;
    private final CollectionBinder collectionBinder;
    private final ManyToOneBinder manyToOneBinder;
    private final OneToOneBinder oneToOneBinder;
    private final ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher;
    private final SimpleValueBinder simpleValueBinder;
    private final ComponentUpdater componentUpdater;

    public ComponentBinder(MetadataBuildingContext metadataBuildingContext,
                           MappingCacheHolder mappingCacheHolder,
                           CollectionHolder collectionHolder,
                           EnumTypeBinder enumTypeBinder,
                           CollectionBinder collectionBinder,
                           SimpleValueBinder simpleValueBinder,
                           OneToOneBinder oneToOneBinder,
                           ManyToOneBinder manyToOneBinder,
                           ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
                           ComponentUpdater componentUpdater) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.mappingCacheHolder = mappingCacheHolder;
        this.collectionHolder = collectionHolder;
        this.enumTypeBinder = enumTypeBinder;
        this.collectionBinder = collectionBinder;
        this.simpleValueBinder = simpleValueBinder;
        this.oneToOneBinder = oneToOneBinder;
        this.manyToOneBinder = manyToOneBinder;
        this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher;
        this.componentUpdater = componentUpdater;
    }


    public Component bindComponent(PersistentClass owner, HibernateEmbeddedProperty property,
                              @Nonnull InFlightMetadataCollector mappings) {
        Component component = new Component(metadataBuildingContext, owner);
        Class<?> type = property.getType();
        String role = GrailsHibernateUtil.qualify(type.getName(), property.getName());
        component.setRoleName(role);
        component.setComponentClassName(type.getName());

        GrailsHibernatePersistentEntity domainClass = (GrailsHibernatePersistentEntity) property.getAssociatedEntity();
        mappingCacheHolder.cacheMapping(domainClass);
        var properties = domainClass.getHibernatePersistentProperties();
        Table table = component.getOwner().getTable();
        PersistentClass persistentClass = component.getOwner();
        String path = property.getName();
        Class<?> propertyType = property.getOwner().getJavaClass();

        for (GrailsHibernatePersistentProperty currentGrailsProp : properties) {
            if (currentGrailsProp.equals(domainClass.getIdentity())) continue;
            if (currentGrailsProp.getName().equals(GormProperties.VERSION)) continue;

            if (currentGrailsProp.getType().equals(propertyType)) {
                component.setParentProperty(currentGrailsProp.getName());
                continue;
            }
            var value = bindComponentProperty(component, property, currentGrailsProp, persistentClass, path, table, mappings);
            componentUpdater.updateComponent(component, property, currentGrailsProp, value);

        }
        return component;
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

            value = manyToOneBinder.bindManyToOne((Association) currentGrailsProp, table, path);
        } else if (currentGrailsProp instanceof org.grails.datastore.mapping.model.types.OneToOne association) {
            if (LOG.isDebugEnabled())
                LOG.debug("[GrailsDomainBinder] Binding property [" + currentGrailsProp.getName() + "] as OneToOne");

            if (association.canBindOneToOneWithSingleColumnAndForeignKey()) {
                value = oneToOneBinder.bindOneToOne((org.grails.datastore.mapping.model.types.OneToOne) currentGrailsProp, persistentClass, table, path);
            }
            else {
                value = manyToOneBinder.bindManyToOne((Association) currentGrailsProp, table, path);
            }
        }
        else if (currentGrailsProp instanceof HibernateEmbeddedProperty embedded) {
            value = bindComponent(persistentClass, embedded, mappings);
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
        return value;
    }
}
