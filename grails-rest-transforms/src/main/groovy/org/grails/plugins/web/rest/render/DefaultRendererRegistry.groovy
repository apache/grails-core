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
package org.grails.plugins.web.rest.render

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import grails.core.support.proxy.ProxyHandler
import grails.rest.render.ContainerRenderer
import grails.rest.render.Renderer
import grails.rest.render.RendererRegistry
import grails.util.GrailsClassUtils
import grails.web.mime.MimeType
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import jakarta.annotation.PostConstruct
import org.grails.plugins.web.rest.render.html.DefaultHtmlRenderer
import org.grails.plugins.web.rest.render.json.DefaultJsonRenderer
import org.grails.plugins.web.rest.render.xml.DefaultXmlRenderer
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import org.grails.web.util.ClassAndMimeTypeRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors

import java.util.concurrent.ConcurrentHashMap

/**
 * Default implementation of the {@link RendererRegistry} interface
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class DefaultRendererRegistry extends ClassAndMimeTypeRegistry<Renderer, RendererCacheKey> implements RendererRegistry {

    private Map<ContainerRendererCacheKey, Renderer> containerRenderers = new ConcurrentHashMap<>()
    private Cache<ContainerRendererCacheKey, Renderer<?>> containerRendererCache = Caffeine.newBuilder()
            .initialCapacity(500)
            .maximumSize(1000)
            .build()

    @Autowired(required = false)
    GrailsConventionGroovyPageLocator groovyPageLocator
    @Autowired(required = false)
    ProxyHandler proxyHandler

    String modelSuffix = ''

    DefaultRendererRegistry() {}

    @PostConstruct
    void initialize() {
        addDefaultRenderer(new DefaultXmlRenderer<Object>(Object, groovyPageLocator, this))
        addDefaultRenderer(new DefaultJsonRenderer<Object>(Object, groovyPageLocator, this))
        final defaultHtmlRenderer = new DefaultHtmlRenderer<Object>(Object)
        defaultHtmlRenderer.suffix = modelSuffix
        defaultHtmlRenderer.proxyHandler = proxyHandler
        addDefaultRenderer(defaultHtmlRenderer)
        final allHtmlRenderer = new DefaultHtmlRenderer<Object>(Object, MimeType.ALL)
        allHtmlRenderer.suffix = modelSuffix
        allHtmlRenderer.proxyHandler = proxyHandler
        addDefaultRenderer(allHtmlRenderer)
        containerRenderers.put(new ContainerRendererCacheKey(Errors, Object, MimeType.XML), new DefaultXmlRenderer(Errors))
        containerRenderers.put(new ContainerRendererCacheKey(Errors, Object, MimeType.TEXT_XML), new DefaultXmlRenderer(Errors))
        containerRenderers.put(new ContainerRendererCacheKey(Errors, Object, MimeType.JSON), new DefaultJsonRenderer(Errors))
        containerRenderers.put(new ContainerRendererCacheKey(Errors, Object, MimeType.TEXT_JSON), new DefaultJsonRenderer(Errors))
        final defaultContainerHtmlRenderer = new DefaultHtmlRenderer(Errors)
        defaultContainerHtmlRenderer.suffix = modelSuffix
        defaultContainerHtmlRenderer.proxyHandler = proxyHandler
        containerRenderers.put(new ContainerRendererCacheKey(Errors, Object, MimeType.HTML), defaultContainerHtmlRenderer)
        containerRenderers.put(new ContainerRendererCacheKey(Errors, Object, MimeType.ALL), defaultContainerHtmlRenderer)
    }

    @Autowired(required = false)
    void setRenderers(Renderer[] renderers) {
        for (Renderer r in renderers) {
            addRenderer(r)
        }
    }

    @Override
    def <T> void addRenderer(Renderer<T> renderer) {
        if (renderer instanceof ContainerRenderer) {
            ContainerRenderer cr = (ContainerRenderer) renderer
            addContainerRenderer(cr.componentType, cr)
        } else {
            Class targetType = renderer.targetType
            addToRegisteredObjects(targetType, renderer)
        }
    }

    @Override
    void addDefaultRenderer(Renderer<Object> renderer) {
        for (MimeType mt in renderer.mimeTypes) {
            registerDefault(mt, renderer)
            removeFromCache(renderer.getTargetType(), mt)
        }
    }

    @Override
    void addContainerRenderer(Class objectType, Renderer renderer) {
        for (MimeType mt in renderer.mimeTypes) {
            def key = new ContainerRendererCacheKey(renderer.getTargetType(), objectType, mt)

            containerRendererCache.invalidate(key)
            containerRenderers.put(key, renderer)
        }
    }

    @Override
    def <T> Renderer<T> findRenderer(MimeType mimeType, T object) {
        return findMatchingObjectForMimeType(mimeType, object)
    }

    @Override
    def <C, T> Renderer<C> findContainerRenderer(MimeType mimeType, Class<C> containerType, T object) {
        if (object == null) return null
        if (proxyHandler != null) {
            object = proxyHandler.unwrapIfProxy(object) as T
        }

        def originalTargetClass = object instanceof Class ? (Class) object : object.getClass()
        originalTargetClass = getTargetClassForContainer(originalTargetClass, object)
        def originalKey = new ContainerRendererCacheKey(containerType, originalTargetClass, mimeType)

        Renderer<C> renderer = (Renderer<C>) containerRendererCache.getIfPresent(originalKey)

        if (renderer == null) {
            def key = originalKey
            def targetClass = originalTargetClass

            while (targetClass != null) {

                renderer = containerRenderers.get(key)
                if (renderer != null) break
                else {

                    key = new ContainerRendererCacheKey(containerType, targetClass, mimeType)
                    renderer = containerRenderers.get(key)
                    if (renderer != null) break
                    else {
                        final containerInterfaces = GrailsClassUtils.getAllInterfacesForClass(containerType)
                        for (Class i in containerInterfaces) {
                            key = new ContainerRendererCacheKey(i, targetClass, mimeType)
                            renderer = containerRenderers.get(key)
                            if (renderer != null) break
                        }
                    }
                    if (targetClass == Object) break
                    targetClass = targetClass.getSuperclass()
                }
            }

            if (renderer == null) {
                final interfaces = GrailsClassUtils.getAllInterfacesForClass(originalTargetClass)
                outer:
                for (Class i in interfaces) {
                    key = new ContainerRendererCacheKey(containerType, i, mimeType)
                    renderer = containerRenderers.get(key)
                    if (renderer) break
                    else {
                        final containerInterfaces = GrailsClassUtils.getAllInterfacesForClass(containerType)
                        for (Class ci in containerInterfaces) {
                            key = new ContainerRendererCacheKey(ci, i, mimeType)
                            renderer = containerRenderers.get(key)
                            if (renderer != null) break outer
                        }
                    }
                }
            }

            if (renderer) {
                containerRendererCache.put(originalKey, renderer)
            }
        }

        return renderer
    }

    protected Class<? extends Object> getTargetClassForContainer(Class containerClass, Object object) {
        Class targetClass = containerClass
        if (containerClass.isArray()) {
            targetClass = containerClass.getComponentType()
        } else if (object instanceof Iterable) {
            if (object) {
                final iterator = object.iterator()
                def first = iterator.next()
                if (first) {
                    if (proxyHandler != null) {
                        first = proxyHandler.unwrapIfProxy(first)
                    }
                    targetClass = first.getClass()
                }
            }
        } else if (object instanceof Map) {
            if (object) {
                def first = object.values().iterator().next()
                if (first) {
                    if (proxyHandler != null) {
                        first = proxyHandler.unwrapIfProxy(first)
                    }
                    targetClass = first.getClass()
                }
            }
        } else if (object instanceof BeanPropertyBindingResult) {
            def target = ((BeanPropertyBindingResult) object).target
            if (target) {
                if (proxyHandler != null) {
                    target = proxyHandler.unwrapIfProxy(target)
                }
                targetClass = target.getClass()
            }
        }
        return targetClass
    }

    @Override
    boolean isContainerType(Class<?> aClass) {
        if (containerRenderers.keySet().any { ContainerRendererCacheKey key -> key.containerType.isAssignableFrom(aClass) }) {
            return true
        }
        return false
    }

    @Override
    RendererCacheKey createCacheKey(Class type, MimeType mimeType) {
        return new RendererCacheKey(type, mimeType)
    }

    @Canonical
    class RendererCacheKey {

        Class clazz
        MimeType mimeType
    }

    @Canonical
    class ContainerRendererCacheKey {

        Class containerType
        Class clazz
        MimeType mimeType
    }
}
