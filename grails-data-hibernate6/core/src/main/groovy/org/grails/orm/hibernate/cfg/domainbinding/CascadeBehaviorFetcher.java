package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.grails.orm.hibernate.cfg.domainbinding.CascadeBehavior.*;


public class CascadeBehaviorFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(CascadeBehaviorFetcher.class);

    public String getCascadeBehaviour(PersistentProperty<?> grailsProperty) {
        PropertyConfig config = new PersistentPropertyToPropertyConfig().apply(grailsProperty);
        if (config != null && config.getCascade() != null) {
            String cascade = config.getCascade();
            LOG.debug("Cascade strategy for property {} is {}", grailsProperty.getName(), cascade);
            return cascade;
        }

        if (grailsProperty instanceof Association association) {
            return determineImplicitCascade( association);
        }

        LOG.debug("No cascade strategy for property: {}", grailsProperty);
        return NONE.getValue();
    }

    /**
     * Determines the implicit, default cascade behavior for an association.
     * This method contains a direct, but cleaner, translation of the original implementation's logic.
     *
     * @param association The association to evaluate.
     * @return The default cascade strategy string.
     */
    private String determineImplicitCascade(Association association) {
        var cascadeStrategy = NONE; // Default for associations unless overridden
        PersistentEntity referenced = association.getAssociatedEntity();
        PersistentEntity domainClass = association.getOwner();

        if (association.isHasOne() || association.isOneToOne()) {
            if (referenced != null && association.isOwningSide()) {
                cascadeStrategy = ALL;
            }
            else {
                cascadeStrategy = SAVE_UPDATE;
            }
        }
        else if (association.isOneToMany()) {
            if (referenced != null && association.isOwningSide()) {
                cascadeStrategy = ALL;
            }
            else {
                cascadeStrategy = SAVE_UPDATE;
            }
        }
        else if (association.isManyToMany()) {
            if ((referenced != null && referenced.isOwningEntity(domainClass)) || association.isCircular()) {
                cascadeStrategy = SAVE_UPDATE;
            }
        }
        else if (association.isManyToOne()) {
            if (referenced != null && referenced.isOwningEntity(domainClass) && !association.isCircular()) {
                cascadeStrategy = ALL;
            }
            else {
                Mapping mappedForm = new HibernateEntityWrapper(domainClass).getMappedForm();
                if (mappedForm.isCompositeIdProperty(association)) {
                    cascadeStrategy = ALL;
                }
            }
        }
        else if (association instanceof Basic) {
            cascadeStrategy = ALL;
        }
        else if (Map.class.isAssignableFrom(association.getType())) {
            if (referenced != null && referenced.isOwningEntity(domainClass)) {
                cascadeStrategy = ALL;
            }
            else {
                cascadeStrategy = SAVE_UPDATE;
            }
        }
        new LogCascadeMapping(LOG).logCascadeMapping(association, cascadeStrategy, referenced);
        return cascadeStrategy.getValue();
    }

}