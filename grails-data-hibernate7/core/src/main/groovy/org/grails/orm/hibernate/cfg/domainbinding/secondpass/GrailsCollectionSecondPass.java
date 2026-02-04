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

import org.grails.datastore.mapping.model.types.ToMany;
import org.grails.orm.hibernate.cfg.GrailsDomainBinder;

/**
 * Second pass class for grails relationships. This is required as all
 * persistent classes need to be loaded in the first pass and then relationships
 * established in the second pass compile
 *
 * @author Graeme
 */
public class GrailsCollectionSecondPass implements org.hibernate.boot.spi.SecondPass {

    private static final long serialVersionUID = -5540526942092611348L;

    protected final GrailsDomainBinder grailsDomainBinder;
    ToMany property;
    @Nonnull
    InFlightMetadataCollector mappings;
    Collection collection;
    String sessionFactoryBeanName;

    public GrailsCollectionSecondPass(GrailsDomainBinder grailsDomainBinder, ToMany property, @Nonnull InFlightMetadataCollector mappings,
                                      Collection coll, String sessionFactoryBeanName) {
        this.grailsDomainBinder = grailsDomainBinder;
        this.property = property;
        this.mappings = mappings;
        this.collection = coll;
        this.sessionFactoryBeanName = sessionFactoryBeanName;
    }

    public void doSecondPass(Map<?, ?> persistentClasses, Map<?, ?> inheritedMetas) throws MappingException {
        grailsDomainBinder.bindCollectionSecondPass(property, mappings, persistentClasses, collection, sessionFactoryBeanName);
        createCollectionKeys();
    }

    private void createCollectionKeys() {
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

    private String columns(Value val) {
        StringBuilder columns = new StringBuilder();
        Iterator<?> iter = val.getColumns().iterator();
        while (iter.hasNext()) {
            columns.append(((Selectable) iter.next()).getText());
            if (iter.hasNext()) columns.append(", ");
        }
        return columns.toString();
    }

    @SuppressWarnings("rawtypes")
    public void doSecondPass(Map persistentClasses) throws MappingException {
        grailsDomainBinder.bindCollectionSecondPass(property, mappings, persistentClasses, collection, sessionFactoryBeanName);
        createCollectionKeys();
    }
}
