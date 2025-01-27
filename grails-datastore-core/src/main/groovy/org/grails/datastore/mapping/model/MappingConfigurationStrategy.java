package org.grails.datastore.mapping.model;

import java.util.List;
import java.util.Set;

/**
 * <p>This interface defines a strategy for reading how
 * persistent properties are defined in a persistent entity.</p>
 *
 * <p>Subclasses can implement this interface in order to provide
 * a different mechanism for mapping entities such as annotations or XML.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public interface MappingConfigurationStrategy {

    /**
     * Tests whether the given class is a persistent entity
     *
     * @param javaClass The java class
     * @return true if it is a persistent entity
     */
    boolean isPersistentEntity(Class javaClass);

    /**
     * @see #getPersistentProperties(Class, MappingContext, ClassMapping)
     */
    List<PersistentProperty> getPersistentProperties(Class javaClass, MappingContext context);

    /**
     * @see #getPersistentProperties(Class, MappingContext, ClassMapping)
     */
    List<PersistentProperty> getPersistentProperties(PersistentEntity entity, MappingContext context, ClassMapping classMapping);

    /**
     * @see #getPersistentProperties(Class, MappingContext, ClassMapping)
     */
    List<PersistentProperty> getPersistentProperties(PersistentEntity entity, MappingContext context, ClassMapping classMapping, boolean includeIdentifiers);

    /**
     * Obtains a List of PersistentProperty instances for the given Mapped class
     *
     * @param javaClass The Java class
     * @param context The MappingContext instance
     * @param mapping The mapping for this class
     * @return The PersistentProperty instances
     */
    List<PersistentProperty> getPersistentProperties(Class javaClass, MappingContext context, ClassMapping mapping);

    /**
     * Obtains the identity of a persistent entity
     *
     * @param javaClass The Java class
     * @param context The MappingContext
     * @return A PersistentProperty instance
     */
    PersistentProperty getIdentity(Class javaClass, MappingContext context);

    /**
     * Obtains the identity of a persistent entity
     *
     * @param javaClass The Java class
     * @param context The MappingContext
     * @return A PersistentProperty instance
     */
    PersistentProperty[] getCompositeIdentity(Class javaClass, MappingContext context);

    /**
     * Obtains the default manner in which identifiers are mapped. In GORM
     * this is just using a property called 'id', but in other frameworks this
     * may differ. For example JPA expects an annotated @Id property
     *
     * @return The default identifier mapping
     * @param classMapping The ClassMapping instance
     */
    IdentityMapping getDefaultIdentityMapping(ClassMapping classMapping);

    /**
     * Returns a set of entities that "own" the given entity. Ownership
     * dictates default cascade strategies. So if entity A owns entity B
     * then saves, updates and deletes will cascade from A to B
     *
     * @param javaClass The Java class
     * @param context The MappingContext
     * @return A Set of owning classes
     */
    Set getOwningEntities(Class javaClass, MappingContext context);

    /**
     * Obtains the identity mapping for the specified class mapping
     *
     * @param classMapping The class mapping
     * @return The identity mapping
     */
    IdentityMapping getIdentityMapping(ClassMapping classMapping);

    /**
     * Whether the strategy can add new entities to the mapping context
     */
    void setCanExpandMappingContext(boolean canExpandMappingContext);

}
