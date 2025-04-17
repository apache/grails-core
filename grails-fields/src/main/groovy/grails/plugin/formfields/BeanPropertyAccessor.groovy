/*
 * Copyright 2012 Rob Fletcher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugin.formfields

import grails.core.GrailsDomainClass
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.scaffolding.model.property.Constrained
import org.springframework.validation.FieldError

interface BeanPropertyAccessor {

    /**
     * @return the object at the root of a path expression, e.g. for a `person` bean and `address.street` then `person` is returned.
     */
    Object getRootBean()

    /**
     * @return the type of the object at the root of a path expression, e.g. for a `person` bean and `address.street` then the type of `person` is returned.
     */
    Class getRootBeanType()

    /**
     * @return the full path from the root bean to the requested property.
     */
    String getPathFromRoot()

    /**
     * @return the name of the property at the end of the path, e.g. for `address.home.street`, `street` is returned.
     */
    String getPropertyName()

    /**
     * @return the type of the object that owns the property at the end of the path, e.g. for a `address.home.street` then the type of `home` is returned.
     */
    Class getBeanType()

    /**
     * @return the GORM domain type of `beanType`. This will be null if `beanType` is not a domain class.
     * @deprecated use {@link #getEntity}
     */
    @Deprecated
    GrailsDomainClass getBeanClass()

    /**
     * @return the GORM domain type of `beanType`. This will be null if `beanType` is not a domain class.
     */
    PersistentEntity getEntity()

    /**
     * @return all superclasses and interfaces of `beanType` excluding `Object`, `Serializable`, `Comparable` and `Cloneable`.
     */
    List<Class> getBeanSuperclasses()

    /**
     * @return the type of the property at the end of the path, e.g. for `address.home.street` then the type of `street` is returned.
     */
    Class getPropertyType()

    /**
     * @return all superclasses and interfaces of `propertyType` excluding `Object`, `Serializable`, `Comparable` and `Cloneable`.
     */
    List<Class> getPropertyTypeSuperclasses()

    /**
     * @return the value of the property at the end of the path, e.g. for `address.home.street` then the value of `street` is returned.
     */
    Object getValue()

    /**
     * @return the GORM persistent property descriptor for the property at the end of the path, e.g. for `address.home.street` then the descriptor of `street` is returned. This will be null for non-domain properties.
     */
    PersistentProperty getDomainProperty()

    /**
     * @return the constraints of the property at the end of the path, e.g. for `address.home.street` then the constraints of `street` are returned. This will be null for non-domain properties.
     */
    Constrained getConstraints()

    /**
     * @return the i18n keys used to resolve a label for the property at the end of the path in order of preference.
     */
    List<String> getLabelKeys()

    /**
     * @return default label text for the property at the end of the path.
     */
    String getDefaultLabel()

    /**
     * @return the resolved messages for any validation errors present on the property at the end of the path. This will be an empty list if there are no errors or the property is not a validateable type.
     */
    List<FieldError> getErrors()

    /**
     * @return whether or not the property is required as determined by constraints. This will always be false for non-validateable types.
     */
    boolean isRequired()

    /**
     * @return whether or not the property has any validation errors (i.e. `getErrors` will return a non-empty list). This will always be false for non-validateable types.
     */
    boolean isInvalid()

}

