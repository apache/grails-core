package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import jakarta.annotation.Nonnull;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.orm.hibernate.cfg.*;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher;
import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.*;
import org.hibernate.mapping.Collection;
import org.hibernate.type.Type;

import java.util.Optional;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.*;

/**
 * Binds a collection with a join table.
 */
public class CollectionWithJoinTableBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final UnidirectionalOneToManyInverseValuesBinder unidirectionalOneToManyInverseValuesBinder;
    private final EnumTypeBinder enumTypeBinder;
    private final CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder;
    private final SimpleValueColumnFetcher simpleValueColumnFetcher;
    private final CollectionForPropertyConfigBinder collectionForPropertyConfigBinder;

    public CollectionWithJoinTableBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            UnidirectionalOneToManyInverseValuesBinder unidirectionalOneToManyInverseValuesBinder,
            EnumTypeBinder enumTypeBinder,
            CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder,
            SimpleValueColumnFetcher simpleValueColumnFetcher,
            CollectionForPropertyConfigBinder collectionForPropertyConfigBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.namingStrategy = namingStrategy;
        this.unidirectionalOneToManyInverseValuesBinder = unidirectionalOneToManyInverseValuesBinder;
        this.enumTypeBinder = enumTypeBinder;
        this.compositeIdentifierToManyToOneBinder = compositeIdentifierToManyToOneBinder;
        this.simpleValueColumnFetcher = simpleValueColumnFetcher;
        this.collectionForPropertyConfigBinder = collectionForPropertyConfigBinder;
    }

    public void bindCollectionWithJoinTable(@Nonnull HibernateToManyProperty property,
                                             @Nonnull InFlightMetadataCollector mappings, @Nonnull Collection collection) {

        collection.setInverse(false);
        SimpleValue element;
        final boolean isBasicCollectionType = property instanceof Basic;
        if (isBasicCollectionType) {
            element = new BasicValue(metadataBuildingContext, collection.getCollectionTable());
        }
        else {
            // for a normal unidirectional one-to-many we use a join column
            element = new ManyToOne(metadataBuildingContext, collection.getCollectionTable());
            unidirectionalOneToManyInverseValuesBinder.bindUnidirectionalOneToManyInverseValues(property, (ManyToOne) element);
        }

        String columnName;

        var joinColumnMappingOptional = Optional.ofNullable(property.getMappedForm()).map(PropertyConfig::getJoinTableColumnConfig);
        if (isBasicCollectionType) {
            final Class<?> referencedType = ((Basic) property).getComponentType();
            String className = referencedType.getName();
            final boolean isEnum = referencedType.isEnum();
            if (joinColumnMappingOptional.isPresent()) {
                columnName = joinColumnMappingOptional.get().getName();
            }
            else {
                var clazz = namingStrategy.resolveColumnName(className);
                var prop = namingStrategy.resolveTableName(property.getName());
                columnName = isEnum ? clazz : new BackticksRemover().apply(prop) + UNDERSCORE + new BackticksRemover().apply(clazz);
            }

            if (isEnum) {
                enumTypeBinder.bindEnumType(property, referencedType, element, columnName);
            }
            else {

                String typeName = property.getTypeName(referencedType);
                if (typeName == null) {
                    Type type = mappings.getTypeConfiguration().getBasicTypeRegistry().getRegisteredType(className);
                    if (type != null) {
                        typeName = type.getName();
                    }
                }
                if (typeName == null) {
                    String domainName = property.getHibernateOwner().getName();
                    throw new MappingException("Missing type or column for column["+columnName+"] on domain["+domainName+"] referencing["+className+"]");
                }

                new SimpleValueColumnBinder().bindSimpleValue(element, typeName, columnName, true);
                if (joinColumnMappingOptional.isPresent()) {
                    Column column = simpleValueColumnFetcher.getColumnForSimpleValue(element);
                    ColumnConfig columnConfig = joinColumnMappingOptional.get();
                    final PropertyConfig mappedForm = property.getMappedForm();
                    new ColumnConfigToColumnBinder().bindColumnConfigToColumn(column, columnConfig, mappedForm);
                }
            }
        } else {
            final GrailsHibernatePersistentEntity domainClass = property.getHibernateAssociatedEntity();

            Mapping m = null;
            if (domainClass != null) {
                m = domainClass.getMappedForm();
            }
            if (m != null && m.hasCompositeIdentifier()) {
                CompositeIdentity ci = (CompositeIdentity) m.getIdentity();
                compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(property, element, ci, domainClass, EMPTY_PATH);
            }
            else {
                if (joinColumnMappingOptional.isPresent()) {
                    columnName = joinColumnMappingOptional.get().getName();
                }
                else {
                    var decapitalize = domainClass.getHibernateRootEntity().getJavaClass().getSimpleName();
                    columnName = namingStrategy.resolveColumnName(decapitalize) + FOREIGN_KEY_SUFFIX;
                }

                new SimpleValueColumnBinder().bindSimpleValue(element, "long", columnName, true);
            }
        }

        collection.setElement(element);

        collectionForPropertyConfigBinder.bindCollectionForPropertyConfig(collection, property);
    }
}
