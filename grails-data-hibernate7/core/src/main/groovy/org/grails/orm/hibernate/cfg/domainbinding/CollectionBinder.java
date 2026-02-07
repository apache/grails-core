package org.grails.orm.hibernate.cfg.domainbinding;

import jakarta.annotation.Nonnull;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.ManyToMany;
import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.JoinTable;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.GrailsCollectionSecondPass;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.ListSecondPass;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.MapSecondPass;
import org.hibernate.FetchMode;
import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.IndexedCollection;
import org.hibernate.mapping.OneToMany;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;
import org.hibernate.type.StandardBasicTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.*;

/**
 * Handles the binding of collections to the Hibernate runtime meta model.
 */
public class CollectionBinder {

    private static final Logger LOG = LoggerFactory.getLogger(CollectionBinder.class);

    private final MetadataBuildingContext metadataBuildingContext;
    public final GrailsDomainBinder grailsDomainBinder;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final ListSecondPassBinder listSecondPassBinder;
    private final CollectionSecondPassBinder collectionSecondPassBinder;
    private final MapSecondPassBinder mapSecondPassBinder;

    public CollectionBinder(MetadataBuildingContext metadataBuildingContext, GrailsDomainBinder grailsDomainBinder, PersistentEntityNamingStrategy namingStrategy) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.grailsDomainBinder = grailsDomainBinder;
        this.namingStrategy = namingStrategy;
        this.listSecondPassBinder = new ListSecondPassBinder(metadataBuildingContext, this);
        this.collectionSecondPassBinder = new CollectionSecondPassBinder(metadataBuildingContext, namingStrategy);
        this.mapSecondPassBinder = new MapSecondPassBinder(metadataBuildingContext, namingStrategy, this);
    }

    /**
     * First pass to bind collection to Hibernate metamodel, sets up second pass
     *
     * @param property   The GrailsDomainClassProperty instance
     * @param collection The collection
     * @param owner      The owning persistent class
     * @param mappings   The Hibernate mappings instance
     * @param path       The property path
     */
    public void bindCollection(HibernateToManyProperty property, Collection collection,
                               PersistentClass owner, @Nonnull InFlightMetadataCollector mappings, String path, String sessionFactoryBeanName) {

        // set role
        String propertyName = getNameForPropertyAndPath(property, path);
        collection.setRole(GrailsHibernateUtil.qualify(property.getOwner().getName(), propertyName));

        PropertyConfig pc = property.getMappedForm();
        // configure eager fetching
        final FetchMode fetchMode = pc.getFetchMode();
        if (fetchMode == FetchMode.JOIN) {
            collection.setFetchMode(FetchMode.JOIN);
        }
        else if (pc.getFetchMode() != null) {
            collection.setFetchMode(pc.getFetchMode());
        }
        else {
            collection.setFetchMode(FetchMode.DEFAULT);
        }

        if (pc.getCascade() != null) {
            collection.setOrphanDelete(pc.getCascade().equals(CascadeBehavior.ALL_DELETE_ORPHAN.getValue()));
        }
        // if it's a one-to-many mapping
        if (property.shouldBindWithForeignKey()) {
            OneToMany oneToMany = new OneToMany(metadataBuildingContext, collection.getOwner());
            collection.setElement(oneToMany);
            bindOneToMany((org.grails.datastore.mapping.model.types.OneToMany) property, oneToMany, mappings);
        } else {
            bindCollectionTable(property, mappings, collection, owner.getTable());

            if (!property.isOwningSide()) {
                collection.setInverse(true);
            }
        }

        if (pc.getBatchSize() != null) {
            collection.setBatchSize(pc.getBatchSize());
        }

        // set up second pass
        if (collection instanceof org.hibernate.mapping.Set) {
            mappings.addSecondPass(new GrailsCollectionSecondPass(grailsDomainBinder, property, mappings, collection, sessionFactoryBeanName));
        }
        else if (collection instanceof org.hibernate.mapping.List) {
            mappings.addSecondPass(new ListSecondPass(grailsDomainBinder, property, mappings, collection, sessionFactoryBeanName));
        }
        else if (collection instanceof org.hibernate.mapping.Map) {
            mappings.addSecondPass(new MapSecondPass(grailsDomainBinder, property, mappings, collection, sessionFactoryBeanName));
        }
        else { // Collection -> Bag
            mappings.addSecondPass(new GrailsCollectionSecondPass(grailsDomainBinder, property, mappings, collection, sessionFactoryBeanName));
        }
    }

    public void bindCollectionSecondPass(HibernateToManyProperty property, @Nonnull InFlightMetadataCollector mappings,
                                         Map<?, ?> persistentClasses, Collection collection, String sessionFactoryBeanName) {
        collectionSecondPassBinder.bindCollectionSecondPass(property, mappings, persistentClasses, collection, sessionFactoryBeanName);
    }

    public void bindListSecondPass(HibernateToManyProperty property, @Nonnull InFlightMetadataCollector mappings,
                                   Map<?, ?> persistentClasses, org.hibernate.mapping.List list, String sessionFactoryBeanName) {
        listSecondPassBinder.bindListSecondPass(property, mappings, persistentClasses, list, sessionFactoryBeanName);
    }

    public void bindMapSecondPass(HibernateToManyProperty property, @Nonnull InFlightMetadataCollector mappings,
                                  Map<?, ?> persistentClasses, org.hibernate.mapping.Map map, String sessionFactoryBeanName) {
        mapSecondPassBinder.bindMapSecondPass(property, mappings, persistentClasses, map, sessionFactoryBeanName);
    }

    private String getNameForPropertyAndPath(PersistentProperty property, String path) {
        if (GrailsHibernateUtil.isNotEmpty(path)) {
            return GrailsHibernateUtil.qualify(path, property.getName());
        }
        return property.getName();
    }

    private void bindOneToMany(org.grails.datastore.mapping.model.types.OneToMany currentGrailsProp, OneToMany one, @Nonnull InFlightMetadataCollector mappings) {
        one.setReferencedEntityName(currentGrailsProp.getAssociatedEntity().getName());
        one.setIgnoreNotFound(true);
    }

    private void bindCollectionTable(HibernateToManyProperty property, @Nonnull InFlightMetadataCollector mappings,
                                     Collection collection, Table ownerTable) {

        String owningTableSchema = ownerTable.getSchema();
        PropertyConfig config = property.getMappedForm();
        JoinTable jt = config.getJoinTable();

        String s = new TableForManyCalculator(namingStrategy).calculateTableForMany(property);
        String tableName = (jt != null && jt.getName() != null ? jt.getName() : namingStrategy.resolveTableName(s));

        String schemaName = new NamespaceNameExtractor().getSchemaName(mappings);
        String catalogName = new NamespaceNameExtractor().getCatalogName(mappings);
        if(jt != null) {
            if(jt.getSchema() != null) {
                schemaName = jt.getSchema();
            }
            if(jt.getCatalog() != null) {
                catalogName = jt.getCatalog();
            }
        }

        if(schemaName == null && owningTableSchema != null) {
            schemaName = owningTableSchema;
        }

        collection.setCollectionTable(mappings.addTable(
                schemaName, catalogName,
                tableName, null, false, metadataBuildingContext));
    }

    public String getIndexColumnName(PersistentProperty property) {
        PropertyConfig pc = property instanceof GrailsHibernatePersistentProperty ghpp ? ghpp.getMappedForm() : new PropertyConfig();
        if (pc.getIndexColumn() != null && pc.getIndexColumn().getColumn() != null) {
            return pc.getIndexColumn().getColumn();
        }
        return namingStrategy.resolveColumnName(property.getName()) + UNDERSCORE + IndexedCollection.DEFAULT_INDEX_COLUMN_NAME;
    }

    public String getMultiTenantFilterCondition(PersistentEntity referenced) {
        return collectionSecondPassBinder.getMultiTenantFilterCondition(referenced);
    }
}
