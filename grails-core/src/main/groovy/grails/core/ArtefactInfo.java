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

/**
 * Holder for all the class-related info and structures relating to an Artefact.
 *
 * @author Marc Palmer (marc@anyware.co.uk)
 * @author Graeme Rocher
 *
 * @since 1.0
 */
public interface ArtefactInfo {

    /**
     * Gets you the array of all artefact Class(es), the original classes loaded
     * that represent the artefacts.
     * @return The array, which may be empty but never null.
     */
    @SuppressWarnings("rawtypes")
    Class[] getClasses();

    /**
     * Gets you the array of all artefact GrailsClass(es), the classes used to track
     * information about the artefact.
     * @return The array, which may be empty but never null.
     */
    GrailsClass[] getGrailsClasses();

    /**
     * Gets you the unmodifiable Map of all artefact Class(es), the actual classes implementing the artefact.
     * <p>The Map is keyed by class name, i.e. "BookController"</p>
     * @return The Map, which may be empty but never null.
     */
    @SuppressWarnings("rawtypes")
    Map getClassesByName();

    /**
     * <p>Gets you the unmodifiable Map of all artefact GrailsClass(es), the classes that store extra
     * state relating to the artefact.</p>
     * <p>The Map is keyed by class name, i.e. "BookController"</p>
     * @return The Map, which may be empty but never null.
     */
    @SuppressWarnings("rawtypes")
    Map getGrailsClassesByName();

    /**
     * <p>Retrieves the GrailsClass representing a named artefact.</p>
     * <p>From the GrailsClass you can get to the original artefact Class using getClazz().</p>
     * @param name The name of the artefact, i.e. "BookController"
     * @return The Map, which may be empty but never null.
     */
    GrailsClass getGrailsClass(String name);

    /**
     * Retrieves a Grails class by its logical property name. For example "BookController" would be "book".
     * @param logicalName The logical name
     * @return A GrailsClass or null
     */
    GrailsClass getGrailsClassByLogicalPropertyName(String logicalName);
}
