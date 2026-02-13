package org.grails.orm.hibernate.cfg.domainbinding.binder;

import org.hibernate.MappingException;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.ManyToOne;

import org.grails.datastore.mapping.model.types.Association;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.JoinTable;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.FOREIGN_KEY_SUFFIX;

public class ManyToOneBinder {

    private final PersistentEntityNamingStrategy namingStrategy;
    private final SimpleValueBinder simpleValueBinder;
    private final ManyToOneValuesBinder manyToOneValuesBinder;
    private final CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder;
    private final SimpleValueColumnFetcher simpleValueColumnFetcher;

    public ManyToOneBinder(PersistentEntityNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
        this.simpleValueBinder = new SimpleValueBinder(namingStrategy);
        this.manyToOneValuesBinder = new ManyToOneValuesBinder();
        this.compositeIdentifierToManyToOneBinder = new CompositeIdentifierToManyToOneBinder(namingStrategy);
        this.simpleValueColumnFetcher = new SimpleValueColumnFetcher();
    }

    protected ManyToOneBinder(PersistentEntityNamingStrategy namingStrategy
            , SimpleValueBinder simpleValueBinder
    , ManyToOneValuesBinder manyToOneValuesBinder
    , CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder
    , SimpleValueColumnFetcher simpleValueColumnFetcher) {
        this.namingStrategy = namingStrategy;
        this.simpleValueBinder =simpleValueBinder;
        this.manyToOneValuesBinder = manyToOneValuesBinder;
        this.compositeIdentifierToManyToOneBinder = compositeIdentifierToManyToOneBinder;
        this.simpleValueColumnFetcher = simpleValueColumnFetcher;
    }


    /**
     * Binds a many-to-one relationship to the
     *
     */
    @SuppressWarnings("unchecked")
    public void bindManyToOne(Association property
            , ManyToOne manyToOne
            ,String path) {
        GrailsHibernatePersistentProperty hibernateProperty = (GrailsHibernatePersistentProperty) property;
        manyToOneValuesBinder.bindManyToOneValues(property, manyToOne);
        GrailsHibernatePersistentEntity refDomainClass = (GrailsHibernatePersistentEntity) (property instanceof HibernateManyToManyProperty ? property.getOwner() : property.getAssociatedEntity());
        Mapping mapping = refDomainClass.getMappedForm();

        boolean isComposite = mapping != null && mapping.hasCompositeIdentifier();
        if (isComposite) {
            CompositeIdentity ci = (CompositeIdentity) mapping.getIdentity();
            compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(hibernateProperty, manyToOne, ci, refDomainClass, path);
        }
        else {
            if (property.isCircular() && (property instanceof HibernateManyToManyProperty)) {
                PropertyConfig pc = hibernateProperty.getMappedForm();

                if (mapping != null && pc.getColumns().isEmpty()) {
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
                simpleValueBinder.bindSimpleValue(hibernateProperty, null, manyToOne, path);
            }
            else {
                // bind column
                // set type
                simpleValueBinder.bindSimpleValue(hibernateProperty, null, manyToOne, path);
            }
        }

        PropertyConfig config = hibernateProperty.getMappedForm();
        boolean isOneToOne = property instanceof HibernateOneToOneProperty;
        boolean notComposite = !isComposite;
        if (isOneToOne && notComposite) {
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
