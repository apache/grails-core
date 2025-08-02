package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.hibernate.MappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

import static org.grails.orm.hibernate.cfg.domainbinding.CascadeBehavior.*;


public class CascadeBehaviorFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(CascadeBehaviorFetcher.class);

    public String getCascadeBehaviour(PersistentProperty<?> grailsProperty) {
        var cascadeStrategy = NONE;
        if (grailsProperty instanceof Association association) {
            cascadeStrategy = getDefinedBehavior(grailsProperty)
                    .orElse(getImpliedBehavior(association));
            new LogCascadeMapping(LOG).logCascadeMapping(association, cascadeStrategy);
        }
        return cascadeStrategy.getValue();
    }

    private Optional<CascadeBehavior> getDefinedBehavior(PersistentProperty<?> grailsProperty) {
        return Optional.ofNullable(new PersistentPropertyToPropertyConfig().apply(grailsProperty))
                .map(PropertyConfig::getCascade)
                .map(CascadeBehavior::fromString);
    }

    private CascadeBehavior getImpliedBehavior( Association association) {
        PersistentEntity referenced = association.getAssociatedEntity();
        PersistentEntity domainClass = association.getOwner();

        if (association.isHasOne()) {
            return ALL;
        }
        else if (association.isOneToOne()) {
            if (referenced != null && association.isOwningSide()) {
                return ALL;
            } else {
                return SAVE_UPDATE;
            }
        }
        else if (association.isOneToMany()) {
            if (referenced != null && association.isOwningSide()) {
                return ALL;
            } else {
                return SAVE_UPDATE;
            }
        }  else if (association.isManyToMany()) {
            if ((referenced != null && referenced.isOwningEntity(domainClass)) || association.isCircular()) {
                return SAVE_UPDATE;
            }
            return NONE;
        }
        else if (association.isManyToOne()) {
            if (referenced != null && referenced.isOwningEntity(domainClass) && !association.isCircular()) {
                return ALL;
            }
            else if (new HibernateEntityWrapper(domainClass).getMappedForm().isCompositeIdProperty(association)) {
                return ALL;
            }
            return NONE;
        }
        else if (association instanceof Basic) {
            return ALL;
        }
        else if (Map.class.isAssignableFrom(association.getType())) {
            if (referenced != null && referenced.isOwningEntity(domainClass)) {
                return ALL;
            }
            else {
                return SAVE_UPDATE;
            }
        }
        return NONE;
    }


}