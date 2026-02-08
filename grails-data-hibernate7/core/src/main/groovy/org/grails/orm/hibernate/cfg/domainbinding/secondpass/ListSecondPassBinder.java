package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import jakarta.annotation.Nonnull;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.ManyToMany;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.BackticksRemover;
import org.grails.orm.hibernate.cfg.domainbinding.SimpleValueColumnBinder;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Backref;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.IndexBackref;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.OneToMany;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;

import java.util.Map;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.UNDERSCORE;

/**
 * Refactored from CollectionBinder to handle list second pass binding.
 */
public class ListSecondPassBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final CollectionSecondPassBinder collectionSecondPassBinder;
    private final PersistentEntityNamingStrategy namingStrategy;

    public ListSecondPassBinder(MetadataBuildingContext metadataBuildingContext
            , PersistentEntityNamingStrategy namingStrategy, CollectionSecondPassBinder collectionSecondPassBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.collectionSecondPassBinder = collectionSecondPassBinder;
        this.namingStrategy = namingStrategy;
    }

    public void bindListSecondPass(HibernateToManyProperty property, @Nonnull InFlightMetadataCollector mappings,
                                   Map<?, ?> persistentClasses, org.hibernate.mapping.List list, String sessionFactoryBeanName) {

        collectionSecondPassBinder.bindCollectionSecondPass(property, mappings, persistentClasses, list, sessionFactoryBeanName);
        String columnName = property.getIndexColumnName(namingStrategy);
        final boolean isManyToMany = property instanceof ManyToMany;

        if (isManyToMany && !property.isOwningSide()) {
            throw new MappingException("Invalid association [" + property +
                    "]. List collection types only supported on the owning side of a many-to-many relationship.");
        }

        Table collectionTable = list.getCollectionTable();
        SimpleValue iv = new BasicValue(metadataBuildingContext, collectionTable);
        String type = ((GrailsHibernatePersistentProperty) property).getIndexColumnType("integer");
        new SimpleValueColumnBinder().bindSimpleValue(iv, type, columnName, true);
        iv.setTypeName(type);
        list.setIndex(iv);
        list.setBaseIndex(0);
        list.setInverse(false);

        Value v = list.getElement();
        v.createForeignKey();

        if (property.isBidirectional()) {

            String entityName;
            Value element = list.getElement();
            if (element instanceof ManyToOne) {
                ManyToOne manyToOne = (ManyToOne) element;
                entityName = manyToOne.getReferencedEntityName();
            } else {
                entityName = ((OneToMany) element).getReferencedEntityName();
            }

            PersistentClass referenced = mappings.getEntityBinding(entityName);

            boolean compositeIdProperty = property.getInverseSide().isCompositeIdProperty();
            if (!compositeIdProperty) {
                Backref prop = new Backref();
                final PersistentEntity owner = property.getOwner();
                prop.setEntityName(owner.getName());
                String s2 = property.getName();
                prop.setName(UNDERSCORE + new BackticksRemover().apply(owner.getJavaClass().getSimpleName()) + UNDERSCORE + new BackticksRemover().apply(s2) + "Backref");
                prop.setSelectable(false);
                prop.setUpdatable(false);
                if (isManyToMany) {
                    prop.setInsertable(false);
                }
                prop.setCollectionRole(list.getRole());
                prop.setValue(list.getKey());

                DependantValue value = (DependantValue) prop.getValue();
                if (!property.isCircular()) {
                    value.setNullable(false);
                }
                value.setUpdateable(true);
                prop.setOptional(false);

                referenced.addProperty(prop);
            }

            if ((!list.getKey().isNullable() && !list.isInverse()) || compositeIdProperty) {
                IndexBackref ib = new IndexBackref();
                ib.setName(UNDERSCORE + property.getName() + "IndexBackref");
                ib.setUpdatable(false);
                ib.setSelectable(false);
                if (isManyToMany) {
                    ib.setInsertable(false);
                }
                ib.setCollectionRole(list.getRole());
                ib.setEntityName(list.getOwner().getEntityName());
                ib.setValue(list.getIndex());
                referenced.addProperty(ib);
            }
        }
    }
}