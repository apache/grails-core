package grails.gorm.validation

/**
 * Represents an entity that is constrained
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface ConstrainedEntity {

    /**
     * @return The constrained property instances
     */
    Map<String, ConstrainedProperty> getConstrainedProperties()
}