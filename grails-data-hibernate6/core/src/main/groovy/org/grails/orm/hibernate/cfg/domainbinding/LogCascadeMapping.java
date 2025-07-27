package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.ManyToMany;
import org.grails.datastore.mapping.model.types.ManyToOne;
import org.grails.datastore.mapping.model.types.OneToMany;
import org.grails.datastore.mapping.model.types.OneToOne;
import org.slf4j.Logger;

public class LogCascadeMapping {

    private final Logger log;

    public LogCascadeMapping(Logger log) {
        this.log = log;
    }

    /**
     * Logs the cascade mapping strategy for a given association if debug logging is enabled.
     *
     * @param grailsProperty    The association property.
     * @param cascadeStrategy   The calculated cascade string.
     * @param referenced        The entity referenced by the association.
     */
    public void logCascadeMapping(Association grailsProperty, CascadeBehavior cascadeStrategy, PersistentEntity referenced) {
        if (log.isDebugEnabled() && referenced != null) {
            String assType = getAssociationType(grailsProperty);
            log.debug("Mapping cascade strategy for {} property {}.{} referencing type [{}] -> [CASCADE: {}]",
                    assType, grailsProperty.getOwner().getName(), grailsProperty.getName(),
                    referenced.getJavaClass().getName(), cascadeStrategy);
        }
    }

    /**
     * Determines the string representation of an association's type using a modern
     * switch expression with pattern matching.
     *
     * @param association The association to inspect.
     * @return A string describing the association type (e.g., "one-to-many").
     */
    private String getAssociationType(Association association) {
        // Use a standard if-else-if chain for compatibility with Java 17 and earlier.
        if (association instanceof ManyToMany) {
            return "many-to-many";
        }
        else if (association instanceof OneToMany) {
            return "one-to-many";
        }
        else if (association instanceof OneToOne) {
            return "one-to-one";
        }
        else if (association instanceof ManyToOne) {
            return "many-to-one";
        }
        else if (association.isEmbedded()) {
            return "embedded";
        }
        return "unknown";
    }

}