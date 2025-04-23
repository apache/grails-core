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
package grails.rest.render.util

import grails.core.support.proxy.DefaultProxyHandler
import grails.core.support.proxy.EntityProxyHandler
import grails.core.support.proxy.ProxyHandler
import grails.rest.Link
import grails.rest.render.AbstractIncludeExcludeRenderer
import grails.rest.render.RenderContext
import grails.rest.render.RendererRegistry
import grails.util.Environment
import grails.util.GrailsMessageSourceUtils
import grails.util.GrailsWebUtil
import grails.web.mapping.LinkGenerator
import grails.web.mime.MimeType
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.plugins.web.rest.render.html.DefaultHtmlRenderer
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.MessageSource
import org.springframework.http.HttpMethod

/**
 * Abstract base class for HAL renderers
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
abstract class AbstractLinkingRenderer<T> extends AbstractIncludeExcludeRenderer<T> {

    protected static List<String> DEFAULT_EXCLUDES = ['metaClass', 'class']

    public static final String RELATIONSHIP_SELF = 'self'
    public static final String HREF_ATTRIBUTE = 'href'
    public static final String TITLE_ATTRIBUTE = 'title'
    public static final String HREFLANG_ATTRIBUTE = 'hreflang'
    public static final String TYPE_ATTRIBUTE = 'type'
    public static final String TEMPLATED_ATTRIBUTE = 'templated'
    public static final String DEPRECATED_ATTRIBUTE = 'deprecated'

    MessageSource messageSource

    @Autowired
    setMessageSource(List<MessageSource> messageSources) {
        setMessageSource(GrailsMessageSourceUtils.findPreferredMessageSource(messageSources))
    }

    void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource
    }

    @Autowired
    LinkGenerator linkGenerator

    @Autowired
    @Qualifier('grailsDomainClassMappingContext')
    MappingContext mappingContext

    @Autowired
    RendererRegistry rendererRegistry

    @Autowired(required = false)
    ProxyHandler proxyHandler = new DefaultProxyHandler()

    @Autowired(required = false)
    GrailsConventionGroovyPageLocator groovyPageLocator

    boolean prettyPrint = Environment.isDevelopmentMode()
    boolean absoluteLinks = true
    String encoding = GrailsWebUtil.DEFAULT_ENCODING

    AbstractLinkingRenderer(Class<T> targetType, MimeType mimeType) {
        super(targetType, mimeType)
    }

    AbstractLinkingRenderer(Class<T> targetType, MimeType[] mimeTypes) {
        super(targetType, mimeTypes)
    }

    @Override
    void render(T object, RenderContext renderContext) {
        def mimeType = renderContext.acceptMimeType ?: getMimeTypes()[0]
        def contentType = GrailsWebUtil.getContentType(mimeType.name, encoding)
        renderContext.setContentType(contentType)

        def viewName = renderContext.viewName ?: renderContext.actionName
        def view = groovyPageLocator?.findViewForFormat(renderContext.controllerName, viewName, mimeType.extension)
        if (view) {
            // if a view is provided, we use the HTML renderer to return an appropriate model to the view
            def htmlRenderer = rendererRegistry?.findRenderer(MimeType.HTML, object) ?: new DefaultHtmlRenderer(targetType)
            htmlRenderer.render(object, renderContext)
        } else {
            renderInternal(object, renderContext)
        }
    }

    abstract void renderInternal(T object, RenderContext context)

    protected boolean isDomainResource(Class clazz) {
        if(mappingContext != null) {
            return mappingContext.isPersistentEntity(clazz)
        } else {
            DomainClassArtefactHandler.isDomainClass(clazz, true)
        }
    }

    protected String getLinkTitle(PersistentEntity entity, Locale locale) {
        final propertyName = entity.decapitalizedName
        messageSource.getMessage("resource.${propertyName}.href.title", [propertyName, entity.name] as Object[], '', locale)
    }

    protected String getResourceTitle(String uri, Locale locale) {
        if (uri.startsWith('/')) uri = uri.substring(1)
        if (uri.endsWith('/')) uri = uri.substring(0, uri.length()-1)
        uri = uri.replace('/', '.')
        messageSource.getMessage("resource.${uri}.href.title", [uri] as Object[], '', locale)
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    Collection<Link> getLinksForObject(def object) {
        if (object.respondsTo('links')) {
            return object.links()
        }
        return Collections.emptyList()
    }

    protected Map<Association, Object> writeAssociationLinks(RenderContext context, object, Locale locale, writer, PersistentEntity entity, MetaClass metaClass) {

        writeExtraLinks(object, locale, writer)

        Map<Association, Object> associationMap = [:]
        for (Association a in entity.associations) {
            def propertyName = a.name
            if (!shouldIncludeProperty(context, object, propertyName)) {
                continue
            }
            def associatedEntity = a.associatedEntity
            if (!associatedEntity) {
                continue
            }
            if (proxyHandler.isInitialized(object, propertyName)) {
                if (a instanceof ToOne) {
                    def value = proxyHandler.unwrapIfProxy(metaClass.getProperty(object, propertyName))
                    if (a instanceof Embedded) {
                        // no links for embedded
                        associationMap[a] = value
                    } else if (value != null) {
                        final href = linkGenerator.link(resource: value, method: HttpMethod.GET, absolute: absoluteLinks)
                        final associationTitle = getLinkTitle(associatedEntity, locale)
                        final link = new Link(propertyName, href)
                        link.title = associationTitle
                        link.hreflang = locale
                        writeLink(link, locale, writer)
                        associationMap[a] = value
                    }
                } else if (!(a instanceof Basic)) {
                    associationMap[a] = metaClass.getProperty(object, propertyName)
                }

            } else if ((a instanceof ToOne) && (proxyHandler instanceof EntityProxyHandler)) {
                if (associatedEntity) {
                    final proxy = mappingContext.getEntityReflector(a.owner).getProperty(object, propertyName)
                    final id = proxyHandler.getProxyIdentifier(proxy)
                    final href = linkGenerator.link(resource: associatedEntity.decapitalizedName, id: id, method: HttpMethod.GET, absolute: absoluteLinks)
                    final associationTitle = getLinkTitle(associatedEntity, locale)
                    def link = new Link(propertyName, href)
                    link.title = associationTitle
                    link.hreflang = locale
                    writeLink(link, locale, writer)
                }
            }
        }
        associationMap
    }

    protected void writeExtraLinks(object, Locale locale, writer) {
        def extraLinks = getLinksForObject(object)
        for (def link in extraLinks) {
            writeLink(link, locale, writer)
        }
    }

    /**
     * Writes a domain instance
     *
     * @param clazz The class
     * @param object The object
     * @param writer The writer
     * @return Any associations embedded within the object
     */
    protected void writeDomain(RenderContext context, MetaClass metaClass, PersistentEntity entity, Object object, writer) {
        if (entity) {
            for (def p in entity.persistentProperties) {
                def propertyName = p.name
                if (shouldIncludeProperty(context, object, propertyName) && (p instanceof Basic) || !(p instanceof Association)) {
                    def value = metaClass.getProperty(object, propertyName)
                    if (value != null) {
                        writeDomainProperty(value, propertyName, writer)
                    }
                }
            }
        }
    }

    protected abstract void writeLink(Link link, Locale locale, writerObject)
    protected abstract void writeDomainProperty(value, String propertyName, writer)
}
