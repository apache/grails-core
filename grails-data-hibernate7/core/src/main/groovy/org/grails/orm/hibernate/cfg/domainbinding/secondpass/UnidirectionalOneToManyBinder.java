package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import jakarta.annotation.Nonnull;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Backref;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.OneToMany;
import org.hibernate.mapping.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.UNDERSCORE;

/**
 * Binds unidirectional one-to-many associations.
 */
public class UnidirectionalOneToManyBinder {

    private static final Logger LOG = LoggerFactory.getLogger(UnidirectionalOneToManyBinder.class);
    private final CollectionWithJoinTableBinder collectionWithJoinTableBinder;
    private final BackticksRemover backticksRemover = new BackticksRemover();

    public UnidirectionalOneToManyBinder(CollectionWithJoinTableBinder collectionWithJoinTableBinder) {
        this.collectionWithJoinTableBinder = collectionWithJoinTableBinder;
    }

    public void bind(@Nonnull HibernateOneToManyProperty property,
                     @Nonnull InFlightMetadataCollector mappings,
                     @Nonnull Collection collection) {
        if (!property.shouldBindWithForeignKey()) {
            collectionWithJoinTableBinder.bindCollectionWithJoinTable(property, mappings, collection);
        } else {
            bindUnidirectionalOneToMany(property, mappings, collection);
        }
    }

    private void bindUnidirectionalOneToMany(@Nonnull HibernateOneToManyProperty property,
                                              @Nonnull InFlightMetadataCollector mappings,
                                              @Nonnull Collection collection) {
        Value element = collection.getElement();
        element.createForeignKey();

        String entityName = (element instanceof ManyToOne manyToOne)
                ? manyToOne.getReferencedEntityName()
                : ((OneToMany) element).getReferencedEntityName();

        collection.setInverse(false);

        mappings.getEntityBinding(entityName).addProperty(createBackref(property, collection));
    }

    private Backref createBackref(HibernateOneToManyProperty property, Collection collection) {
        GrailsHibernatePersistentEntity owner = (GrailsHibernatePersistentEntity) property.getOwner();
        Backref backref = new Backref();
        backref.setEntityName(owner.getName());
        backref.setName(UNDERSCORE + backticksRemover.apply(owner.getJavaClass().getSimpleName()) + UNDERSCORE + backticksRemover.apply(property.getName()) + "Backref");
        backref.setUpdatable(false);
        backref.setInsertable(true);
        backref.setCollectionRole(collection.getRole());
        backref.setValue(collection.getKey());
        backref.setOptional(true);
        return backref;
    }
}
