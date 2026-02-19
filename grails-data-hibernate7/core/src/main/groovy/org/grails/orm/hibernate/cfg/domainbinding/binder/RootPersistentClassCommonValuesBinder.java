package org.grails.orm.hibernate.cfg.domainbinding.binder;

import jakarta.annotation.Nonnull;
import org.grails.orm.hibernate.cfg.CacheConfig;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;

import static java.util.Optional.ofNullable;

public class RootPersistentClassCommonValuesBinder {
    public static final Logger LOG = LoggerFactory.getLogger(RootPersistentClassCommonValuesBinder.class);

    private final MetadataBuildingContext metadataBuildingContext;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final IdentityBinder identityBinder;
    private final VersionBinder versionBinder;
    private final ClassBinder classBinder;
    private final ClassPropertiesBinder classPropertiesBinder;

    public RootPersistentClassCommonValuesBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            IdentityBinder identityBinder,
            VersionBinder versionBinder,
            ClassBinder classBinder,
            ClassPropertiesBinder classPropertiesBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.namingStrategy = namingStrategy;
        this.identityBinder = identityBinder;
        this.versionBinder = versionBinder;
        this.classBinder = classBinder;
        this.classPropertiesBinder = classPropertiesBinder;
    }

    public RootClass bindRootPersistentClassCommonValues(@Nonnull GrailsHibernatePersistentEntity domainClass,
                                                       @Nonnull Collection<GrailsHibernatePersistentEntity> children,
                                                       @Nonnull InFlightMetadataCollector mappings) {

        RootClass root = new RootClass(this.metadataBuildingContext);
        root.setAbstract(domainClass.isAbstract());
        classBinder.bindClass(domainClass, root, mappings);

        // get the schema and catalog names from the configuration
        Mapping gormMapping = domainClass.getMappedForm();

        domainClass.configureDerivedProperties();
        CacheConfig cc = gormMapping.getCache();
        if (cc != null && cc.getEnabled()) {
            root.setCacheConcurrencyStrategy(cc.getUsage());
            root.setCached(true);
            if ("read-only".equals(cc.getUsage())) {
                root.setMutable(false);
            }
            root.setLazyPropertiesCacheable(!"non-lazy".equals(cc.getInclude()));
        }
        root.setBatchSize(ofNullable(gormMapping.getBatchSize()).orElse(0));
        root.setDynamicUpdate(gormMapping.getDynamicUpdate());
        root.setDynamicInsert(gormMapping.getDynamicInsert());


        var schema = domainClass.getSchema(mappings);

        var catalog = domainClass.getCatalog(mappings);


        // create the table
        var table = mappings.addTable(schema
                , catalog
                , domainClass.getTableName(namingStrategy)
                , null
                , domainClass.isTableAbstract()
                , metadataBuildingContext
        );
        root.setTable(table);
        if (LOG.isDebugEnabled()) {
            LOG.debug("[GrailsDomainBinder] Mapping Grails domain class: " + domainClass.getName() + " -> " + root.getTable().getName());
        }

        identityBinder.bindIdentity(domainClass, root, mappings, gormMapping);
        versionBinder.bindVersion(domainClass.getVersion(), root);
        root.createPrimaryKey();
        classPropertiesBinder.bindClassProperties(domainClass, root, mappings);

        return root;
    }
}
