/* Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.model;

import java.util.Collection;

import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.grails.datastore.mapping.reflect.EntityReflector;
import org.grails.datastore.mapping.validation.ValidatorRegistry;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.grails.datastore.mapping.proxy.ProxyFactory;
import org.springframework.validation.Validator;

/**
 * <p>Defines the overall context including all known
 * PersistentEntity instances and methods to obtain instances on demand</p>
 *
 * <p>This interface is used internally to establish associations
 * between entities and also at runtime to obtain entities by name</p>
 *
 * <p>The generic type parameters T &amp; R are used to specify the
 * mapped form of a class (example Table) and property (example Column) respectively.</p>
 *
 * <p>Uses instances of the {@link org.grails.datastore.mapping.core.Datastore} interface to
 * discover how to persist objects</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public interface MappingContext {

    /**
     * @return The multi tenancy mode
     */
    MultiTenancySettings.MultiTenancyMode getMultiTenancyMode();

    /**
     * Obtains a list of PersistentEntity instances
     *
     * @return A list of PersistentEntity instances
     */
    Collection<PersistentEntity> getPersistentEntities();

    /**
     * Obtains a PersistentEntity by name
     *
     * @param name The name of the entity
     * @return The entity or null
     */
    PersistentEntity getPersistentEntity(String name);

    /**
     * Returns true if the given entity is in an inheritance hierarchy
     * @param entity The entity
     * @return True if it is
     */
    boolean isInInheritanceHierarchy(PersistentEntity entity);
    /**
     * Obtains a child of the given root entity using the given discriminator
     * @param root The root entity
     * @param discriminator The discriminator
     * @return The child entity or null if non exists
     */
    PersistentEntity getChildEntityByDiscriminator(PersistentEntity root, String discriminator);

    /**
     * Obtains all of the children for the given root
     *
     * @param root The root
     * @return The children
     */
    Collection<PersistentEntity> getChildEntities(PersistentEntity root);

    /**
     * Obtains only the direct children (1 level down) for the given root entity
     *
     * @param root The root
     * @return The children
     */
    Collection<PersistentEntity> getDirectChildEntities(PersistentEntity root);

    /**
     * Adds several PersistentEntity instances
     *
     * @param javaClasses The Java class representing the entity
     * @return The PersistentEntity instance
     */
    Collection<PersistentEntity> addPersistentEntities(Class...javaClasses);
    /**
     * Adds a PersistentEntity instance
     *
     * @param javaClass The Java class representing the entity
     * @return The PersistentEntity instance
     */
    PersistentEntity addPersistentEntity(Class javaClass);

    /**
     * Adds a PersistentEntity instance
     *
     * @param javaClass The Java class representing the entity
     * @param override Whether to override an existing entity
     *
     * @return The PersistentEntity instance
     */
    PersistentEntity addPersistentEntity(Class javaClass, boolean override);

    /**
     * Adds a persistent entity that is not mapped by this MappingContext instance.
     * Used for cross store persistence
     *
     * @param javaClass The Java class
     * @return The persistent entity
     */
    PersistentEntity addExternalPersistentEntity(Class javaClass);

    /**
     * Adds a validator to be used by the entity for validation
     * @param entity The PersistentEntity
     * @param validator The validator
     */
    void addEntityValidator(PersistentEntity entity, Validator validator);

    /**
     * Sets the validator registry used by this mapping context
     *
     * @param validatorRegistry The validator registry
     */
    void setValidatorRegistry(ValidatorRegistry validatorRegistry);

    /**
     * @return Gets the validation registry used by this MappingContext
     */
    ValidatorRegistry getValidatorRegistry();

    /**
     * Add a converter used to convert property values to and from the datastore
     *
     * @param converter The converter to add
     */
    void addTypeConverter(Converter converter);

    /**
     * Obtains the ConversionService instance to use for type conversion
     * @return The conversion service instance
     */
    ConversionService getConversionService();

    /**
     * Obtains the converter registry
     *
     * @return The converter registry used for type conversion
     */
    ConverterRegistry getConverterRegistry();

    /**
     * Obtains a validator for the given entity
     * @param entity The entity
     * @return A validator or null if none exists for the given entity
     */
    Validator getEntityValidator(PersistentEntity entity);

    /**
     * Returns the syntax reader used to interpret the entity
     * mapping syntax
     *
     * @return The SyntaxReader
     */
    MappingConfigurationStrategy getMappingSyntaxStrategy();

    /**
     * Obtains the MappingFactory instance
     * @return The mapping factory instance
     */
    MappingFactory getMappingFactory();

    /**
     * Returns whether the specified class is a persistent entity
     * @param type The type to check
     * @return True if it is
     */
    boolean isPersistentEntity(Class type);

    /**
     * Returns whether the specified value is a persistent entity
     * @param value The value to check
     * @return True if it is
     */
    boolean isPersistentEntity(Object value);

    /**
     * Factory used for creating proxies
     * @return The proxy factory
     */
    ProxyFactory getProxyFactory();

    /**
     * For handling proxies
     *
     * @return The proxy handler
     */
    ProxyHandler getProxyHandler();

    /**
     * Factory to use for creating proxies
     * @param factory The proxy factory
     */
    void setProxyFactory(ProxyFactory factory);

    /**
     * Adds a new mapping context listener instance
     * @param listener The listener
     */
    void addMappingContextListener(Listener listener);

    PersistentEntity createEmbeddedEntity(Class type);

    /**
     * Obtains a {@link EntityReflector} instance for the given entity
     *
     * @param entity The entity
     * @return The class data
     */
    EntityReflector getEntityReflector(PersistentEntity entity);


    /**
     * Creates an {@link EntityAccess} instance for the given entity and instance of said entity
     *
     * @param entity The entity
     * @param instance The instance
     * @return The {@link EntityAccess}
     */
    EntityAccess createEntityAccess(PersistentEntity entity, Object instance);

    /**
     * Implementors can register for events when the mapping context changes
     */
    interface Listener {

        /**
         * Fired when a new entity is added
         * @param entity The entity
         */
        void persistentEntityAdded(PersistentEntity entity);
    }
}
