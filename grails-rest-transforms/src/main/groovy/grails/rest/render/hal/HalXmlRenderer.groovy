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
package grails.rest.render.hal

import grails.converters.XML
import grails.rest.Link
import grails.rest.render.RenderContext
import grails.rest.render.util.AbstractLinkingRenderer
import groovy.transform.CompileStatic
import grails.web.mime.MimeType
import org.grails.web.xml.PrettyPrintXMLStreamWriter
import org.grails.web.xml.StreamingMarkupWriter
import org.grails.web.xml.XMLStreamWriter
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.ToOne
import org.springframework.beans.PropertyAccessorFactory
import org.springframework.http.HttpMethod

/**
 * Renders domain instances in HAL XML format (see http://stateless.co/hal_specification.html)
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class HalXmlRenderer<T> extends AbstractLinkingRenderer<T> {

    public static final MimeType MIME_TYPE = MimeType.HAL_XML
    public static final String RESOURCE_TAG = "resource"
    public static final String LINK_TAG = "link"
    public static final String RELATIONSHIP_ATTRIBUTE = "rel"

    private static final MimeType[] DEFAULT_MIME_TYPES = [MIME_TYPE] as MimeType[]

    HalXmlRenderer(Class<T> targetType) {
        super(targetType, DEFAULT_MIME_TYPES)
    }

    HalXmlRenderer(Class<T> targetType, MimeType mimeType) {
        super(targetType, mimeType)
    }

    HalXmlRenderer(Class<T> targetType, MimeType[] mimeTypes) {
        super(targetType, mimeTypes)
    }

    protected XML startDocument(RenderContext renderContext) {
        def streamingWriter = new StreamingMarkupWriter(renderContext.writer, encoding)
        def xmlWriter = prettyPrint ? new PrettyPrintXMLStreamWriter(streamingWriter) : new XMLStreamWriter(streamingWriter)
        xmlWriter.startDocument(encoding, '1.0')
        return new XML(xmlWriter)
    }

    @Override
    void renderInternal(Object object, RenderContext renderContext) {

        Set writtenObjects = []
        def xml = startDocument(renderContext)
        def entity = mappingContext.getPersistentEntity(object.class.name)
        def isDomain = entity != null

        if (isDomain) {
            writeDomainWithEmbeddedAndLinks(entity, object, renderContext, xml, writtenObjects)
        }
        else if (object instanceof Collection) {
            def writer = xml.getWriter()
            startResourceTagForCurrentPath(renderContext, writer)
            for (o in (Collection) object) {
                def currentEntity = mappingContext.getPersistentEntity(o.class.name)
                if (currentEntity) {
                    writeDomainWithEmbeddedAndLinks(currentEntity, o, renderContext, xml, writtenObjects)
                }
            }
            writer.end()
        }
        else {
            def writer = xml.getWriter()
            startResourceTagForCurrentPath(renderContext, writer)
            writeExtraLinks(object, renderContext.locale, xml)
            def bean = PropertyAccessorFactory.forBeanPropertyAccess(object)
            def propertyDescriptors = bean.propertyDescriptors
            for (pd in propertyDescriptors) {
                def propertyName = pd.name
                if (DEFAULT_EXCLUDES.contains(propertyName)) continue
                if (shouldIncludeProperty(renderContext, object, propertyName)) {
                    if (pd.readMethod && pd.writeMethod) {
                        writer.startNode(propertyName)
                        xml.convertAnother(bean.getPropertyValue(propertyName))
                        writer.end()
                    }
                }
            }
            writer.end()
        }
    }

    protected void startResourceTagForCurrentPath(RenderContext context, XMLStreamWriter writer) {
        final locale = context.locale
        String resourceHref = linkGenerator.link(uri: context.resourcePath, method: HttpMethod.GET, absolute: absoluteLinks)
        final title = getResourceTitle(context.resourcePath, locale)
        startResourceTag(writer, resourceHref, locale, title)
    }

    protected void writeDomainWithEmbeddedAndLinks(PersistentEntity entity, object, RenderContext context, XML xml, Set writtenObjects) {

        def locale = context.locale
        def resourceHref = linkGenerator.link(resource: object, method: HttpMethod.GET, absolute: absoluteLinks)
        def title = getLinkTitle(entity, locale)
        def writer = xml.getWriter()
        startResourceTag(writer, resourceHref, locale, title)

        def metaClass = GroovySystem.metaClassRegistry.getMetaClass(entity.javaClass)
        def associationMap = writeAssociationLinks(context, object, locale, xml, entity, metaClass)
        writeDomain(context, metaClass, entity, object, xml)

        if (associationMap) {
            for (def entry in associationMap.entrySet()) {
                def property = entry.key
                if (property instanceof ToOne) {
                    def value = entry.value
                    if (value && !writtenObjects.contains(value)) {
                        def associatedEntity = property.associatedEntity
                        if (associatedEntity) {
                            writtenObjects.add(value)
                            writeDomainWithEmbeddedAndLinks(associatedEntity, value, context, xml, writtenObjects)
                        }
                    }
                } else {
                    def associatedEntity = property.associatedEntity
                    if (associatedEntity) {
                        for (def obj in entry.value) {
                            if (!writtenObjects.contains(obj)) {
                                writtenObjects.add(obj)
                                writeDomainWithEmbeddedAndLinks(associatedEntity, obj, context, xml, writtenObjects)
                            }
                        }
                    }
                }
            }
        }
        writer.end()
    }

    protected void startResourceTag(XMLStreamWriter writer, String resourceHref, Locale locale, String title) {
        writer.startNode(RESOURCE_TAG)
            .attribute(HREF_ATTRIBUTE, resourceHref)
            .attribute(HREFLANG_ATTRIBUTE, locale.language)

        if (title) {
            writer.attribute(TITLE_ATTRIBUTE, title)
        }
    }

    void writeLink(Link link, Locale locale, writerObject) {
        XMLStreamWriter writer = ((XML) writerObject).getWriter()
        writer.startNode(LINK_TAG)
            .attribute(RELATIONSHIP_ATTRIBUTE, link.rel)
            .attribute(HREF_ATTRIBUTE, link.href)
            .attribute(HREFLANG_ATTRIBUTE, (link.hreflang ?: locale).language)

        final title = link.title
        if (title) {
            writer.attribute(TITLE_ATTRIBUTE, title)
        }
        final contentType = link.contentType
        if (contentType) {
            writer.attribute(TYPE_ATTRIBUTE, contentType)
        }

        if (link.templated) {
            writer.attribute(TEMPLATED_ATTRIBUTE,"true")
        }
        if (link.deprecated) {
            writer.attribute(DEPRECATED_ATTRIBUTE,"true")
        }
        writer.end()
    }

    @Override
    protected void writeDomainProperty(value, String propertyName, writerObject) {
        final xml = (XML) writerObject
        XMLStreamWriter writer = xml.getWriter()

        writer.startNode(propertyName)
        xml.convertAnother(value)
        writer.end()
    }
}
