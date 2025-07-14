/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.core;

import grails.config.Config;
import grails.core.events.ArtefactAdditionEvent;
import grails.util.GrailsNameUtils;
import grails.util.GrailsUtil;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClassRegistry;
import groovy.lang.MetaMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.reflection.CachedClass;
import org.codehaus.groovy.runtime.m12n.ExtensionModuleScanner;
import org.codehaus.groovy.runtime.metaclass.MetaClassRegistryImpl;
import org.grails.config.PropertySourcesConfig;
import org.grails.core.AbstractGrailsApplication;
import org.grails.core.artefact.DomainClassArtefactHandler;
import org.grails.core.exceptions.GrailsConfigurationException;
import org.grails.core.io.support.GrailsFactoriesLoader;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.io.support.GrailsResourceUtils;
import org.grails.spring.beans.GrailsApplicationAwareBeanPostProcessor;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of the GrailsApplication interface that manages application loading,
 * state, and artefact instances.
 * <p>
 * Upon loading this GrailsApplication will inspect each class using its registered ArtefactHandler instances. Each
 * ArtefactHandler provides knowledge about the conventions used to establish its artefact type. For example
 * controllers use the ControllerArtefactHandler to establish this knowledge.
 * <p>
 * New ArtefactHandler instances can be registered with the GrailsApplication thus allowing application extensibility.
 *
 * @author Marc Palmer
 * @author Steven Devijver
 * @author Graeme Rocher
 * @see grails.plugins.GrailsPluginManager
 * @see grails.plugins.DefaultGrailsPluginManager
 * @see grails.core.ArtefactHandler
 * @see grails.core.ArtefactInfo
 * @since 0.1
 */
public class DefaultGrailsApplication extends AbstractGrailsApplication implements BeanClassLoaderAware {

    protected static final Pattern GETCLASSESPROP_PATTERN = Pattern.compile("(\\w+)(Classes)");
    protected static final Pattern GETCLASSESMETH_PATTERN = Pattern.compile("(get)(\\w+)(Classes)");
    protected static final Pattern ISCLASS_PATTERN = Pattern.compile("(is)(\\w+)(Class)");
    protected static final Pattern GETCLASS_PATTERN = Pattern.compile("(get)(\\w+)Class");
    protected static final Log LOG = LogFactory.getLog(DefaultGrailsApplication.class);

    private static boolean extensionMethodsInitialized = false;

    protected Class<?>[] allClasses = new Class[0];

    protected Set<Class<?>> loadedClasses = new LinkedHashSet<Class<?>>();
    protected ArtefactHandler[] artefactHandlers;
    protected Map<String, ArtefactHandler> artefactHandlersByName = new HashMap<String, ArtefactHandler>();
    protected List<Class<?>> allArtefactClasses = new ArrayList<Class<?>>();
    protected Map<String, ArtefactInfo> artefactInfo = new HashMap<String, ArtefactInfo>();
    protected Class<?>[] allArtefactClassesArray;
    protected Resource[] resources;
    protected boolean initialised = false;
    protected GrailsApplicationClass applicationClass;

    protected MappingContext proxyMappingContext = buildMappingContextProxy(this);
    protected MappingContext mappingContext;

    /**
     * Creates a new empty Grails application.
     */
    public DefaultGrailsApplication() {
        this(new GroovyClassLoader());
    }

    /**
     * Creates a new empty Grails application.
     */
    public DefaultGrailsApplication(GrailsApplicationClass applicationClass) {
        this(new GroovyClassLoader());
        this.applicationClass = applicationClass;
    }

    public DefaultGrailsApplication(ClassLoader classLoader) {
        super();
        this.classLoader = classLoader;
    }

    /**
     * Construct an application for the given classes
     *
     * @param classes The classes
     */
    public DefaultGrailsApplication(final Class<?>... classes) {
        this(classes, new GroovyClassLoader(Thread.currentThread().getContextClassLoader()));
    }

    /**
     * Creates a new GrailsApplication instance using the given classes and GroovyClassLoader.
     *
     * @param classes     The classes that make up the GrailsApplication
     * @param classLoader The GroovyClassLoader to use
     */
    public DefaultGrailsApplication(final Class<?>[] classes, ClassLoader classLoader) {
        super();
        Assert.notNull(classes, "Constructor argument 'classes' cannot be null");

        loadedClasses.addAll(Arrays.asList(classes));
        allClasses = classes;
        this.classLoader = classLoader;
    }

    /**
     * Loads a GrailsApplication using the given ResourceLocator instance which will search for appropriate class names
     *
     */
    public DefaultGrailsApplication(Resource[] resources) {
        this();
        for (Resource resource : resources) {

            Class<?> aClass;
            try {
                aClass = classLoader.loadClass(GrailsResourceUtils.getClassName(resource.getFile().getAbsolutePath()));
            } catch (ClassNotFoundException e) {
                throw new GrailsConfigurationException("Class not found loading Grails application: " + e.getMessage(), e);
            } catch (IOException e) {
                throw new GrailsConfigurationException("Class not found loading Grails application: " + e.getMessage(), e);
            }
            loadedClasses.add(aClass);
        }
    }

    /**
     * Loads a GrailsApplication using the given ResourceLocator instance which will search for appropriate class names
     *
     */
    public DefaultGrailsApplication(org.grails.io.support.Resource[] resources) {
        this();
        for (org.grails.io.support.Resource resource : resources) {

            Class<?> aClass;
            try {
                aClass = classLoader.loadClass(GrailsResourceUtils.getClassName(resource.getFile().getAbsolutePath()));
            } catch (ClassNotFoundException e) {
                throw new GrailsConfigurationException("Class not found loading Grails application: " + e.getMessage(), e);
            } catch (IOException e) {
                throw new GrailsConfigurationException("Class not found loading Grails application: " + e.getMessage(), e);
            }
            loadedClasses.add(aClass);
        }
    }

    private static MappingContext buildMappingContextProxy(final DefaultGrailsApplication application) {
        InvocationHandler proxyHandler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                MappingContext realContext = application.mappingContext;
                if (realContext == null) {
                    throw new GrailsConfigurationException("The method [" + method + "] cannot be accessed before GORM has initialized");
                }
                return ReflectionUtils.invokeMethod(method, realContext, args);
            }
        };
        return (MappingContext) Proxy.newProxyInstance(application.getClass().getClassLoader(), new Class[]{MappingContext.class}, proxyHandler);
    }

    /**
     * @return The application class
     */
    public GrailsApplicationClass getApplicationClass() {
        return applicationClass;
    }

    /**
     * Initialises the default set of ArtefactHandler instances.
     *
     * @see grails.core.ArtefactHandler
     */
    @SuppressWarnings("deprecation")
    protected void initArtefactHandlers() {

        List<ArtefactHandler> additionalArtefactHandlers = GrailsFactoriesLoader.loadFactories(ArtefactHandler.class, getClassLoader());

        for (ArtefactHandler artefactHandler : additionalArtefactHandlers) {
            registerArtefactHandler(artefactHandler);
        }

        updateArtefactHandlers();
    }

    private void updateArtefactHandlers() {
        // Cache the list as an array
        artefactHandlers = artefactHandlersByName.values().toArray(
                new ArtefactHandler[artefactHandlersByName.size()]);
    }

    /**
     * Returns all the classes identified as artefacts by ArtefactHandler instances.
     *
     * @return An array of classes
     */
    public Class<?>[] getAllArtefacts() {
        return allArtefactClassesArray;
    }

    protected Class<?>[] populateAllClasses() {
        allClasses = loadedClasses.toArray(new Class[loadedClasses.size()]);
        return allClasses;
    }

    /**
     * Configures the loaded classes within the GrailsApplication instance using the
     * registered ArtefactHandler instances.
     *
     * @param classes The classes to configure
     */
    protected void configureLoadedClasses(Class<?>[] classes) {

        initArtefactHandlers();

        artefactInfo.clear();
        allArtefactClasses.clear();
        allArtefactClassesArray = null;
        allClasses = classes;

        // first load the domain classes
        LOG.debug("Going to inspect artefact classes.");
        MetaClassRegistry metaClassRegistry = GroovySystem.getMetaClassRegistry();

        for (final Class<?> theClass : classes) {
            LOG.debug("Inspecting [" + theClass.getName() + "]");
            // start fresh
            metaClassRegistry.removeMetaClass(theClass);
            if (allArtefactClasses.contains(theClass)) {
                continue;
            }

            // check what kind of artefact it is and add to corrent data structure
            for (ArtefactHandler artefactHandler : artefactHandlers) {
                if (artefactHandler.isArtefact(theClass)) {
                    LOG.debug("Adding artefact " + theClass + " of kind " + artefactHandler.getType());
                    GrailsClass gclass = addArtefact(artefactHandler.getType(), theClass);
                    // Also maintain set of all artefacts (!= all classes loaded)
                    allArtefactClasses.add(theClass);

                    // Update per-artefact cache
                    DefaultArtefactInfo info = getArtefactInfo(artefactHandler.getType(), true);
                    info.addGrailsClass(gclass);
                    break;
                }
            }
        }

        refreshArtefactGrailsClassCaches();

        allArtefactClassesArray = allArtefactClasses.toArray(new Class[allArtefactClasses.size()]);

        // Tell all artefact handlers to init now we've worked out which classes are which artefacts
        for (ArtefactHandler artefactHandler : artefactHandlers) {
            initializeArtefacts(artefactHandler);
        }
    }

    /**
     * Tell all our artefact info objects to update their internal state after we've added a bunch of classes.
     */
    protected void refreshArtefactGrailsClassCaches() {
        for (Object o : artefactInfo.values()) {
            ((DefaultArtefactInfo) o).updateComplete();
        }
    }

    protected void addToLoaded(Class<?> clazz) {
        loadedClasses.add(clazz);
        populateAllClasses();
    }

    public Config getConfig() {
        if (config == null) {
            if (parentContext != null) {
                org.springframework.core.env.Environment environment = parentContext.getEnvironment();
                if (environment instanceof ConfigurableEnvironment) {
                    MutablePropertySources propertySources = ((ConfigurableEnvironment) environment).getPropertySources();
                    this.config = new PropertySourcesConfig(propertySources);
                } else {
                    this.config = new PropertySourcesConfig();
                }
            } else {
                this.config = new PropertySourcesConfig();
            }

            setConfig(this.config);
        }
        return config;
    }

    /**
     * Retrieves the number of artefacts registered for the given artefactType as defined by the ArtefactHandler.
     *
     * @param artefactType The type of the artefact as defined by the ArtefactHandler
     * @return The number of registered artefacts
     */
    protected int getArtefactCount(String artefactType) {
        ArtefactInfo info = getArtefactInfo(artefactType);
        return info == null ? 0 : info.getClasses().length;
    }

    /**
     * Retrieves all classes loaded by the GrailsApplication.
     *
     * @return All classes loaded by the GrailsApplication
     */
    public Class<?>[] getAllClasses() {
        return allClasses;
    }

    /**
     * Retrieves a class from the GrailsApplication for the given name.
     *
     * @param className The class name
     * @return Either the Class instance or null if it doesn't exist
     */
    public Class<?> getClassForName(String className) {
        if (!StringUtils.hasText(className)) {
            return null;
        }

        for (Class<?> c : allClasses) {
            if (c.getName().equals(className)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Refreshes this GrailsApplication, rebuilding all of the artefact definitions as
     * defined by the registered ArtefactHandler instances.
     */
    public void refresh() {
        if (classLoader instanceof GroovyClassLoader) {
            configureLoadedClasses(((GroovyClassLoader) classLoader).getLoadedClasses());
        }
    }

    public void rebuild() {
        initialised = false;
        loadedClasses.clear();
        initArtefactHandlers();

        if (GrailsUtil.isDevelopmentEnv()) {
            initialise();
        } else {
            throw new IllegalStateException("Cannot rebuild GrailsApplication when not in development mode!");
        }
    }

    /**
     * Retrieves the Spring Resource that was used to load the given Class.
     *
     * @param theClazz The class
     * @return Either a Spring Resource or null if no Resource was found for the given class
     */
    public Resource getResourceForClass(@SuppressWarnings("rawtypes") Class theClazz) {

        // TODO fix
        return null;
    }

    /**
     * Returns true if the given class is an artefact identified by one of the registered
     * ArtefactHandler instances. Uses class name equality to handle class reloading
     *
     * @param theClazz The class to check
     * @return true if it is an artefact
     */
    public boolean isArtefact(@SuppressWarnings("rawtypes") Class theClazz) {
        String className = theClazz.getName();
        for (Class<?> artefactClass : allArtefactClasses) {
            if (className.equals(artefactClass.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the specified class is of the given artefact type as defined by the ArtefactHandler.
     *
     * @param artefactType The type of the artefact
     * @param theClazz     The class
     * @return true if it is of the specified artefactType
     * @see grails.core.ArtefactHandler
     */
    public boolean isArtefactOfType(String artefactType, @SuppressWarnings("rawtypes") Class theClazz) {
        ArtefactHandler handler = artefactHandlersByName.get(artefactType);
        if (handler == null) {
            throw new GrailsConfigurationException(
                    "Unable to locate arefact handler for specified type: " + artefactType);
        }

        return handler.isArtefact(theClazz);
    }

    /**
     * Returns true if the specified class name is of the given artefact type as defined by the ArtefactHandler.
     *
     * @param artefactType The type of the artefact
     * @param className    The class name
     * @return true if it is of the specified artefactType
     * @see grails.core.ArtefactHandler
     */
    public boolean isArtefactOfType(String artefactType, String className) {
        return getArtefact(artefactType, className) != null;
    }

    /**
     * Retrieves an artefact for the given type and name.
     *
     * @param artefactType The artefact type as defined by a registered ArtefactHandler
     * @param name         The name of the class
     * @return A GrailsClass instance or null if none could be found for the given artefactType and name
     */
    public GrailsClass getArtefact(String artefactType, String name) {
        ArtefactInfo info = getArtefactInfo(artefactType);
        return info == null ? null : info.getGrailsClass(name);
    }

    public ArtefactHandler getArtefactType(@SuppressWarnings("rawtypes") Class theClass) {
        for (ArtefactHandler artefactHandler : artefactHandlers) {
            if (artefactHandler.isArtefact(theClass)) {
                return artefactHandler;
            }
        }
        return null;
    }

    protected GrailsClass getFirstArtefact(String artefactType) {
        ArtefactInfo info = getArtefactInfo(artefactType);
        // This will throw AIOB if we have none
        return info == null ? null : info.getGrailsClasses()[0];
    }

    /**
     * Returns all of the GrailsClass instances for the given artefactType as defined by the ArtefactHandler
     *
     * @param artefactType The type of the artefact defined by the ArtefactHandler
     * @return An array of classes for the given artefact
     */
    public GrailsClass[] getArtefacts(String artefactType) {
        return getArtefactInfo(artefactType, true).getGrailsClasses();
    }

    /**
     * Adds an artefact of the given type for the given Class.
     *
     * @param artefactType  The type of the artefact as defined by a ArtefactHandler instance
     * @param artefactClass A Class instance that matches the type defined by the ArtefactHandler
     * @return The GrailsClass if successful or null if it couldn't be added
     * @throws GrailsConfigurationException If the specified Class is not the same as the type defined by the ArtefactHandler
     * @see grails.core.ArtefactHandler
     */
    public GrailsClass addArtefact(String artefactType, @SuppressWarnings("rawtypes") Class artefactClass) {
        return addArtefact(artefactType, artefactClass, false);
    }

    /**
     * Adds an artefact of the given type for the given GrailsClass.
     *
     * @param artefactType        The type of the artefact as defined by a ArtefactHandler instance
     * @param artefactGrailsClass A GrailsClass instance that matches the type defined by the ArtefactHandler
     * @return The GrailsClass if successful or null if it couldn't be added
     * @throws GrailsConfigurationException If the specified GrailsClass is not the same as the type defined by the ArtefactHandler
     * @see grails.core.ArtefactHandler
     */
    public GrailsClass addArtefact(String artefactType, GrailsClass artefactGrailsClass) {
        ArtefactHandler handler = artefactHandlersByName.get(artefactType);
        if (handler.isArtefactGrailsClass(artefactGrailsClass)) {
            // Store the GrailsClass in cache
            DefaultArtefactInfo info = getArtefactInfo(artefactType, true);
            info.addGrailsClass(artefactGrailsClass);
            info.updateComplete();

            initializeArtefacts(artefactType);

            return artefactGrailsClass;
        }

        throw new GrailsConfigurationException("Cannot add " + artefactType + " class [" +
                artefactGrailsClass + "]. It is not a " + artefactType + "!");
    }

    /**
     * Registers a new ArtefactHandler that is responsible for identifying and managing a
     * particular artefact type that is defined by some convention.
     *
     * @param handler The ArtefactHandler to regster
     */
    public void registerArtefactHandler(ArtefactHandler handler) {
        GrailsApplicationAwareBeanPostProcessor.processAwareInterfaces(this, handler);
        artefactHandlersByName.put(handler.getType(), handler);
        updateArtefactHandlers();
    }

    public boolean hasArtefactHandler(String type) {
        return artefactHandlersByName.containsKey(type);
    }

    public ArtefactHandler[] getArtefactHandlers() {
        return artefactHandlers;
    }

    public ArtefactHandler getArtefactHandler(String type) {
        return artefactHandlersByName.get(type);
    }

    /**
     * Re-initialize the artefacts of the specified type. This gives handlers a chance to update caches etc.
     *
     * @param artefactType The type of artefact to init
     */
    protected void initializeArtefacts(String artefactType) {
        initializeArtefacts(artefactHandlersByName.get(artefactType));
    }

    /**
     * Clears the application returning it to an empty state. Very dangerous method, use with caution.
     */
    public void clear() {
        artefactHandlersByName.clear();
        updateArtefactHandlers();
        artefactInfo.clear();
        initialise();
    }

    /**
     * Re-initialize the artefacts of the specified type. This gives handlers a chance to update caches etc.
     *
     * @param handler The handler to register
     */
    protected void initializeArtefacts(ArtefactHandler handler) {
        if (handler == null) {
            return;
        }

        ArtefactInfo info = getArtefactInfo(handler.getType());
        // Only init those that have data
        if (info != null) {
            //System.out.println("Initialising artefacts of kind " + handler.getType() + " with registered artefacts" + info.getGrailsClassesByName());
            handler.initialize(info);
        }
    }

    /**
     * Get or create the cache of classes for the specified artefact type.
     *
     * @param artefactType The name of an artefact type
     * @param create       Set to true if you want non-existent caches to be created
     * @return The cache of classes for the type, or null if no cache exists and create is false
     */
    protected DefaultArtefactInfo getArtefactInfo(String artefactType, boolean create) {
        DefaultArtefactInfo cache = (DefaultArtefactInfo) artefactInfo.get(artefactType);
        if (cache == null && create) {
            cache = new DefaultArtefactInfo();
            artefactInfo.put(artefactType, cache);
            cache.updateComplete();
        }
        return cache;
    }

    /**
     * Get the cache of classes for the specified artefact type.
     *
     * @param artefactType The name of an artefact type
     * @return The cache of classes for the type, or null if no cache exists
     */
    public ArtefactInfo getArtefactInfo(String artefactType) {
        return getArtefactInfo(artefactType, false);
    }

    /**
     * <p>Overrides method invocation to return dynamic artefact methods.</p>
     * <p>We will support getXXXXClasses() and isXXXXClass(class)</p>
     *
     * @param methodName The name of the method
     * @param args       The arguments to the method
     * @return The return value of the method
     */
    // TODO Need to add matches for add<Artefact>Class(java.lang.Class) and add<Artefact>Class(GrailsClass)
    @Override
    public Object invokeMethod(String methodName, Object args) {

        Object[] argsv = (Object[]) args;

        Matcher match = GETCLASS_PATTERN.matcher(methodName);
        // look for getXXXXClass(y)
        match.find();
        if (match.matches()) {
            if (argsv.length > 0) {
                if (argsv[0] instanceof CharSequence) {
                    argsv[0] = argsv[0].toString();
                }
                if ((argsv.length != 1) || !(argsv[0] instanceof String)) {
                    throw new IllegalArgumentException(
                            "Dynamic method get<Artefact>Class(artefactName) requires a single String parameter");
                }
                return getArtefact(match.group(2), argsv[0].toString());
            }

            // It's a no-param getter
            return super.invokeMethod(methodName, args);
        }

        // look for isXXXXClass(y)
        match = ISCLASS_PATTERN.matcher(methodName);
        // find match
        match.find();
        if (match.matches()) {
            if ((argsv.length != 1) || !(argsv[0] instanceof Class<?>)) {
                throw new IllegalArgumentException(
                        "Dynamic method is<Artefact>Class(artefactClass) requires a single Class parameter");
            }

            return isArtefactOfType(match.group(2), (Class<?>) argsv[0]);
        }

        // look for getXXXXClasses
        match = GETCLASSESMETH_PATTERN.matcher(methodName);
        // find match
        match.find();
        if (match.matches()) {
            String artefactName = GrailsNameUtils.getClassNameRepresentation(match.group(2));
            if (artefactHandlersByName.containsKey(artefactName)) {
                return getArtefacts(match.group(2));
            }

            throw new IllegalArgumentException("Dynamic method get<Artefact>Classes() called for " +
                    "unrecognized artefact: " + match.group(2));
        }

        return super.invokeMethod(methodName, args);
    }

    /**
     * Override property access and hit on xxxxClasses to return class arrays of artefacts.
     *
     * @param propertyName The name of the property, if it ends in *Classes then match and invoke internal ArtefactHandler
     * @return All the artifacts or delegate to super.getProperty
     */
    @Override
    public Object getProperty(String propertyName) {
        // look for getXXXXClasses
        final Matcher match = GETCLASSESPROP_PATTERN.matcher(propertyName);
        // find match
        match.find();
        if (match.matches()) {
            String artefactName = GrailsNameUtils.getClassNameRepresentation(match.group(1));
            if (artefactHandlersByName.containsKey(artefactName)) {
                return getArtefacts(artefactName);
            }
        }
        return super.getProperty(propertyName);
    }

    public void initialise() {
        // get all the classes that were loaded
        if (LOG.isDebugEnabled()) {
            LOG.debug("loaded classes: [" + loadedClasses + "]");
        }
        Class<?>[] classes = populateAllClasses();
        configureLoadedClasses(classes);
        initialiseGroovyExtensionModules();
        initialised = true;
    }

    protected static void initialiseGroovyExtensionModules() {
        if (extensionMethodsInitialized) {
            return;
        }

        extensionMethodsInitialized = true;
        Map<CachedClass, List<MetaMethod>> map = new HashMap<CachedClass, List<MetaMethod>>();

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try {
            Enumeration<URL> resources = classLoader.getResources(ExtensionModuleScanner.MODULE_META_INF_FILE);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                if (url.getPath().contains("groovy-all")) {
                    // already registered
                    continue;
                }
                Properties properties = new Properties();
                InputStream inStream = null;
                try {
                    inStream = url.openStream();
                    properties.load(inStream);
                    ((MetaClassRegistryImpl) GroovySystem.getMetaClassRegistry()).registerExtensionModuleFromProperties(properties, classLoader, map);
                } catch (IOException e) {
                    throw new GroovyRuntimeException("Unable to load module META-INF descriptor", e);
                } finally {
                    if (inStream != null) {
                        inStream.close();
                    }
                }
            }
        } catch (IOException ignored) {
        }

        for (Map.Entry<CachedClass, List<MetaMethod>> moduleMethods : map.entrySet()) {
            CachedClass cls = moduleMethods.getKey();
            cls.addNewMopMethods(moduleMethods.getValue());
        }
    }

    // This is next call is equiv to getControllerByURI / getTagLibForTagName
    public GrailsClass getArtefactForFeature(String artefactType, Object featureID) {
        return artefactHandlersByName.get(artefactType).getArtefactForFeature(featureID);
    }

    public boolean isInitialised() {
        return initialised;
    }

    public GrailsClass getArtefactByLogicalPropertyName(String type, String logicalName) {
        ArtefactInfo info = getArtefactInfo(type);
        return info == null ? null : info.getGrailsClassByLogicalPropertyName(logicalName);
    }

    public void addArtefact(@SuppressWarnings("rawtypes") Class artefact) {
        for (ArtefactHandler artefactHandler : artefactHandlers) {
            if (artefactHandler.isArtefact(artefact)) {
                addArtefact(artefactHandler.getType(), artefact);
            }
        }
    }

    public void setBeanClassLoader(ClassLoader classLoader) {
        // do nothing
    }

    public void addOverridableArtefact(@SuppressWarnings("rawtypes") Class artefact) {
        for (ArtefactHandler artefactHandler : artefactHandlers) {
            if (artefactHandler.isArtefact(artefact)) {
                addOverridableArtefact(artefactHandler.getType(), artefact);
            }
        }
    }

    /**
     * Adds an artefact of the given type for the given Class.
     *
     * @param artefactType  The type of the artefact as defined by a ArtefactHandler instance
     * @param artefactClass A Class instance that matches the type defined by the ArtefactHandler
     * @return The GrailsClass if successful or null if it couldn't be added
     * @throws GrailsConfigurationException If the specified Class is not the same as the type defined by the ArtefactHandler
     * @see grails.core.ArtefactHandler
     */
    public GrailsClass addOverridableArtefact(String artefactType, @SuppressWarnings("rawtypes") Class artefactClass) {
        return addArtefact(artefactType, artefactClass, true);
    }

    protected GrailsClass addArtefact(String artefactType, Class<?> artefactClass, boolean overrideable) {
        ArtefactHandler handler = artefactHandlersByName.get(artefactType);
        if (handler != null && handler.isArtefact(artefactClass)) {
            GrailsClass artefactGrailsClass;
            if (handler instanceof DomainClassArtefactHandler) {
                artefactGrailsClass = ((DomainClassArtefactHandler) handler).newArtefactClass(artefactClass, proxyMappingContext);
            } else {
                artefactGrailsClass = handler.newArtefactClass(artefactClass);
            }
            artefactGrailsClass.setGrailsApplication(this);

            // Store the GrailsClass in cache
            DefaultArtefactInfo info = getArtefactInfo(artefactType, true);
            if (overrideable) {
                info.addOverridableGrailsClass(artefactGrailsClass);
            } else {
                info.addGrailsClass(artefactGrailsClass);
            }
            info.updateComplete();

            addToLoaded(artefactClass);

            if (isInitialised()) {
                initializeArtefacts(artefactType);
                ApplicationContext context = getMainContext();
                if (context instanceof ConfigurableApplicationContext && contextInitialized && ((ConfigurableApplicationContext) context).isActive()) {

                    context.publishEvent(new ArtefactAdditionEvent(artefactGrailsClass));
                }
            }

            return artefactGrailsClass;
        }

        throw new GrailsConfigurationException("Cannot add " + artefactType + " class [" +
                artefactClass + "]. It is not a " + artefactType + "!");
    }

    @Override
    public MappingContext getMappingContext() {
        if (mappingContext != null) {
            return mappingContext;
        }
        return proxyMappingContext;
    }

    @Override
    public void setMappingContext(MappingContext mappingContext) {
        this.mappingContext = mappingContext;
    }
}

