package org.grails.orm.hibernate.cfg.domainbinding.binder;

import jakarta.annotation.Nonnull;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.UnionSubclass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Binds a union sub-class mapping using table-per-concrete-class
 *
 * @since 7.0
 */
public class UnionSubclassBinder {

    private static final Logger LOG = LoggerFactory.getLogger(UnionSubclassBinder.class);

    private final MetadataBuildingContext metadataBuildingContext;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final ClassBinder classBinder;

    public UnionSubclassBinder(MetadataBuildingContext metadataBuildingContext, PersistentEntityNamingStrategy namingStrategy, ClassBinder classBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.namingStrategy = namingStrategy;
        this.classBinder = classBinder;
    }

    /**
     * Binds a union sub-class mapping using table-per-concrete-class
     *
     * @param subClass       The Grails sub class
     * @param unionSubclass  The Hibernate UnionSubclass object
     * @param mappings       The mappings Object
     */
    public void bindUnionSubclass(@Nonnull GrailsHibernatePersistentEntity subClass, UnionSubclass unionSubclass,
                                   @Nonnull InFlightMetadataCollector mappings) throws MappingException {
        classBinder.bindClass(subClass, unionSubclass, mappings);

        String schema = subClass.getSchema(mappings);
        String catalog = subClass.getCatalog(mappings);

        Table denormalizedSuperTable = unionSubclass.getSuperclass().getTable();
        Table mytable = mappings.addDenormalizedTable(
                schema,
                catalog,
                subClass.getTableName(namingStrategy),
                Boolean.TRUE.equals(unionSubclass.isAbstract()),
                null,
                denormalizedSuperTable, metadataBuildingContext
        );
        unionSubclass.setTable( mytable );
        unionSubclass.setClassName(subClass.getName());

        if (LOG.isInfoEnabled()) {
            LOG.info("Mapping union-subclass: " + unionSubclass.getEntityName() +
                    " -> " + unionSubclass.getTable().getName());
        }
    }
}
