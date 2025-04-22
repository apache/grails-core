/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.core;

import java.util.Map;
import java.util.Set;

import org.springframework.validation.Validator;

/**
 * Represents a persistable Grails domain class.
 *
 * @author Graeme Rocher
 * @since Jul 5, 2005
 *
 * @deprecated Use {@link org.grails.datastore.mapping.model.PersistentEntity} instead
 */
@Deprecated
public interface GrailsDomainClass extends GrailsClass {

    /**
     * The name of the default ORM implementation used to map the class
     */
    String GORM = "GORM";

    String ORM_MAPPING = "mapping";

    /**
     * @return Whether to autowire
     */
    boolean isAutowire();
    /**
     * @param domainClass
     * @return true if the specifying domain class is on the owning side of a relationship
     */
    @SuppressWarnings("rawtypes")
    boolean isOwningClass(Class domainClass);

    /**
     * <p>Returns the default property name of the GrailsClass. For example the property name for
     * a class called "User" would be "user"</p>
     *
     * @return The property name representation of the class name
     */
    String getPropertyName();

    /**
     * Returns a map of constraints applied to this domain class with the keys being the property name
     * and the values being ConstrainedProperty instances
     *
     * @return A map of constraints
     */
    @SuppressWarnings("rawtypes")
    Map getConstrainedProperties();

    /**
     * Retreives the validator for this domain class
     *
     * @return A validator instance or null if none exists
     */
    Validator getValidator();

    /**
     * Sets the validator for this domain class
     *
     * @param validator The domain class validator to set
     */
    void setValidator(Validator validator);

}
