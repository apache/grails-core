package org.grails.orm.hibernate.cfg.domainbinding;

import org.codehaus.groovy.transform.trait.Traits;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.reflect.EntityReflector;
import org.grails.orm.hibernate.access.TraitPropertyAccessStrategy;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.PropertyConfig;

import org.hibernate.boot.spi.AccessType;
import org.hibernate.mapping.Property;

import java.util.Optional;


public class PropertyBinder {

    private final CascadeBehaviorFetcher cascadeBehaviorFetcher;
    private final BidirectionalManyToOneWithListMapping bidirectionalManyToOneWithListMapping;

    public PropertyBinder(
            CascadeBehaviorFetcher cascadeBehaviorFetcher
            , BidirectionalManyToOneWithListMapping bidirectionalManyToOneWithListMapping) {
        this.cascadeBehaviorFetcher = cascadeBehaviorFetcher;
        this.bidirectionalManyToOneWithListMapping = bidirectionalManyToOneWithListMapping;
    }

    public PropertyBinder() {
        this(new CascadeBehaviorFetcher()
                , new BidirectionalManyToOneWithListMapping());
    }

    /**
     * Binds a property to Hibernate runtime meta model. Deals with cascade strategy based on the Grails domain model
     *
     * @param persistentProperty The grails property instance
     * @param prop           The Hibernate property
     */
    public void bindProperty(PersistentProperty<?> persistentProperty, Property prop) {
        // set the property name
        prop.setName(persistentProperty.getName());
        PropertyConfig config = persistentProperty instanceof GrailsHibernatePersistentProperty ghpp ? ghpp.getMappedForm() : null;
        if (config == null) {
            config = new PropertyConfig();
        }

        if (bidirectionalManyToOneWithListMapping.isBidirectionalManyToOneWithListMapping(persistentProperty, prop)) {
            prop.setInsertable(false);
            prop.setUpdateable(false);
        } else {
            prop.setInsertable(config.getInsertable());
            prop.setUpdateable(config.getUpdatable());
        }

        var accessType = AccessType.getAccessStrategy(config.getAccessType());

        var accessorName = accessType == AccessType.FIELD ?
                Optional.ofNullable(persistentProperty.getReader())
                        .map(EntityReflector.PropertyReader::getter)
                        .map(getter -> getter.getAnnotation(Traits.Implemented.class))
                        .map(annotation -> TraitPropertyAccessStrategy.class.getName())
                        .orElse(accessType.getType())
                : accessType.getType();
        prop.setPropertyAccessorName(accessorName);


        prop.setOptional(persistentProperty.isNullable());
        if (persistentProperty instanceof Association<?> association) {
            prop.setCascade(cascadeBehaviorFetcher.getCascadeBehaviour(association));
        }


        // lazy to true

        if (persistentProperty.isLazyAble()) {
            final boolean isLazy = Optional.ofNullable(config.getLazy())
                    .orElse( persistentProperty instanceof Association);
            prop.setLazy(isLazy);
        }
    }
}
