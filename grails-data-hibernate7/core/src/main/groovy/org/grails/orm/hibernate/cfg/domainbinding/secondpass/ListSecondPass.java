package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import java.util.Iterator;
import java.util.Map;

import jakarta.annotation.Nonnull;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.IndexedCollection;
import org.hibernate.mapping.OneToMany;
import org.hibernate.mapping.Selectable;
import org.hibernate.mapping.Value;

import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
import org.grails.orm.hibernate.cfg.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.CollectionBinder;
import org.grails.orm.hibernate.cfg.domainbinding.ListSecondPassBinder;

public class ListSecondPass implements org.hibernate.boot.spi.SecondPass {
    private static final long serialVersionUID = -3024674993774205193L;

    protected final GrailsDomainBinder grailsDomainBinder;
    protected final CollectionBinder collectionBinder;
    private final ListSecondPassBinder listSecondPassBinder;
    protected final HibernateToManyProperty property;
    protected final @Nonnull InFlightMetadataCollector mappings;
    protected final Collection collection;
    protected final String sessionFactoryBeanName;

    public ListSecondPass(GrailsDomainBinder grailsDomainBinder, CollectionBinder collectionBinder, ListSecondPassBinder listSecondPassBinder, HibernateToManyProperty property, @Nonnull InFlightMetadataCollector mappings,
                          Collection coll, String sessionFactoryBeanName) {
        this.grailsDomainBinder = grailsDomainBinder;
        this.collectionBinder = collectionBinder;
        this.listSecondPassBinder = listSecondPassBinder;
        this.property = property;
        this.mappings = mappings;
        this.collection = coll;
        this.sessionFactoryBeanName = sessionFactoryBeanName;
    }


    @SuppressWarnings("rawtypes")
    @Override
    public void doSecondPass(Map persistentClasses) throws MappingException {
        collectionBinder.bindCollectionSecondPass(property, mappings, persistentClasses, collection, sessionFactoryBeanName);
        listSecondPassBinder.bindListSecondPass(property, mappings, persistentClasses,
                (org.hibernate.mapping.List) collection, sessionFactoryBeanName);
        createCollectionKeys();
    }

    protected void createCollectionKeys() {
        collection.createAllKeys();

        if (GrailsDomainBinder.LOG.isDebugEnabled()) {
            String msg = "Mapped collection key: " + columns(collection.getKey());
            if (collection.isIndexed())
                msg += ", index: " + columns(((IndexedCollection) collection).getIndex());
            if (collection.isOneToMany()) {
                msg += ", one-to-many: "
                        + ((OneToMany) collection.getElement()).getReferencedEntityName();
            } else {
                msg += ", element: " + columns(collection.getElement());
            }
            GrailsDomainBinder.LOG.debug(msg);
        }
    }

    protected String columns(Value val) {
        StringBuilder columns = new StringBuilder();
        Iterator<?> iter = val.getColumns().iterator();
        while (iter.hasNext()) {
            columns.append(((Selectable) iter.next()).getText());
            if (iter.hasNext()) columns.append(", ");
        }
        return columns.toString();
    }
}

