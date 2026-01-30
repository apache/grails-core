package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.RootClass;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;

import jakarta.annotation.Nonnull;

public class CompositeIdBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final ComponentPropertyBinder componentPropertyBinder;

    public CompositeIdBinder(MetadataBuildingContext metadataBuildingContext, ComponentPropertyBinder componentPropertyBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.componentPropertyBinder = componentPropertyBinder;
    }

    protected CompositeIdBinder() {
        this.metadataBuildingContext = null;
        this.componentPropertyBinder = null;
    }

    public void bindCompositeId(@Nonnull PersistentEntity domainClass, RootClass root,
                                 CompositeIdentity compositeIdentity, @Nonnull InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        bindCompositeId(domainClass, (GrailsHibernatePersistentEntity) domainClass, root, compositeIdentity, mappings, sessionFactoryBeanName);
    }

    public void bindCompositeId(@Nonnull PersistentEntity domainClass, @Nonnull GrailsHibernatePersistentEntity hibernatePersistentEntity, RootClass root,
                                 CompositeIdentity compositeIdentity, @Nonnull InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        Component id = new Component(metadataBuildingContext, root);
        id.setNullValue("undefined");
        root.setIdentifier(id);
        root.setIdentifierMapper(id);
        root.setEmbeddedIdentifier(true);
        id.setComponentClassName(domainClass.getName());
        id.setKey(true);
        id.setEmbedded(true);

        String path = GrailsHibernateUtil.qualify(root.getEntityName(), "id");

        id.setRoleName(path);

        PersistentProperty[] composite;
        if (compositeIdentity != null && compositeIdentity.getPropertyNames() != null) {
            String[] propertyNames = compositeIdentity.getPropertyNames();
            composite = new PersistentProperty[propertyNames.length];
            for (int i = 0; i < propertyNames.length; i++) {
                composite[i] = domainClass.getPropertyByName(propertyNames[i]);
            }
        } else {
            composite = hibernatePersistentEntity.getCompositeIdentity();
        }

        if (composite == null) {
            throw new MappingException("No composite identifier properties found for class [" + domainClass.getName() + "]");
        }

        PersistentProperty identifierProp = hibernatePersistentEntity.getIdentity();
        for (PersistentProperty property : composite) {
            if (property == null) {
                throw new MappingException("Property referenced in composite-id mapping of class [" + domainClass.getName() +
                        "] is not a valid property!");
            }

            componentPropertyBinder.bindComponentProperty(id, identifierProp, property, root, "", root.getTable(), mappings, sessionFactoryBeanName);
        }
    }
}
