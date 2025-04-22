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

import org.codehaus.groovy.ast.ClassNode;

/**
 * <p>The ArtefactHandler interface's purpose is to allow the analysis of conventions within a Grails application.
 * An artefact is represented by the GrailsClass interface and this interface provides methods that allow artefacts to
 * be identified, created and initialized.
 *
 * <p>Artefacts need to provide info about themselves, and some callbacks are required
 * to verify whether or not a class is that kind of artefact/p>
 *
 * @see GrailsApplication#registerArtefactHandler(ArtefactHandler)
 *
 * @author Graeme Rocher
 * @author Marc Palmer (marc@anyware.co.uk)
 * @since 1.0
 */
public interface ArtefactHandler {

    /**
     * Obtains the plugin name that deals with this artefact.
     * @return The plugin name or null if there isn't one
     */
    String getPluginName();

    /**
     * Implementations must return a name such as "Domain" to indicate the type of artefact they represent.
     * @return The aretfact type, as a String
     */
    String getType();
    /**
     * @param classNode The ClassNode instance
     * @return True if the given ClassNode instance is an instance of the Artefact type
     */
    boolean isArtefact(ClassNode classNode);
    /**
     * <p>Called by the GrailsApplication whenever it needs to know if a given class
     * is considered to be the kind of artefact represented by this handler.</p>
     * <p>Typically you will check the name of the class and some other properties to see
     * if it is of the correct artefact type</p>
     * @param aClass A class to test
     * @return true if the class looks like one of your artefacts
     */
    boolean isArtefact(@SuppressWarnings("rawtypes") Class aClass);

    /**
     * <p>Called by GrailsApplication when a new class is found and a GrailsClass wrapping it is required</p>
     * @param artefactClass The new class that has been loaded
     * @return A new custom GrailsClass wrapper containing any extra information your artefact type requires
     */
    GrailsClass newArtefactClass(@SuppressWarnings("rawtypes") Class artefactClass);

    /**
     * <p>Called whenever the list of artefacts has changed or been reloaded.</p>
     * <p>It must be safe to call this method multiple times and have any internal data structures reset.</p>
     * @param artefacts The collection of artefact classes for this handler
     */
    void initialize(ArtefactInfo artefacts);

    /**
     * <p>Called to retrieve an artefact relating to some other key for example a URI or tag name</p>
     * <p>Handlers are responsible for caching the appropriate information using the data passed to them in calls
     * to initialize()</p>
     * @param feature Any object that acts as a key
     * @return A matching artefact GrailsClass or null if there is no match for this feature ID
     */
    GrailsClass getArtefactForFeature(Object feature);

    /**
     * <p>Called to check if the specified GrailsClass is one managed by this artefact handler</p>
     * @param artefactGrailsClass A GrailsClass instance
     * @return true if this handler manages the specified GrailsClass
     */
    boolean isArtefactGrailsClass(GrailsClass artefactGrailsClass);
}
