package org.grails.orm.hibernate.cfg.domainbinding.binder;

import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.RootClass;

import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;

import jakarta.annotation.Nonnull;

public class CompositeIdBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final ComponentBinder componentBinder;
    private final ComponentUpdater componentUpdater;
    private final GrailsPropertyBinder grailsPropertyBinder;

    public CompositeIdBinder(MetadataBuildingContext metadataBuildingContext, ComponentBinder componentBinder, ComponentUpdater componentUpdater, GrailsPropertyBinder grailsPropertyBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.componentBinder = componentBinder;
        this.componentUpdater = componentUpdater;
        this.grailsPropertyBinder = grailsPropertyBinder;
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

        if (compositeIdentity == null) {
            compositeIdentity = new CompositeIdentity();
        }
        GrailsHibernatePersistentProperty[] composite = compositeIdentity.getHibernateProperties(domainClass);

        GrailsHibernatePersistentProperty identifierProp = domainClass.getIdentity();
        for (GrailsHibernatePersistentProperty property : composite) {
           var value = grailsPropertyBinder.bindProperty(root, root.getTable(), "", identifierProp, property, mappings);
           componentUpdater.updateComponent(id, identifierProp, property, value);
        }
    }
}
