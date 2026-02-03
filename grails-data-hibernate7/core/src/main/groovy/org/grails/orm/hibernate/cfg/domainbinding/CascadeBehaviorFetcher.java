package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.hibernate.MappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

import static org.grails.orm.hibernate.cfg.domainbinding.CascadeBehavior.*;


public class CascadeBehaviorFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(CascadeBehaviorFetcher.class);

    private final LogCascadeMapping logCascadeMapping;

    public CascadeBehaviorFetcher(LogCascadeMapping logCascadeMapping) {
        this.logCascadeMapping = logCascadeMapping;
    }

    public CascadeBehaviorFetcher() {
        this(new LogCascadeMapping(LOG));
    }

    public String getCascadeBehaviour(Association<?> association) {
        var cascadeStrategy = getDefinedBehavior(association).orElse(getImpliedBehavior(association));
        logCascadeMapping.logCascadeMapping(association, cascadeStrategy);
        return cascadeStrategy.getValue();
    }

    private Optional<CascadeBehavior> getDefinedBehavior(PersistentProperty<?> grailsProperty) {
        return Optional.ofNullable(((GrailsHibernatePersistentProperty) grailsProperty).getMappedForm())
                .map(PropertyConfig::getCascade)
                .map(CascadeBehavior::fromString);
    }

    private CascadeBehavior getImpliedBehavior( Association<?> association) {
        if(association.getAssociatedEntity() == null) {
            //NEW BEHAVIOR, FAIL-FAST
            throw new MappingException("Relationship " + association + " has no associated entity");
        }
        if (association instanceof Embedded) {
            return ALL;
        }
        if (association.isHasOne()) {
            return ALL;
        }
        else if (association.isOneToOne()) {
            return association.isOwningSide() ?  ALL : SAVE_UPDATE;
        }
        else if (association.isOneToMany()) {
            return association.isOwningSide() ?  ALL : SAVE_UPDATE;
        }  else if (association.isManyToMany()) {
            return  association.isCorrectlyOwned() || association.isCircular() ? SAVE_UPDATE :NONE;
        }
        else if (association.isManyToOne()) {
            if ( association.isCorrectlyOwned() && !association.isCircular()) {
                return ALL;
            }
            else if (association.isCompositeIdProperty()) {
                return ALL;
            } else {
                return NONE;
            }
        }
        else if (association instanceof Basic) {
            return ALL;
        }
        else if (Map.class.isAssignableFrom(association.getType())) {
            return association.isCorrectlyOwned() ? ALL :SAVE_UPDATE;
        } else {
            throw new MappingException("Unrecognized association type " + association.getType() );
        }

    }

    private  Mapping getOwnersWrappedForm(Association<?> association) {
        if (association.getOwner() instanceof GrailsHibernatePersistentEntity) {
            return ((GrailsHibernatePersistentEntity) association.getOwner()).getMappedForm();
        }
        return null;
    }


}