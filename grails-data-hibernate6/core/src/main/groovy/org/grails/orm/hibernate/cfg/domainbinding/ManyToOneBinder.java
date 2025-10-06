package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.ManyToOne;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.ManyToMany;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.JoinTable;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.FOREIGN_KEY_SUFFIX;

public class ManyToOneBinder {

    private final PersistentEntityNamingStrategy namingStrategy;
    private final SimpleValueBinder simpleValueBinder;
    private final PersistentPropertyToPropertyConfig persistentPropertyToPropertyConfig;
    private final ManyToOneValuesBinder manyToOneValuesBinder;
    private final CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder;
    private final SimpleValueColumnFetcher simpleValueColumnFetcher;
    private final HibernateEntityWrapper hibernateEntityWrapper;

    public ManyToOneBinder(PersistentEntityNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
        this.simpleValueBinder = new SimpleValueBinder(namingStrategy);
        this.persistentPropertyToPropertyConfig = new PersistentPropertyToPropertyConfig();
        this.manyToOneValuesBinder = new ManyToOneValuesBinder();
        this.compositeIdentifierToManyToOneBinder = new CompositeIdentifierToManyToOneBinder(namingStrategy);
        this.simpleValueColumnFetcher = new SimpleValueColumnFetcher();
        this.hibernateEntityWrapper = new HibernateEntityWrapper();
    }

    protected  ManyToOneBinder(PersistentEntityNamingStrategy namingStrategy
            , SimpleValueBinder simpleValueBinder
    , PersistentPropertyToPropertyConfig persistentPropertyToPropertyConfig
    , ManyToOneValuesBinder manyToOneValuesBinder
    , CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder
    , SimpleValueColumnFetcher simpleValueColumnFetcher
    , HibernateEntityWrapper hibernateEntityWrapper) {
        this.namingStrategy = namingStrategy;
        this.simpleValueBinder =simpleValueBinder;
        this.persistentPropertyToPropertyConfig = persistentPropertyToPropertyConfig;
        this.manyToOneValuesBinder = manyToOneValuesBinder;
        this.compositeIdentifierToManyToOneBinder = compositeIdentifierToManyToOneBinder;
        this.simpleValueColumnFetcher = simpleValueColumnFetcher;
        this.hibernateEntityWrapper = hibernateEntityWrapper;
    }


    /**
     * Binds a many-to-one relationship to the
     *
     */
    @SuppressWarnings("unchecked")
    public void bindManyToOne(Association property
            , ManyToOne manyToOne
            ,String path) {
        manyToOneValuesBinder.bindManyToOneValues(property, manyToOne);
        PersistentEntity refDomainClass = property instanceof ManyToMany ? property.getOwner() : property.getAssociatedEntity();
        Mapping mapping = hibernateEntityWrapper.getMappedForm(refDomainClass);

        boolean isComposite = mapping.hasCompositeIdentifier();
        if (isComposite) {
            CompositeIdentity ci = (CompositeIdentity) mapping.getIdentity();
            compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(property, manyToOne, ci, refDomainClass, path);
        }
        else {
            if (property.isCircular() && (property instanceof ManyToMany)) {
                PropertyConfig pc = persistentPropertyToPropertyConfig.toPropertyConfig(property);

                if (pc.getColumns().isEmpty()) {
                    mapping.getColumns().put(property.getName(), pc);
                }
                if (!pc.hasJoinKeyMapping()) {
                    JoinTable jt = new JoinTable();
                    final ColumnConfig columnConfig = new ColumnConfig();
                    columnConfig.setName(namingStrategy.resolveColumnName(property.getName()) + FOREIGN_KEY_SUFFIX);
                    jt.setKey(columnConfig);
                    pc.setJoinTable(jt);
                }
                // set type
                simpleValueBinder.bindSimpleValue(property, null, manyToOne, path);
            }
            else {
                // bind column
                // set type
                simpleValueBinder.bindSimpleValue(property, null, manyToOne, path);
            }
        }

        PropertyConfig config = persistentPropertyToPropertyConfig.toPropertyConfig(property);
        if ((property instanceof org.grails.datastore.mapping.model.types.OneToOne) && !isComposite) {
            manyToOne.setAlternateUniqueKey(true);
            Column c = simpleValueColumnFetcher.getColumnForSimpleValue(manyToOne);
            if (c == null) {
                throw new MappingException("There is no column for property [" + property.getName() + "]");
            }
            if (!config.isUniqueWithinGroup()) {
                c.setUnique(config.isUnique());
            }
            else {
                if (property.isBidirectional() && property.getInverseSide().isHasOne()) {
                    c.setUnique(true);
                }
            }
        }
    }
}
