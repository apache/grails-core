package org.grails.orm.hibernate.cfg.domainbinding.binder;

import jakarta.annotation.Nonnull;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator;
import org.grails.orm.hibernate.cfg.domainbinding.util.TableNameFetcher;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.JoinedSubclass;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Binds a joined sub-class mapping using table-per-subclass
 *
 * @since 7.0
 */
public class JoinedSubClassBinder {

    private static final Logger LOG = LoggerFactory.getLogger(JoinedSubClassBinder.class);
    private static final String EMPTY_PATH = "";

    private final MetadataBuildingContext metadataBuildingContext;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final SimpleValueColumnBinder simpleValueColumnBinder;
    private final ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher;
    private final ClassBinder classBinder;

    public JoinedSubClassBinder(MetadataBuildingContext metadataBuildingContext, PersistentEntityNamingStrategy namingStrategy, SimpleValueColumnBinder simpleValueColumnBinder, ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher, ClassBinder classBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.namingStrategy = namingStrategy;
        this.simpleValueColumnBinder = simpleValueColumnBinder;
        this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher;
        this.classBinder = classBinder;
    }

    /**
     * Binds a joined sub-class mapping using table-per-subclass
     *
     * @param sub                    The Grails sub class
     * @param joinedSubclass         The Hibernate Subclass object
     * @param mappings               The mappings Object
     */
    public void bindJoinedSubClass(GrailsHibernatePersistentEntity sub,
                                   JoinedSubclass joinedSubclass,
                                    InFlightMetadataCollector mappings) {
        classBinder.bindClass(sub, joinedSubclass, mappings);

        String schemaName = sub.getSchema(mappings);
        String catalogName = sub.getCatalog(mappings);

        Table mytable = mappings.addTable(
                schemaName, catalogName,
                getJoinedSubClassTableName(sub, joinedSubclass, null, mappings),
                null, false, metadataBuildingContext);

        joinedSubclass.setTable(mytable);
        if (LOG.isInfoEnabled()) {
            LOG.info("Mapping joined-subclass: " + joinedSubclass.getEntityName() +
                    " -> " + joinedSubclass.getTable().getName());
        }

        SimpleValue key = new DependantValue(metadataBuildingContext, mytable, joinedSubclass.getIdentifier());
        joinedSubclass.setKey(key);
        var identifier = sub.getIdentity();
        String columnName = columnNameForPropertyAndPathFetcher.getColumnNameForPropertyAndPath(identifier, EMPTY_PATH, null);
        simpleValueColumnBinder.bindSimpleValue(key, identifier.getType().getName(), columnName, false);

        joinedSubclass.createPrimaryKey();
        joinedSubclass.createForeignKey();
    }

    private String getJoinedSubClassTableName(
            GrailsHibernatePersistentEntity sub, PersistentClass model, Table denormalizedSuperTable,
            InFlightMetadataCollector mappings) {

        String logicalTableName = GrailsHibernateUtil.unqualify(model.getEntityName());
        String physicalTableName = new TableNameFetcher(namingStrategy).getTableName(sub);

        String schemaName = sub.getSchema(mappings);
        String catalogName = sub.getCatalog(mappings);

        mappings.addTableNameBinding(schemaName, catalogName, logicalTableName, physicalTableName, denormalizedSuperTable);
        return physicalTableName;
    }
}
