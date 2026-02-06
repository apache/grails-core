package org.grails.orm.hibernate.cfg.domainbinding;

import jakarta.annotation.Nonnull;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionType;
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder;
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

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.EMPTY_PATH;

public class GrailsPropertyBinder {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsPropertyBinder.class);

    private final MetadataBuildingContext metadataBuildingContext;
    private final CollectionHolder collectionHolder;
    private final EnumTypeBinder enumTypeBinder;
    private final ComponentPropertyBinder componentPropertyBinder;
    private final CollectionBinder collectionBinder;
    private final SimpleValueBinder simpleValueBinder;
    private final ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher;
    private final OneToOneBinder oneToOneBinder;
    private final ManyToOneBinder manyToOneBinder;

    public GrailsPropertyBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            CollectionHolder collectionHolder,
            EnumTypeBinder enumTypeBinder,
            ComponentPropertyBinder componentPropertyBinder,
            CollectionBinder collectionBinder,
            PropertyFromValueCreator propertyFromValueCreator) {
        this(metadataBuildingContext,
                namingStrategy,
                collectionHolder,
                enumTypeBinder,
                componentPropertyBinder,
                collectionBinder,
                new SimpleValueBinder(namingStrategy),
                new ColumnNameForPropertyAndPathFetcher(namingStrategy),
                new OneToOneBinder(namingStrategy),
                new ManyToOneBinder(namingStrategy));
    }

    protected GrailsPropertyBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            CollectionHolder collectionHolder,
            EnumTypeBinder enumTypeBinder,
            ComponentPropertyBinder componentPropertyBinder,
            CollectionBinder collectionBinder,
            SimpleValueBinder simpleValueBinder,
            ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
            OneToOneBinder oneToOneBinder,
            ManyToOneBinder manyToOneBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.collectionHolder = collectionHolder;
        this.enumTypeBinder = enumTypeBinder;
        this.componentPropertyBinder = componentPropertyBinder;
        this.collectionBinder = collectionBinder;
        this.simpleValueBinder = simpleValueBinder;
        this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher;
        this.oneToOneBinder = oneToOneBinder;
        this.manyToOneBinder = manyToOneBinder;
    }

    public Value bindProperty(PersistentClass persistentClass
            , @Nonnull InFlightMetadataCollector mappings
            , String sessionFactoryBeanName
            , @Nonnull GrailsHibernatePersistentProperty currentGrailsProp) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[GrailsPropertyBinder] Binding persistent property [" + currentGrailsProp.getName() + "]");
        }
        Mapping gormMapping =  currentGrailsProp.getHibernateOwner().getMappedForm();
        Table table = persistentClass.getTable();
        table.setComment(gormMapping.getComment());

        Value value = null;

        // see if it's a collection type
        CollectionType collectionType = collectionHolder.get(currentGrailsProp.getType());


        // 1. Create Value and apply binders (consolidated block)
        if (currentGrailsProp.isUserButNotCollectionType()) {
            value = new BasicValue(metadataBuildingContext, table);
            // No specific binder call needed for this case per original logic
            simpleValueBinder.bindSimpleValue(currentGrailsProp, null,(SimpleValue) value, EMPTY_PATH);
        }
        else if (collectionType != null) {
            if (currentGrailsProp.isSerializableType()) {
                value = new BasicValue(metadataBuildingContext, table);
                simpleValueBinder.bindSimpleValue(currentGrailsProp, null,(SimpleValue) value, EMPTY_PATH);// No specific binder call needed
            }
            else { // Actual Collection
                Collection collection = collectionType.create((HibernateToManyProperty) currentGrailsProp, persistentClass
                );
                collectionBinder.bindCollection((HibernateToManyProperty) currentGrailsProp, collection, persistentClass, mappings, EMPTY_PATH, sessionFactoryBeanName);
                mappings.addCollectionBinding(collection);
                value = collection;
                // No specific binder for Collection itself in Block 2 originally.
            }
        }
        else if (currentGrailsProp.getType().isEnum()) {
            value = new BasicValue(metadataBuildingContext, table);
            // Apply enumTypeBinder if the created value is a SimpleValue
            SimpleValue simpleValue = (SimpleValue) value;
            String columnName = columnNameForPropertyAndPathFetcher.getColumnNameForPropertyAndPath(currentGrailsProp, EMPTY_PATH, null);
            enumTypeBinder.bindEnumType(currentGrailsProp, currentGrailsProp.getType(), simpleValue, columnName);
        }
        else if (currentGrailsProp.isHibernateOneToOne()) {
            value = new OneToOne(metadataBuildingContext, table, persistentClass);
            // Apply OneToOneBinder logic
            oneToOneBinder.bindOneToOne((org.grails.datastore.mapping.model.types.OneToOne)currentGrailsProp, (OneToOne)value, EMPTY_PATH);
        } else if(currentGrailsProp.isHibernateManyToOne()) {
            value = new ManyToOne(metadataBuildingContext, table);
            // Apply ManyToOneBinder logic
            manyToOneBinder.bindManyToOne((Association)currentGrailsProp, (ManyToOne)value, EMPTY_PATH);
        }
        else if (currentGrailsProp instanceof Embedded) {
            value = new Component(metadataBuildingContext, persistentClass);
            // Apply ComponentPropertyBinder logic
            componentPropertyBinder.bindComponent((Component)value, (Embedded)currentGrailsProp, true, mappings, sessionFactoryBeanName);
        }
        // work out what type of relationship it is and bind value
        else { // Default BasicValue
            value = new BasicValue(metadataBuildingContext, table);
            simpleValueBinder.bindSimpleValue(currentGrailsProp, null,(SimpleValue) value, EMPTY_PATH);
        }

        // After creating the value and applying binders (where applicable), create and add the property.
        // This is now done once at the end of the consolidated block.

        return value;
    }
}