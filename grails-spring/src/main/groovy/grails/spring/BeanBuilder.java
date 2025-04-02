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
package grails.spring;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GString;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.GroovyShell;
import groovy.lang.MetaClass;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.grails.spring.BeanConfiguration;
import org.grails.spring.DefaultBeanConfiguration;
import org.grails.spring.DefaultRuntimeSpringConfiguration;
import org.grails.spring.RuntimeSpringConfiguration;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanDefinitionParsingException;
import org.springframework.beans.factory.parsing.EmptyReaderEventListener;
import org.springframework.beans.factory.parsing.FailFastProblemReporter;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.NullSourceExtractor;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.beans.factory.xml.BeanDefinitionParserDelegate;
import org.springframework.beans.factory.xml.DefaultNamespaceHandlerResolver;
import org.springframework.beans.factory.xml.NamespaceHandler;
import org.springframework.beans.factory.xml.NamespaceHandlerResolver;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.beans.factory.xml.XmlReaderContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.Assert;

/**
 * <p>Runtime bean configuration wrapper. Like a Groovy builder, but more of a DSL for
 * Spring configuration. Allows syntax like:</p>
 *
 * <pre>
 * import org.hibernate.SessionFactory
 * import org.apache.tomcat.jdbc.pool.DataSource
 *
 * BeanBuilder builder = new BeanBuilder()
 * builder.beans {
 *   dataSource(DataSource) {                      // invokeMethod
 *      driverClassName = "org.h2.Driver"
 *      url = "jdbc:h2:mem:grailsDB"
 *      username = "sa"                            // setProperty
 *      password = ""
 *      settings = [mynew:"setting"]
 *  }
 *  sessionFactory(SessionFactory) {
 *         dataSource = dataSource                 // getProperty for retrieving refs
 *  }
 *  myService(MyService) {
 *      nestedBean = { AnotherBean bean->          // setProperty with closure for nested bean
 *              dataSource = dataSource
 *      }
 *  }
 * }
 * </pre>
 * <p>
 *   You can also use the Spring IO API to load resources containing beans defined as a Groovy
 *   script using either the constructors or the loadBeans(Resource[] resources) method
 * </p>
 *
 * @author Graeme Rocher
 * @since 0.4
 *
 */
public class BeanBuilder extends GroovyObjectSupport {
    private static final Log LOG = LogFactory.getLog(BeanBuilder.class);
    private static final String CREATE_APPCTX = "createApplicationContext";
    private static final String REGISTER_BEANS = "registerBeans";
    private static final String BEANS = "beans";
    private static final String REF = "ref";
    private RuntimeSpringConfiguration springConfig;
    private BeanConfiguration currentBeanConfig;
    private Map<String, DeferredProperty> deferredProperties = new HashMap<String, DeferredProperty>();
    private ApplicationContext parentCtx;
    private Map<String, Object> binding = Collections.emptyMap();
    private ClassLoader classLoader = null;
    private NamespaceHandlerResolver namespaceHandlerResolver;
    private Map<String, NamespaceHandler> namespaceHandlers = new HashMap<String, NamespaceHandler>();
    private XmlBeanDefinitionReader xmlBeanDefinitionReader;
    private Map<String, String> namespaces = new HashMap<String, String>();
    private Resource beanBuildResource = new ByteArrayResource(new byte[0]);
    private XmlReaderContext readerContext;
    private ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

    public BeanBuilder() {
        this(null,null);
    }

    public BeanBuilder(ClassLoader classLoader) {
        this(null, classLoader);
    }

    public BeanBuilder(ApplicationContext parent) {
        this(parent, null);
    }

    public BeanBuilder(ApplicationContext parent,ClassLoader classLoader) {
        this(parent, null, classLoader);
    }

    public BeanBuilder(ApplicationContext parentCtx, RuntimeSpringConfiguration springConfig,  ClassLoader classLoader) {
        this.springConfig = springConfig == null ? createRuntimeSpringConfiguration(parentCtx, classLoader) : springConfig;
        this.parentCtx = parentCtx;
        this.classLoader = classLoader;
        initializeSpringConfig();
    }

    public void setResourcePatternResolver(ResourcePatternResolver resourcePatternResolver) {
        Assert.notNull(resourcePatternResolver, "The argument [resourcePatternResolver] cannot be null");
        this.resourcePatternResolver = resourcePatternResolver;
    }

    protected void initializeSpringConfig() {
        xmlBeanDefinitionReader = new XmlBeanDefinitionReader((GenericApplicationContext)springConfig.getUnrefreshedApplicationContext());
        initializeBeanBuilderForClassLoader(classLoader);
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader == null ? getClass().getClassLoader() : classLoader;
        initializeBeanBuilderForClassLoader(classLoader);
    }

    protected void initializeBeanBuilderForClassLoader(ClassLoader classLoader) {
        xmlBeanDefinitionReader.setBeanClassLoader(classLoader);
        namespaceHandlerResolver = new DefaultNamespaceHandlerResolver(this.classLoader);
        readerContext = new XmlReaderContext(beanBuildResource, new FailFastProblemReporter(), new EmptyReaderEventListener(),
                new NullSourceExtractor(), xmlBeanDefinitionReader, namespaceHandlerResolver);
    }

    public void setNamespaceHandlerResolver(NamespaceHandlerResolver namespaceHandlerResolver) {
        this.namespaceHandlerResolver = namespaceHandlerResolver;
    }

    protected RuntimeSpringConfiguration createRuntimeSpringConfiguration(ApplicationContext parent, ClassLoader cl) {
        return new DefaultRuntimeSpringConfiguration(parent, cl);
    }

    public Log getLog() {
        return LOG;
    }

    /**
     * Imports Spring bean definitions from either XML or Groovy sources into the current bean builder instance
     *
     * @param resourcePattern The resource pattern
     */
    public void importBeans(String resourcePattern) {
        try {
            Resource[] resources = resourcePatternResolver.getResources(resourcePattern);
            for (Resource resource : resources) {
                importBeans(resource);
            }
        } catch (IOException e) {
            LOG.error("Error loading beans for resource pattern: " + resourcePattern, e);
        }
    }

    public void importBeans(Resource resource) {
        if (resource.getFilename().endsWith(".groovy")) {
            loadBeans(resource);
        }
        else if (resource.getFilename().endsWith(".xml")) {
            SimpleBeanDefinitionRegistry beanRegistry = new SimpleBeanDefinitionRegistry();
            XmlBeanDefinitionReader beanReader = new XmlBeanDefinitionReader(beanRegistry);
            beanReader.loadBeanDefinitions(resource);
            String[] beanNames = beanRegistry.getBeanDefinitionNames();
            for (String beanName : beanNames) {
                springConfig.addBeanDefinition(beanName, beanRegistry.getBeanDefinition(beanName));
            }
        }
    }

    /**
     * Defines a Spring namespace definition to use.
     *
     * @param definition The definition
     */
    public void xmlns(Map<String, String> definition) {
        Assert.notNull(namespaceHandlerResolver, "You cannot define a Spring namespace without a [namespaceHandlerResolver] set");
        if (definition.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : definition.entrySet()) {
            String namespace = entry.getKey();
            String uri = entry.getValue() == null ? null : entry.getValue();

            Assert.notNull(uri, "Namespace definition cannot supply a null URI");

            final NamespaceHandler namespaceHandler = namespaceHandlerResolver.resolve(uri);
            if (namespaceHandler == null) {
                throw new BeanDefinitionParsingException(
                      new Problem("No namespace handler found for URI: " + uri,
                            new Location(readerContext.getResource())));
            }
            namespaceHandlers.put(namespace, namespaceHandler);
            namespaces.put(namespace, uri);
        }
    }

    /**
     * Retrieves the parent ApplicationContext
     * @return The parent ApplicationContext
     */
    public ApplicationContext getParentCtx() {
        return parentCtx;
    }

    /**
     * Retrieves the RuntimeSpringConfiguration instance used the the BeanBuilder
     * @return The RuntimeSpringConfiguration instance
     */
    public RuntimeSpringConfiguration getSpringConfig() {
        return springConfig;
    }

    /**
     * Retrieves a BeanDefinition for the given name
     * @param name The bean definition
     * @return The BeanDefinition instance
     */
    public BeanDefinition getBeanDefinition(String name) {
        if (!getSpringConfig().containsBean(name)) {
            return null;
        }
        return getSpringConfig().getBeanConfig(name).getBeanDefinition();
    }

    /**
     * Retrieves all BeanDefinitions for this BeanBuilder
     *
     * @return A map of BeanDefinition instances with the bean id as the key
     */
    public Map<String, BeanDefinition> getBeanDefinitions() {
        Map<String, BeanDefinition> beanDefinitions = new HashMap<String, BeanDefinition>();
        for (String beanName : getSpringConfig().getBeanNames()) {
            beanDefinitions.put(beanName, getSpringConfig().getBeanConfig(beanName).getBeanDefinition());
        }
        return beanDefinitions;
    }

    /**
     * Sets the runtime Spring configuration instance to use. This is not necessary to set
     * and is configured to default value if not, but is useful for integrating with other
     * spring configuration mechanisms @see org.codehaus.groovy.grails.commons.spring.GrailsRuntimeConfigurator
     *
     * @param springConfig The spring config
     */
    public void setSpringConfig(RuntimeSpringConfiguration springConfig) {
        this.springConfig = springConfig;
        initializeSpringConfig();
    }

    /**
     * Defers the adding of a property to a bean definition until later.
     * This is for a case where you assign a property to a list that may not contain bean references at
     * that point of asignment, but may later hence it would need to be managed
     *
     * @author Graeme Rocher
     */
    private class DeferredProperty {
        private BeanConfiguration config;
        private String name;
        private Object value;

        DeferredProperty(BeanConfiguration config, String name, Object value) {
            this.config = config;
            this.name = name;
            this.value = value;
        }

        public void setInBeanConfig() {
            config.addProperty(name, value);
        }
    }

    /**
     * Adds new properties to runtime references.
     *
     * @author Graeme Rocher
     * @since 0.4
     */
    private class ConfigurableRuntimeBeanReference extends RuntimeBeanReference implements GroovyObject {

        private MetaClass metaClass;
        private BeanConfiguration beanConfig;

        public ConfigurableRuntimeBeanReference(String beanName, BeanConfiguration beanConfig, boolean toParent) {
            super(beanName, toParent);
            Assert.notNull(beanConfig, "Argument [beanConfig] cannot be null");
            this.beanConfig = beanConfig;
            metaClass = InvokerHelper.getMetaClass(this);
        }

        public MetaClass getMetaClass() {
            return metaClass;
        }

        public Object getProperty(String property) {
            if (property.equals("beanName")) {
                return getBeanName();
            }
            if (property.equals("source")) {
                return getSource();
            }
            if (beanConfig != null) {
                return new WrappedPropertyValue(property, beanConfig.getPropertyValue(property));
            }
            return metaClass.getProperty(this, property);
        }

        /**
         * Wraps a BeanConfiguration property an ensures that any RuntimeReference additions to it are
         * deferred for resolution later.
         */
        private class WrappedPropertyValue extends GroovyObjectSupport {
            private Object propertyValue;
            private String propertyName;
            public WrappedPropertyValue(String propertyName, Object propertyValue) {
                this.propertyValue = propertyValue;
                this.propertyName = propertyName;
            }

            @SuppressWarnings("unused")
            public void leftShift(Object value) {
                InvokerHelper.invokeMethod(propertyValue, "leftShift", value);
                updateDeferredProperties(value);
            }

            @SuppressWarnings("unused")
            public boolean add(Object value) {
                boolean retval = (Boolean) InvokerHelper.invokeMethod(propertyValue, "add", value);
                updateDeferredProperties(value);
                return retval;
            }

            @SuppressWarnings("unused")
            public boolean addAll(@SuppressWarnings("rawtypes") Collection values) {
                boolean retval = (Boolean) InvokerHelper.invokeMethod(propertyValue, "addAll", values);
                for (Object value : values) {
                    updateDeferredProperties(value);
                }
                return retval;
            }

            @Override
            public Object invokeMethod(String name, Object args) {
                return InvokerHelper.invokeMethod(propertyValue, name, args);
            }

            @Override
            public Object getProperty(String name) {
                return InvokerHelper.getProperty(propertyValue, name);
            }

            @Override
            public void setProperty(String name, Object value) {
                InvokerHelper.setProperty(propertyValue, name, value);
            }

            private void updateDeferredProperties(Object value) {
                if (value instanceof RuntimeBeanReference) {
                    deferredProperties.put(beanConfig.getName(), new DeferredProperty(beanConfig, propertyName, propertyValue));
                }
            }
        }

        public Object invokeMethod(String name, Object args) {
            return metaClass.invokeMethod(this, name, args);
        }

        public void setMetaClass(MetaClass metaClass) {
            this.metaClass = metaClass;
        }

        public void setProperty(String property, Object newValue) {
            if (!addToDeferred(beanConfig,property, newValue)) {
                beanConfig.setPropertyValue(property, newValue);
            }
        }
    }

    /**
     * Takes a resource pattern as (@see org.springframework.core.io.support.PathMatchingResourcePatternResolver)
     * This allows you load multiple bean resources in this single builder
     *
     * eg loadBeans("classpath:*Beans.groovy")
     *
     * @param resourcePattern The resource pattern
     * @throws IOException When the path cannot be matched
     */
    public void loadBeans(String resourcePattern) throws IOException {
        loadBeans(new PathMatchingResourcePatternResolver().getResources(resourcePattern));
    }

    /**
     * Loads a single Resource into the bean builder
     *
     * @param resource The resource to load
     */
    public void loadBeans(Resource resource) {
        beanBuildResource = resource;
        loadBeans(new Resource[]{resource});
    }

    /**
     * Loads a set of given beans.
     * @param resources The resources to load
     * @throws BeanDefinitionParsingException Thrown if there is an error reading one of the passes resources
     */
    public void loadBeans(Resource[] resources) {
        @SuppressWarnings("rawtypes") Closure beans = new Closure(this) {
            private static final long serialVersionUID = -2778328821635253740L;

            @Override
            public Object call(Object... args) {
                invokeBeanDefiningClosure((Closure)args[0]);
                return null;
            }
        };

        Binding b = new Binding() {
            @Override
            public void setVariable(String name, Object value) {
                if (currentBeanConfig == null) {
                    super.setVariable(name, value);
                }
                else {
                    setPropertyOnBeanConfig(name, value);
                }
            }
        };
        b.setVariable("beans", beans);

        for (Resource resource : resources) {
            try {
                GroovyShell shell = classLoader == null ? new GroovyShell(b) : new GroovyShell(classLoader, b);
                shell.evaluate(new InputStreamReader(resource.getInputStream(), "UTF-8"));
            }
            catch (Throwable e) {
                throw new BeanDefinitionParsingException(
                        new Problem("Error evaluating bean definition script: " + e.getMessage(), new Location(resource), null, e));
            }
        }
    }

    /**
     * Register a set of beans with the given bean registry. Most
     * application contexts are bean registries.
     */
    public void registerBeans(BeanDefinitionRegistry registry) {
        finalizeDeferredProperties();

        if (registry instanceof GenericApplicationContext) {
            GenericApplicationContext ctx = (GenericApplicationContext) registry;
            ctx.setClassLoader(classLoader);
            ctx.getBeanFactory().setBeanClassLoader(classLoader);
        }

        springConfig.registerBeansWithRegistry(registry);
    }

    /**
     * Registers bean definitions with another instance of RuntimeSpringConfiguration, overriding any beans in the target.
     *
     * @param targetSpringConfig The RuntimeSpringConfiguration object
     */
    public void registerBeans(RuntimeSpringConfiguration targetSpringConfig) {
        springConfig.registerBeansWithConfig(targetSpringConfig);
    }

    /**
     * Overrides method invocation to create beans for each method name that takes a class argument.
     */
    @Override
    public Object invokeMethod(String name, Object arg) {
        Object[] args = (Object[])arg;

        if (CREATE_APPCTX.equals(name)) {
            return createApplicationContext();
        }

        if (REGISTER_BEANS.equals(name) && args.length == 1 && args[0] instanceof GenericApplicationContext) {
            registerBeans((GenericApplicationContext)args[0]);
            return null;
        }

        if (BEANS.equals(name) && args.length == 1 && args[0] instanceof Closure) {
            return beans((Closure<?>)args[0]);
        }

        if (REF.equals(name)) {
            String refName;
            Assert.notNull(args[0], "Argument to ref() is not a valid bean or was not found");

            if (args[0] instanceof RuntimeBeanReference) {
                refName = ((RuntimeBeanReference)args[0]).getBeanName();
            }
            else {
                refName = args[0].toString();
            }

            boolean parentRef = false;
            if (args.length > 1) {
                if (args[1] instanceof Boolean) {
                    parentRef = (Boolean) args[1];
                }
            }
            return new RuntimeBeanReference(refName, parentRef);
        }

        if (namespaceHandlers.containsKey(name) && args.length > 0 && (args[0] instanceof Closure)) {
            createDynamicElementReader(name, true).invokeMethod("doCall", args);
            return this;
        }

        if (args.length > 0 && args[0] instanceof Closure) {
            // abstract bean definition
            return invokeBeanDefiningMethod(name, args);
        }

        if (args.length > 0 && args[0] instanceof Class || args.length > 0 && args[0] instanceof RuntimeBeanReference || args.length > 0 && args[0] instanceof Map) {
            return invokeBeanDefiningMethod(name, args);
        }

        if (args.length > 1 && args[args.length - 1] instanceof Closure) {
            return invokeBeanDefiningMethod(name, args);
        }

        ApplicationContext ctx = springConfig.getUnrefreshedApplicationContext();
        MetaClass mc = DefaultGroovyMethods.getMetaClass(ctx);
        if (!mc.respondsTo(ctx, name, args).isEmpty()) {
            return mc.invokeMethod(ctx,name, args);
        }

        return this;
    }

    /**
     * Defines a set of beans for the given block or closure.
     *
     * @param c The block or closure
     * @return This BeanBuilder instance
     */
    public BeanBuilder beans(Closure<?> c) {
        return invokeBeanDefiningClosure(c);
    }

    /**
     * Creates an ApplicationContext from the current state of the BeanBuilder
     * @return The ApplicationContext instance
     */
    public ApplicationContext createApplicationContext() {
        finalizeDeferredProperties();
        return springConfig.getApplicationContext();
    }

    protected void finalizeDeferredProperties() {
        for (DeferredProperty dp : deferredProperties.values()) {
            if (dp.value instanceof List) {
                dp.value = manageListIfNecessary(dp.value);
            }
            else if (dp.value instanceof Map) {
                dp.value = manageMapIfNecessary(dp.value);
            }
            dp.setInBeanConfig();
        }
        deferredProperties.clear();
    }

    protected boolean addToDeferred(BeanConfiguration beanConfig, String property, Object newValue) {
        if (newValue instanceof List) {
            deferredProperties.put(currentBeanConfig.getName() + property,
                    new DeferredProperty(currentBeanConfig, property, newValue));
            return true;
        }

        if (newValue instanceof Map) {
            deferredProperties.put(currentBeanConfig.getName() + property,
                    new DeferredProperty(currentBeanConfig, property, newValue));
            return true;
        }

        return false;
    }

    /**
     * Called when a bean definition node is called.
     *
     * @param name The name of the bean to define
     * @param args The arguments to the bean. The first argument is the class name, the last argument is sometimes a closure. All
     * the arguments in between are constructor arguments
     * @return The bean configuration instance
     */
    protected BeanConfiguration invokeBeanDefiningMethod(String name, Object[] args) {

        boolean hasClosureArgument = args[args.length - 1] instanceof Closure;
        if (args[0] instanceof Class) {
            Class<?> beanClass = args[0] instanceof Class ? (Class<?>)args[0] : args[0].getClass();

            if (args.length >= 1) {
                if (hasClosureArgument) {
                    if (args.length - 1 != 1) {
                        currentBeanConfig = springConfig.addSingletonBean(name, beanClass, resolveConstructorArguments(args, 1, args.length - 1));
                    }
                    else {
                        currentBeanConfig = springConfig.addSingletonBean(name, beanClass);
                    }
                }
                else {
                    currentBeanConfig = springConfig.addSingletonBean(name, beanClass, resolveConstructorArguments(args, 1, args.length));
                }
            }

        }
        else if (args[0] instanceof RuntimeBeanReference) {
            currentBeanConfig = springConfig.addSingletonBean(name);
            currentBeanConfig.setFactoryBean(((RuntimeBeanReference)args[0]).getBeanName());
        }
        else if (args[0] instanceof Map) {
            // named constructor arguments
            if (args.length > 1 && args[1] instanceof Class) {
                List<?> constructorArgs = resolveConstructorArguments(args, 2, hasClosureArgument ? args.length - 1 :  args.length);
                currentBeanConfig = springConfig.addSingletonBean(name, (Class<?>)args[1], constructorArgs);

                @SuppressWarnings("rawtypes")
                Map namedArgs = (Map)args[0];
                for (Object o : namedArgs.keySet()) {
                    String propName = (String) o;
                    setProperty(propName, namedArgs.get(propName));
                }
            }
            // factory method syntax
            else {
                //First arg is the map containing factoryBean : factoryMethod
                @SuppressWarnings("rawtypes")
                Map.Entry factoryBeanEntry = (Map.Entry)((Map)args[0]).entrySet().iterator().next();
                // If we have a closure body, that will be the last argument.
                // In between are the constructor args
                int constructorArgsTest = hasClosureArgument?2:1;
                // If we have more than this number of args, we have constructor args
                if (args.length > constructorArgsTest) {
                    //factory-method requires args
                    int endOfConstructArgs = hasClosureArgument ? args.length - 1 : args.length;
                    currentBeanConfig = springConfig.addSingletonBean(name, null, resolveConstructorArguments(args, 1, endOfConstructArgs));
                } else {
                    currentBeanConfig = springConfig.addSingletonBean(name);
                }
                currentBeanConfig.setFactoryBean(factoryBeanEntry.getKey().toString());
                currentBeanConfig.setFactoryMethod(factoryBeanEntry.getValue().toString());
            }
        }
        else if (args[0] instanceof Closure) {
            currentBeanConfig = springConfig.addAbstractBean(name);
        }
        else {
            List<?> constructorArgs = resolveConstructorArguments(args, 0, hasClosureArgument ? args.length - 1 : args.length);
            currentBeanConfig = new DefaultBeanConfiguration(name, null, constructorArgs);
            springConfig.addBeanConfiguration(name,currentBeanConfig);
        }

        if (hasClosureArgument) {
            Closure<?> callable = (Closure<?>)args[args.length - 1];
            callable.setDelegate(this);
            callable.setResolveStrategy(Closure.DELEGATE_FIRST);
            callable.call(new Object[]{currentBeanConfig});
        }

        BeanConfiguration beanConfig = currentBeanConfig;
        currentBeanConfig = null;
        return beanConfig;
    }

    @SuppressWarnings("rawtypes")
    protected List resolveConstructorArguments(Object[] args, int start, int end) {
        Object[] constructorArgs = subarray(args, start, end);
        filterGStringReferences(constructorArgs);
        for (int i = 0; i < constructorArgs.length; i++) {
            if (constructorArgs[i] instanceof List) {
                constructorArgs[i] = manageListIfNecessary(constructorArgs[i]);
            } else if (constructorArgs[i] instanceof Map) {
                constructorArgs[i] = manageMapIfNecessary(constructorArgs[i]);
            }
        }
        return Arrays.asList(constructorArgs);
    }

    protected Object[] subarray(Object[] args, int i, int j) {
        Assert.isTrue(j <= args.length, "Upper bound can't be greater than array length");
        Object[] b = new Object[j - i];
        int n = 0;
        for (int k = i;  k < j; k++,n++) {
            b[n] = args[k];
        }
        return b;
    }

    protected void filterGStringReferences(Object[] constructorArgs) {
        for (int i = 0; i < constructorArgs.length; i++) {
            Object constructorArg = constructorArgs[i];
            if (constructorArg instanceof GString) {
                constructorArgs[i] = constructorArg.toString();
            }
        }
    }

    /**
     * When an method's argument is only a closure it is a set of bean definitions.
     *
     * @param callable The closure argument
     * @return This BeanBuilder instance
     */
    protected BeanBuilder invokeBeanDefiningClosure(Closure<?> callable) {

        callable.setDelegate(this);
//        callable.setResolveStrategy(Closure.DELEGATE_FIRST);
        callable.call();
        finalizeDeferredProperties();

        return this;
    }

    /**
     * Overrides property setting in the scope of the BeanBuilder to set
     * properties on the current BeanConfiguration.
     */
    @Override
    public void setProperty(String name, Object value) {
        if (currentBeanConfig != null) {
            setPropertyOnBeanConfig(name, value);
        }
    }

    /**
     * Defines an inner bean definition.
     *
     * @param type The bean type
     * @return The bean definition
     */
    public AbstractBeanDefinition bean(Class<?> type) {
        return springConfig.createSingletonBean(type).getBeanDefinition();
    }

    /**
     * Defines an inner bean definition.
     *
     * @param type The bean type
     * @param args The constructors arguments and closure configurer
     * @return The bean definition
     */
    @SuppressWarnings("rawtypes")
    public AbstractBeanDefinition bean(Class type, Object...args) {
        BeanConfiguration current = currentBeanConfig;
        try {
            Closure callable = null;
            Collection constructorArgs = null;
            if (args != null && args.length > 0) {
                int index = args.length;
                Object lastArg = args[index-1];

                if (lastArg instanceof Closure) {
                    callable = (Closure) lastArg;
                    index--;
                }
                if (index > -1) {
                    constructorArgs = resolveConstructorArguments(args, 0, index);
                }
            }
            currentBeanConfig =  constructorArgs == null ? springConfig.createSingletonBean(type) : springConfig.createSingletonBean(type, constructorArgs);
            if (callable != null) {
                callable.call(new Object[]{currentBeanConfig});
            }
            return currentBeanConfig.getBeanDefinition();

        }
        finally {
            currentBeanConfig = current;
        }
    }

    protected void setPropertyOnBeanConfig(String name, Object value) {
        if (value instanceof GString) {
            value = value.toString();
        }
        if (addToDeferred(currentBeanConfig, name, value)) {
            return;
        }

        if (value instanceof Closure) {
            BeanConfiguration current = currentBeanConfig;
            try {
                Closure<?> callable = (Closure<?>)value;

                Class<?> parameterType = callable.getParameterTypes()[0];
                if (parameterType.equals(Object.class)) {
                    currentBeanConfig = springConfig.createSingletonBean("");
                    callable.call(new Object[]{currentBeanConfig});
                }
                else {
                    currentBeanConfig = springConfig.createSingletonBean(parameterType);
                    callable.call();
                }

                value = currentBeanConfig.getBeanDefinition();
            }
            finally {
                currentBeanConfig = current;
            }
        }
        currentBeanConfig.addProperty(name, value);
    }

    /**
     * Checks whether there are any runtime refs inside a Map and converts
     * it to a ManagedMap if necessary.
     *
     * @param value The current map
     * @return A ManagedMap or a normal map
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Object manageMapIfNecessary(Object value) {
        Map map = (Map)value;
        boolean containsRuntimeRefs = false;
        for (Object e : map.values()) {
            if (e instanceof RuntimeBeanReference) {
                containsRuntimeRefs = true;
                break;
            }
        }
        if (containsRuntimeRefs) {
            Map managedMap = new ManagedMap();
            managedMap.putAll(map);
            return managedMap;
        }
        return value;
    }

    /**
     * Checks whether there are any runtime refs inside the list and
     * converts it to a ManagedList if necessary.
     *
     * @param value The object that represents the list
     * @return Either a new list or a managed one
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Object manageListIfNecessary(Object value) {
        List list = (List)value;
        boolean containsRuntimeRefs = false;
        for (Object e : list) {
            if (e instanceof RuntimeBeanReference) {
                containsRuntimeRefs = true;
                break;
            }
        }
        if (containsRuntimeRefs) {
            List tmp = new ManagedList();
            tmp.addAll((List)value);
            value = tmp;
        }
        return value;
    }

    /**
     * Overrides property retrieval in the scope of the BeanBuilder to either:
     *
     * a) Retrieve a variable from the bean builder's binding if it exists
     * b) Retrieve a RuntimeBeanReference for a specific bean if it exists
     * c) Otherwise just delegate to super.getProperty which will resolve properties from the BeanBuilder itself
     */
    @Override
    public Object getProperty(String name) {

        if (binding.containsKey(name)) {
            return binding.get(name);
        }

        if (namespaceHandlers.containsKey(name)) {
            return createDynamicElementReader(name, currentBeanConfig != null);
        }

        if (springConfig.containsBean(name)) {
            BeanConfiguration beanConfig = springConfig.getBeanConfig(name);
            if (beanConfig != null) {
                return new ConfigurableRuntimeBeanReference(name, springConfig.getBeanConfig(name) ,false);
            }
            return new RuntimeBeanReference(name,false);
        }

        // this is to deal with the case where the property setter is the last
        // statement in a closure (hence the return value)
        if (currentBeanConfig != null) {
            if (currentBeanConfig.hasProperty(name)) {
                return currentBeanConfig.getPropertyValue(name);
            }
            DeferredProperty dp = deferredProperties.get(currentBeanConfig.getName()+name);
            if (dp != null) {
                return dp.value;
            }
            return super.getProperty(name);
        }

        return super.getProperty(name);
    }

    protected DynamicElementReader createDynamicElementReader(String namespace, final boolean decorator) {
        NamespaceHandler handler = namespaceHandlers.get(namespace);
        ParserContext parserContext = new ParserContext(readerContext, new BeanDefinitionParserDelegate(readerContext));
        final DynamicElementReader dynamicElementReader = new DynamicElementReader(namespace, namespaces, handler, parserContext) {
            @Override
            protected void afterInvocation() {
                if (!decorator) {
                    currentBeanConfig = null;
                }
            }
        };
        dynamicElementReader.setClassLoader(classLoader);
        if (currentBeanConfig != null) {
            dynamicElementReader.setBeanConfiguration(currentBeanConfig);
        }
        else if (!decorator) {
            currentBeanConfig = new DefaultBeanConfiguration(namespace);
            dynamicElementReader.setBeanConfiguration(currentBeanConfig);
        }
        dynamicElementReader.setBeanDecorator(decorator);
        return dynamicElementReader;
    }

    /**
     * Sets the binding (the variables available in the scope of the BeanBuilder).
     * @param b The Binding instance
     */
    @SuppressWarnings("unchecked")
    public void setBinding(Binding b) {
        binding = b.getVariables();
    }
}
