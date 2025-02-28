/*
 * Copyright 2004-2005 the original author or authors.
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
package grails.core;

import grails.config.Config;
import grails.util.Metadata;

import java.util.Map;

import org.grails.datastore.mapping.model.MappingContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;

/**
 * <p>The main interface representing a running Grails application. This interface's
 * main purpose is to provide a mechanism for analysing the conventions within a Grails
 * application as well as providing metadata and information about the execution environment.
 *
 * <p>The GrailsApplication interface interacts with {@link ArtefactHandler} instances
 * which are capable of analysing different artefact types (controllers, domain classes etc.) and introspecting
 * the artefact conventions
 *
 * <p>Implementors of this interface should be aware that a GrailsApplication is only initialised when the initialise() method
 * is called. In other words GrailsApplication instances are lazily initialised by the Grails runtime.
 *
 * @see #initialise()
 * @see ArtefactHandler
 *
 * @author Graeme Rocher
 * @author Steven Devijver
 *
 * @since 0.1
 */
public interface GrailsApplication extends ApplicationContextAware {

    /**
     * The id of the grails application within a bean context
     */
    String APPLICATION_ID = "grailsApplication";

    /**
     * The name of the class that provides configuration
     */
    @Deprecated
    String CONFIG_CLASS = "Config";
    /**
     * The name of the DataSource class
     */
    @Deprecated
    String DATA_SOURCE_CLASS = "DataSource";
    /**
     * The name of the project metadata file
     */
    @Deprecated
    String PROJECT_META_FILE = "application.properties";
    /**
     * The name of the transaction manager bean
     */
    String TRANSACTION_MANAGER_BEAN = "transactionManager";
    String SESSION_FACTORY_BEAN = "sessionFactory";
    String DATA_SOURCE_BEAN = "dataSource";
    String MESSAGE_SOURCE_BEAN = "messageSource";
    String MULTIPART_RESOLVER_BEAN = "multipartResolver";
    String EXCEPTION_HANDLER_BEAN = "exceptionHandler";
    String CLASS_LOADER_BEAN = "classLoader";
    String DIALECT_DETECTOR_BEAN = "dialectDetector";
    String OPEN_SESSION_IN_VIEW_INTERCEPTOR_BEAN = "openSessionInViewInterceptor";

    /**
     * Returns the ConfigObject instance.
     *
     * @return The ConfigObject instance
     */
    Config getConfig();

    /**
     * Returns the class loader instance for the Grails application.
     *
     * @return The ClassLoader instance
     */
    ClassLoader getClassLoader();

    /**
     * Retrieves all java.lang.Class instances loaded by the Grails class loader
     * @return An array of classes
     */
    @SuppressWarnings("rawtypes")
    Class[] getAllClasses();

    /**
     * Retrieves all java.lang.Class instances considered Artefacts loaded by the Grails class loader
     * @return An array of classes
     */
    @SuppressWarnings("rawtypes")
    Class[] getAllArtefacts();

    /**
     * Returns the Spring context for this application. Note that this
     * will return <code>null</code> until the application is fully
     * initialised. This context contains all the application artifacts,
     * plugin beans, the works.
     */
    ApplicationContext getMainContext();

    /**
     * @return The GORM {@link MappingContext}. Returns null if none present
     */
    MappingContext getMappingContext();

    /**
     * Sets the main Spring context for this application.
     */
    void setMainContext(ApplicationContext context);

    /**
     * Configures the {@link MappingContext} for this application
     *
     * @param mappingContext The mapping context
     */
    void setMappingContext(MappingContext mappingContext);

    /**
     * Returns the Spring application context that contains this
     * application instance. It is the parent of the context returned
     * by {@link #getMainContext()}.
     */
    ApplicationContext getParentContext();

    /**
     * Retrieves a class for the given name within the GrailsApplication or returns null
     *
     * @param className The name of the class
     * @return The class or null
     */
    @SuppressWarnings("rawtypes")
    Class getClassForName(String className);

    /**
     * This method will refresh the entire application
     */
    void refresh();

    /**
     * Rebuilds this Application throwing away the class loader and re-constructing it from the loaded
     * resources again. Can only be called in development mode and an error will be thrown if called
     * in a different enivronment
     */
    void rebuild();

    /**
     * Retrieves a Resource instance for the given Grails class or null it doesn't exist.
     *
     * @param theClazz The Grails class
     * @return A Resource or null
     */
    @SuppressWarnings("rawtypes")
    Resource getResourceForClass(Class theClazz);

    /**
     * <p>Call this to find out if the class you have is an artefact loaded by grails.</p>
     * @param theClazz A class to test
     * @return true if and only if the class was loaded from grails-app/
     * @since 0.5
     */
    @SuppressWarnings("rawtypes")
    boolean isArtefact(Class theClazz);

    /**
     * <p>Check if the specified artefact Class has been loaded by Grails already AND is
     * of the type expected</p>
     * @param artefactType A string identifying the artefact type to check for
     * @param theClazz The class to check
     * @return true if Grails considers the class to be managed as an artefact of the type specified.
     * @since 0.5
     */
    @SuppressWarnings("rawtypes")
    boolean isArtefactOfType(String artefactType, Class theClazz);

    /**
     * <p>Check if the artefact Class with the name specified is of the type expected</p>
     * @param artefactType A string identifying the artefact type to check for
     * @param className The name of a class to check
     * @return true if Grails considers the class to be managed as an artefact of the type specified.
     * @since 0.5
     */
    boolean isArtefactOfType(String artefactType, String className);

    /**
     * <p>Gets the GrailsClass associated with the named artefact class</p>
     * <p>i.e. to get the GrailsClass for  controller called "BookController" you pass the name "BookController"</p>
     * @param artefactType The type of artefact to retrieve, i.e. "Controller"
     * @param name The name of an artefact such as "BookController"
     * @return The associated GrailsClass or null
     * @since 0.5
     */
    GrailsClass getArtefact(String artefactType, String name);

    /**
     * Returns the ArtefactHandler for the given class or null
     * @param theClass The class
     * @return The ArtefactHandler
     */
    @SuppressWarnings("rawtypes")
    ArtefactHandler getArtefactType(Class theClass);

    /**
     * <p>Obtain all the class information about the artefactType specified</p>
     * @param artefactType An artefact type identifier i.e. "Domain"
     * @return The artefact info or null if the artefactType is not recognized
     * @since 0.5
     */
    ArtefactInfo getArtefactInfo(String artefactType);

    /**
     * <p>Get an array of all the GrailsClass instances relating to artefacts of the specified type.</p>
     * @param artefactType The type of artefact to retrieve, i.e. "Controller"
     * @return An array of GrailsClasses which may empty by not null
     * @since 0.5
     */
    GrailsClass[] getArtefacts(String artefactType);

    /**
     * <p>Get an artefact GrailsClass by a "feature" which depending on the artefact may be a URI or tag name
     * for example</p>
     * @param artefactType The type ID of the artefact, i.e. "TagLib"
     * @param featureID The "feature" ID, say a URL or tag name
     * @return The grails class or null if none is found
     * @since 0.5
     */
    GrailsClass getArtefactForFeature(String artefactType, Object featureID);

    /**
     * <p>Registers a new artefact</p>
     * @param artefactType The type ID of the artefact, i.e. "TagLib"
     * @param artefactClass The class of the artefact. A new GrailsClass will be created automatically and added
     * to internal structures, using the appropriate ArtefactHandler
     * @return The new grails class for the artefact class
     * @since 0.5
     */
    @SuppressWarnings("rawtypes")
    GrailsClass addArtefact(String artefactType, Class artefactClass);

    /**
     * <p>Registers a new artefact</p>
     * @param artefactType The type ID of the artefact, i.e. "TagLib"
     * @param artefactGrailsClass The GrailsClass of the artefact.
     * @return The supplied grails class for the artefact class
     * @since 0.5
     */
    GrailsClass addArtefact(String artefactType, GrailsClass artefactGrailsClass);

    /**
     * <p>Register a new artefact handler</p>
     * @param handler The new handler to add
     */
    void registerArtefactHandler(ArtefactHandler handler);

    /**
     * <p>Test whether an artefact handler exists for a given type</p>
     * @param type The type of the handler
     * @return true if it does
     */
    boolean hasArtefactHandler(String type);

    /**
     * <p>Obtain a list of all the artefact handlers</p>
     * @return The list, possible empty but not null, of all currently registered handlers
     */
    ArtefactHandler[] getArtefactHandlers();

    /**
     * Initialise this GrailsApplication.
     */
    void initialise();

    /**
     * Returns whether this GrailsApplication has been initialised or not.
     * @return true if it has been initialised
     */
    boolean isInitialised();

    /**
     * <p>Get access to the project's metadata, specified in application.yml and grails.build.info if it is present</p>
     * <p>This provides access to information like required grails version, application name, version etc
     * but <b>NOT</b> general application settings.</p>
     * @return A read-only Map of data about the application, not environment specific
     */
    Metadata getMetadata();

    /**
     * Retrieves an artefact by its logical property name. For example the logical property name of
     * BookController would be book.
     * @param type The artefact type
     * @param logicalName The logical name
     * @return The GrailsClass or null if it doesn't exist
     */
    GrailsClass getArtefactByLogicalPropertyName(String type, String logicalName);

    /**
     * Adds the given artefact, attempting to determine type from
     * @param artefact The artefact to add
     */
    @SuppressWarnings("rawtypes")
    void addArtefact(Class artefact);

    /**
     * Returns true if this application has been deployed as a WAR file
     *
     * @return true if the application is WAR deployed
     */
    boolean isWarDeployed();

    /**
     * Adds an artefact that can be overriden by user defined classes
     * @param artefact An overridable artefact
     */
    @SuppressWarnings("rawtypes")
    void addOverridableArtefact(Class artefact);

    /**
     * Fired to inform the application when the Config.groovy file changes.
     */
    void configChanged();

    /**
     * Returns the ArtefactHandler for the given type
     * @param type The artefact handler type
     * @return The artefact handler
     */
    ArtefactHandler getArtefactHandler(String type);
}
