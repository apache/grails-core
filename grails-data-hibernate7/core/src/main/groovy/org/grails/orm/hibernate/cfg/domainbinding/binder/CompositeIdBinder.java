package org.grails.orm.hibernate.cfg.domainbinding.binder;

import java.util.Iterator;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.Value;

import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator;

import jakarta.annotation.Nonnull;

public class CompositeIdBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final ComponentPropertyBinder componentPropertyBinder;
    private final ComponentUpdater componentUpdater;

    public CompositeIdBinder(MetadataBuildingContext metadataBuildingContext, ComponentPropertyBinder componentPropertyBinder, ComponentUpdater componentUpdater) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.componentPropertyBinder = componentPropertyBinder;
        this.componentUpdater = componentUpdater;
    }


    public void bindCompositeId(@Nonnull GrailsHibernatePersistentEntity domainClass, RootClass root,
                                 CompositeIdentity compositeIdentity, @Nonnull InFlightMetadataCollector mappings) {
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

        GrailsHibernatePersistentProperty[] composite;
        if (compositeIdentity != null && compositeIdentity.getPropertyNames() != null) {
            String[] propertyNames = compositeIdentity.getPropertyNames();
            composite = new GrailsHibernatePersistentProperty[propertyNames.length];
            for (int i = 0; i < propertyNames.length; i++) {
                composite[i] = (GrailsHibernatePersistentProperty) domainClass.getPropertyByName(propertyNames[i]);
            }
        } else {
            composite = domainClass.getCompositeIdentity();
        }

        if (composite == null) {
            throw new MappingException("No composite identifier properties found for class [" + domainClass.getName() + "]");
        }

        GrailsHibernatePersistentProperty identifierProp = domainClass.getIdentity();
        for (GrailsHibernatePersistentProperty property : composite) {
            if (property == null) {
                throw new MappingException("Property referenced in composite-id mapping of class [" + domainClass.getName() +
                        "] is not a valid property!");
            }

           componentPropertyBinder.bindComponentProperty(id, identifierProp, property, root, "", root.getTable(), mappings);
        }
    }
}
