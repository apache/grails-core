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

import groovy.lang.MetaClass;

import grails.core.support.GrailsApplicationAware;

/**
 * Represents any class in a Grails application.
 *
 * @author Steven Devijver
 * @author Graeme Rocher
 * @since 0.1
 */
public interface GrailsClass extends GrailsApplicationAware{

    /**
     * Whether the class is abstract or not
     *
     * @return true if it is abstract
     */
    boolean isAbstract();

    /**
     * The GrailsApplication that this class belongs to
     *
     * @return The GrailsApplication instance
     */
    GrailsApplication getApplication();

    /**
     * Gets the initial value of the given property on the class.
     * @param name The name of the property
     * @return The initial value
     */
    Object getPropertyValue(String name);

    /**
     * Returns true if the class has the specified property.
     * @param name The name of the property
     * @return true if it does
     */
    boolean hasProperty(String name);

    /**
     * Creates a new instance of this class.
     *
     * This method can be used as factory method in the Spring application context.
     * @return A new instance of this class
     */
    Object newInstance();

    /**
     * Returns the logical name of the class in the application without the trailing convention part if applicable
     * and without the package name.
     *
     * @return The logical name
     */
    String getName();

    /**
     * Returns the short name of the class without package prefix.
     *
     * @return The short name
     */
    String getShortName();

    /**
     * Returns the full name of the class in the application with the trailing convention part and with
     * the package name.
     *
     * @return The full name
     */
    String getFullName();

    /**
     * Returns the name of the class as a property name.
     *
     * @return The property name representation
     */
    String getPropertyName();

    /**
     * Returns the logical name of the class as a property name.
     *
     * @return The logical property name
     */
    String getLogicalPropertyName();

    /**
     * Returns the name of the property in natural terms (eg. 'lastName' becomes 'Last Name')
     * @return The natural property name.
     */
    String getNaturalName();

    /**
     * Returns the package name of the class.
     *
     * @return The package name
     */
    String getPackageName();

    /**
     * Returns the actual clazz represented by the GrailsClass.
     *
     * @return The class
     */
    @SuppressWarnings("rawtypes")
    Class getClazz();

    /**
     * @return The MetaClass for this Grails class
     */
    MetaClass getMetaClass();

    /**
     * @return Sample (reference) instance for this Grails class
     */
    Object getReferenceInstance();

    /**
     * Obtains a property value for the given name and type
     * @param name The name
     * @param type The type
     *
     * @return The property value
     */
    <T> T getPropertyValue(String name, Class<T> type);

    /**
     * @return The plugin where the artefact originates from
     */
    String getPluginName();
}
