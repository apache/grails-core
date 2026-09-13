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
package org.grails.support

import jakarta.servlet.ServletContext
import java.lang.annotation.Annotation
import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic
import org.jspecify.annotations.NonNull
import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEvent
import org.springframework.context.MessageSource
import org.springframework.context.MessageSourceResolvable
import org.springframework.context.NoSuchMessageException
import org.springframework.core.ResolvableType
import org.springframework.core.env.Environment
import org.springframework.core.io.AbstractResource
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.mock.web.MockServletContext
import org.springframework.util.AntPathMatcher
import org.springframework.util.PathMatcher
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.support.StandardServletEnvironment

import grails.util.GrailsStringUtils

@CompileStatic
class MockApplicationContext extends GroovyObjectSupport implements WebApplicationContext {

    Date startupDate = new Date()
    Map<String, Object> beans = new HashMap<>()
    List<Resource> resources = new ArrayList<>()
    List<String> ignoredClassLocations = new ArrayList<>()
    PathMatcher pathMatcher = new AntPathMatcher()
    ServletContext servletContext = new MockServletContext()

    void registerMockBean(String name, Object instance) {
        beans.put(name, instance)
    }

    /**
     * Registers a mock resource. Path separator: "/"
     * @param location the location of the resource. Example: /WEB-INF/grails-app/i18n/messages.properties
     */
    void registerMockResource(String location) {
        resources.add(new ClassPathResource(GrailsStringUtils.trimStart(location, '/')))
    }

    /**
     * Registers a mock resource. Path separator: "/"
     * @param location the location of the resource. Example: /WEB-INF/grails-app/i18n/messages.properties
     */
    void registerMockResource(String location, String contents) {
        resources.add(new MockResource(GrailsStringUtils.trimStart(location, '/'), contents))
    }

    /**
     * Unregisters a mock resource. Path separator: "/"
     * @param location the location of the resource. Example: /WEB-INF/grails-app/i18n/messages.properties
     */
    void unregisterMockResource(String location) {
        for (Iterator<Resource> it = resources.iterator(); it.hasNext();) {
            MockResource mockResource = (MockResource) it.next()
            if (mockResource.location.equals(location)) {
                it.remove()
            }
        }
    }

    /**
     * Registers a resource that should not be found on the classpath. Path separator: "/"
     * @param location the location of the resource. Example: /WEB-INF/grails-app/i18n/messages.properties
     */
    void registerIgnoredClassPathLocation(String location) {
        ignoredClassLocations.add(location)
    }

    /**
     * Unregisters a resource that should not be found on the classpath. Path separator: "/"
     * @param location the location of the resource. Example: /WEB-INF/grails-app/i18n/messages.properties
     */
    void unregisterIgnoredClassPathLocation(String location) {
        ignoredClassLocations.remove(location)
    }

    ApplicationContext getParent() {
        throw new UnsupportedOperationException('Method not supported by implementation')
    }

    String getId() {
        return 'MockApplicationContext'
    }

    String getApplicationName() {
        return getId()
    }

    String getDisplayName() {
        return getId()
    }

    long getStartupDate() {
        return startupDate.getTime()
    }

    void publishEvent(ApplicationEvent event) {
        // do nothing
    }

    @Override
    void publishEvent(Object event) {

    }

    boolean containsBeanDefinition(String beanName) {
        return beans.containsKey(beanName)
    }

    int getBeanDefinitionCount() {
        return beans.size()
    }

    String[] getBeanDefinitionNames() {
        return beans.keySet().toArray(new String[beans.keySet().size()])
    }

    @Override
    def <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit) {
        return getBeanProvider(requiredType)
    }

    @Override
    def <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit) {
        return getBeanProvider(requiredType)
    }

    @Override
    String[] getBeanNamesForType(ResolvableType type) {
        return new String[0]
    }

    @Override
    String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
        return new String[0]
    }

    @SuppressWarnings([ 'unchecked', 'rawtypes' ])
    String[] getBeanNamesForType(Class type) {
        List<String> beanNames = new ArrayList<>()
        for (String beanName in beans.keySet()) {
            if (type.isAssignableFrom(beans.get(beanName).getClass())) {
                beanNames.add(beanName)
            }
        }
        return beanNames.toArray(new String[beanNames.size()])
    }

    @SuppressWarnings('rawtypes')
    String[] getBeanNamesForType(Class type, boolean includePrototypes, boolean includeFactoryBeans) {
        return getBeanNamesForType(type)
    }

    @SuppressWarnings('unchecked')
    def <T> Map<String, T> getBeansOfType(Class<T> type) throws BeansException {
        String[] beanNames = getBeanNamesForType(type)
        Map<String, T> newMap = new HashMap<>()
        for (int i = 0; i < beanNames.length; i++) {
            String beanName = beanNames[i]
            newMap.put(beanName, (T) getBean(beanName))
        }
        return newMap
    }

    def <T> Map<String, T> getBeansOfType(Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException {
        return getBeansOfType(type)
    }

    def <A extends Annotation> A findAnnotationOnBean(String name, Class<A> annotation) {
        Object o = getBean(name)
        if (o != null) {
            return o.getClass().getAnnotation(annotation)
        }
        return null
    }

    @Override
    def <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
        return findAnnotationOnBean(beanName, annotationType)
    }

    Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotation) throws BeansException {
        Map<String, Object> submap = new HashMap<>()
        for (Object beanName in beans.keySet()) {
            Object bean = beans.get(beanName)
            if (bean != null && bean.getClass().getAnnotation(annotation) != null) {
                submap.put(beanName.toString(), bean)
            }
        }
        return submap
    }

    @NonNull
    @Override
    def <A extends Annotation> Set<A> findAllAnnotationsOnBean(@NonNull String beanName, @NonNull Class<A> annotationType, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
        throw new UnsupportedOperationException('This method was added in Spring 6 API but has not yet been implemented in Grails MockApplicationContext')
    }

    /**
     * Find all names of beans whose {@code Class} has the supplied {@link Annotation}
     * type, without creating any bean instances yet.
     * @param annotationType the type of annotation to look for
     * @return the names of all matching beans
     * @since 2.4
     */
    String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
        List<String> beanNamesList = new ArrayList<>()
        for (Object beanName in beans.keySet()) {
            Object bean = beans.get(beanName)
            if (bean != null && bean.getClass().getAnnotation(annotationType) != null) {
                beanNamesList.add(beanName.toString())
            }
        }
        return beanNamesList.toArray(new String[beanNamesList.size()])
    }

    Object getBean(String name) throws BeansException {
        if (!beans.containsKey(name)) {
            throw new NoSuchBeanDefinitionException(name)
        }
        return beans.get(name)
    }

    @SuppressWarnings('unchecked')
    def <T> T getBean(String name, Class<T> requiredType) throws BeansException {
        if (!beans.containsKey(name)) {
            throw new NoSuchBeanDefinitionException(name)
        }

        if (requiredType != null && !requiredType.isAssignableFrom(beans.get(name).getClass())) {
            throw new NoSuchBeanDefinitionException(name)
        }

        return (T) beans.get(name)
    }

    def <T> T getBean(Class<T> tClass) throws BeansException {
        final Map<String, T> map = getBeansOfType(tClass)
        if (map.isEmpty()) {
            throw new NoSuchBeanDefinitionException(tClass, 'No bean found for type: ' + tClass.getName())
        }
        return map.values().iterator().next()
    }

    Object getBean(String name, Object... args) throws BeansException {
        return getBean(name)
    }

    @Override
    Object getProperty(String name) {
        if (beans.containsKey(name)) {
            return beans.get(name)
        }

        return super.getProperty(name)
    }

    boolean containsBean(String name) {
        return beans.containsKey(name)
    }

    boolean isSingleton(String name) {
        throw new UnsupportedOperationException('Method not supported by implementation')
    }

    boolean isPrototype(String s) {
        throw new UnsupportedOperationException('Method not supported by implementation')
    }

    @Override
    boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
        return isTypeMatch(name, typeToMatch.getRawClass())
    }

    @SuppressWarnings('rawtypes')
    boolean isTypeMatch(String name, Class aClass) {
        return aClass.isInstance(getBean(name))
    }

    @SuppressWarnings([ 'unchecked', 'rawtypes' ])
    Class getType(String name) throws NoSuchBeanDefinitionException {
        if (!beans.containsKey(name)) {
            throw new NoSuchBeanDefinitionException(name)
        }

        return beans.get(name).getClass()
    }

    @Override
    Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
        return getType(name)
    }

    String[] getAliases(String name) {
        return new String[0]
    }

    BeanFactory getParentBeanFactory() {
        return null
    }

    String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
        MessageSource messageSource = (MessageSource) getBean('messageSource')
        if (messageSource == null) {
            throw new BeanCreationException('No bean [messageSource] found in MockApplicationContext')
        }
        return messageSource.getMessage(code, args, defaultMessage, locale)
    }

    String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
        MessageSource messageSource = (MessageSource) getBean('messageSource')
        if (messageSource == null) {
            throw new BeanCreationException('No bean [messageSource] found in MockApplicationContext')
        }
        return messageSource.getMessage(code, args, locale)
    }

    String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
        MessageSource messageSource = (MessageSource) getBean('messageSource')
        if (messageSource == null) {
            throw new BeanCreationException('No bean [messageSource] found in MockApplicationContext')
        }
        return messageSource.getMessage(resolvable, locale)
    }

    Resource[] getResources(String locationPattern) throws IOException {
        if (locationPattern.startsWith('classpath:') || locationPattern.startsWith('file:')) {
            throw new UnsupportedOperationException("Location patterns 'classpath:' and 'file:' not supported by implementation")
        }

        locationPattern = GrailsStringUtils.trimStart(locationPattern, '/') // starting with "**/" is OK
        List<Resource> result = new ArrayList<>()
        for (Resource res in resources) {
            String path = res instanceof ClassPathResource ? ((ClassPathResource) res).getPath() : res.getDescription()
            if (pathMatcher.match(locationPattern, path)) {
                result.add(res)
            }
        }
        return result.toArray(new Resource[0])
    }

    Resource getResource(String location) {
        for (Resource mockResource in resources) {
            if (pathMatcher.match(mockResource.getDescription(), GrailsStringUtils.trimStart(location, '/'))) {
                return mockResource
            }
        }
        // Check for ignored resources and return null instead of a classpath resource in that case.
        for (String resourceLocation in ignoredClassLocations) {
            if (pathMatcher.match(
                    GrailsStringUtils.trimStart(location, '/'),
                    GrailsStringUtils.trimStart(resourceLocation, '/'))) {
                return null
            }
        }

        return new ClassPathResource(location)
    }

    boolean containsLocalBean(String arg0) {
        throw new UnsupportedOperationException('Method not supported by implementation')
    }

    AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
        return new DefaultListableBeanFactory()
    }

    ClassLoader getClassLoader() {
        return getClass().getClassLoader()
    }

    ServletContext getServletContext() {
        return servletContext
    }

    void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext
    }

    Environment getEnvironment() {
        return new StandardServletEnvironment()
    }

    class MockResource extends AbstractResource {

        private String contents = ''
        private String location

        MockResource(String location) {
            this.location = location
        }

        MockResource(String location, String contents) {
            this(location)
            this.contents = contents
        }

        @Override
        boolean exists() {
            return true
        }

        String getDescription() {
            return location
        }

        InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))
        }
    }

    @Override
    def <T> T getBean(Class<T> requiredType, Object... args)
            throws BeansException {
        return getBean(requiredType)
    }

    @Override
    def <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
        return new ObjectProvider<T>() {
            @Override
            T getObject(Object... args) throws BeansException {
                return getBean(requiredType)
            }

            @Override
            T getIfAvailable() throws BeansException {
                return getBean(requiredType)
            }

            @Override
            T getIfUnique() throws BeansException {
                return getBean(requiredType)
            }

            @Override
            T getObject() throws BeansException {
                return getBean(requiredType)
            }
        }
    }

    @Override
    def <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
        return new ObjectProvider<T>() {
            @Override
            T getObject(Object... args) throws BeansException {
                return (T) getBean(requiredType.toClass())
            }

            @Override
            T getIfAvailable() throws BeansException {
                return (T) getBean(requiredType.toClass())
            }

            @Override
            T getIfUnique() throws BeansException {
                return (T) getBean(requiredType.toClass())
            }

            @Override
            T getObject() throws BeansException {
                return (T) getBean(requiredType.toClass())
            }
        }
    }

    @Override
    def <T> ObjectProvider<T> getBeanProvider(org.springframework.core.ParameterizedTypeReference<T> requiredType) {
        return getBeanProvider(ResolvableType.forType(requiredType.getType()))
    }

}
