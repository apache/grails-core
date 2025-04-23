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
package grails.rest.render.atom

import grails.converters.XML
import grails.rest.Link
import grails.rest.render.RenderContext
import grails.rest.render.hal.HalXmlRenderer
import grails.web.mime.MimeType
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.ToOne
import org.springframework.http.HttpMethod

import java.text.SimpleDateFormat

/**
 *
 * Renders output in Atom format (http://tools.ietf.org/html/rfc4287)
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class AtomRenderer<T> extends HalXmlRenderer<T> {

    public static final MimeType MIME_TYPE = MimeType.ATOM_XML
    public static final SimpleDateFormat ATOM_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz")
    public static final String FEED_TAG = 'feed'
    public static final String ENTRY_TAG = 'entry'
    public static final String XMLNS_ATTRIBUTE = "xmlns"
    public static final String PUBLISHED_TAG = 'published'
    public static final String UPDATED_TAG = 'updated'
    public static final String ID_TAG = 'id'
    public static final String ATOM_NAMESPACE = "http://www.w3.org/2005/Atom"
    public static final SimpleDateFormat ID_DATE_FORMAT = new SimpleDateFormat('yyyy-MM-dd')
    public static final String RELATIONSHIP_ALTERNATE = "alternate"
    public static final MimeType[] DEFAULT_ATOM_MIME_TYPES = [MIME_TYPE] as MimeType[]

    AtomRenderer(Class<T> targetType) {
        super(targetType, DEFAULT_ATOM_MIME_TYPES)
    }

    @Override
    void renderInternal(Object object, RenderContext renderContext) {

        Set writtenObjects = []
        def xml = startDocument(renderContext)
        def entity = mappingContext.getPersistentEntity(object.class.name)
        def isDomain = entity != null

        if (isDomain) {
            writeDomainWithEmbeddedAndLinks(entity, object, renderContext, xml, writtenObjects)
        } else if (object instanceof Collection) {
            def locale = renderContext.locale
            def resourceHref = linkGenerator.link(uri: renderContext.resourcePath, method: HttpMethod.GET, absolute: true)
            def title = getResourceTitle(renderContext.resourcePath, locale)
            def writer = xml.getWriter()
            writer
                .startNode(FEED_TAG)
                .attribute(XMLNS_ATTRIBUTE, ATOM_NAMESPACE)
                .startNode(TITLE_ATTRIBUTE)
                .characters(title)
                .end()
                .startNode(ID_TAG)
                .characters(generateIdForURI(resourceHref))
                .end()

            writeRelationshipLinks(resourceHref, title, locale, xml)

            for (o in (Collection) object) {
                def currentEntity = mappingContext.getPersistentEntity(o.class.name)
                if (currentEntity) {
                    writeDomainWithEmbeddedAndLinks(currentEntity, o, renderContext, xml, writtenObjects, false)
                } else {
                    throw new IllegalArgumentException("Cannot render object [$o] using Atom. The AtomRenderer can only be used with domain classes that specify 'dateCreated' and 'lastUpdated' properties")
                }
            }
            writer.end()
            renderContext.writer.flush()
        } else {
            throw new IllegalArgumentException("Cannot render object [$object] using Atom. The AtomRenderer can only be used with domain classes that specify 'dateCreated' and 'lastUpdated' properties")
        }
    }

    private void writeRelationshipLinks(String resourceHref, String title, Locale locale, XML xml) {
        def linkSelf = new Link(RELATIONSHIP_SELF, resourceHref)
        linkSelf.title = title
        linkSelf.contentType = mimeTypes[0].name
        linkSelf.hreflang = locale
        writeLink(linkSelf, locale, xml)

        def linkAlt = new Link(RELATIONSHIP_ALTERNATE, resourceHref)
        linkAlt.title = title
        linkAlt.hreflang = locale
        writeLink(linkAlt, locale, xml)
    }

    static String generateIdForURI(String url, Date dateCreated = null, Object id = null) {
        if (url.startsWith('http')) {
            url = url.substring(url.indexOf('//')+2, url.length())
        }
        url = url.replace('#', '/')
        def i = url.indexOf('/')
        if (i > -1) {
            String dateCreatedId = ''
            if (dateCreated) {
                dateCreatedId = ",${ID_DATE_FORMAT.format(dateCreated)}"
            }
            url = "${url.substring(0, i)}${dateCreatedId}:${ id ?: url.substring(i, url.length())}"
        }

        return "tag:$url"
    }

    @Override
    protected void writeDomainWithEmbeddedAndLinks(PersistentEntity entity, Object object, RenderContext context, XML xml, Set writtenObjects) {
        writeDomainWithEmbeddedAndLinks(entity, object, context, xml, writtenObjects, true)
    }

    protected void writeDomainWithEmbeddedAndLinks(PersistentEntity entity, Object object, RenderContext context, XML xml, Set writtenObjects, boolean isFirst) {

        if (!entity.getPropertyByName('lastUpdated')) {
            throw new IllegalArgumentException("Cannot render object [$object] using Atom. The AtomRenderer can only be used with domain classes that specify 'dateCreated' and 'lastUpdated' properties")
        }

        def locale = context.locale
        def resourceHref = linkGenerator.link(resource: object, method: HttpMethod.GET, absolute: true)
        def title = getLinkTitle(entity, locale)

        def writer = xml.getWriter()
        writer.startNode(isFirst ? FEED_TAG : ENTRY_TAG)

        if (isFirst) {
            writer.attribute(XMLNS_ATTRIBUTE, ATOM_NAMESPACE)
        }

        if (!entity.getPropertyByName(TITLE_ATTRIBUTE)) {
            writer.startNode(TITLE_ATTRIBUTE)
                    .characters(object.toString())
                    .end()
        }

        def dateCreated = formatDateCreated(object)
        if (dateCreated) {
            writer.startNode(PUBLISHED_TAG)
                    .characters(dateCreated)
                    .end()
        }

        def lastUpdated = formatLastUpdated(object)
        if (lastUpdated) {
            writer.startNode(UPDATED_TAG)
                    .characters(lastUpdated)
                    .end()
        }

        writer.startNode(ID_TAG)
                .characters(getObjectId(entity, object))
                .end()

        writeRelationshipLinks(resourceHref, title, locale, xml)

        def metaClass = GroovySystem.metaClassRegistry.getMetaClass(entity.javaClass)
        def associationMap = writeAssociationLinks(context, object, locale, xml, entity, metaClass)
        writeDomain(context, metaClass, entity, object, xml)

        if (associationMap) {
            for (def entry in associationMap.entrySet()) {
                def property = entry.key
                if (property instanceof ToOne) {
                    def value = entry.value
                    if (writtenObjects.contains(value)) {
                        continue
                    }

                    if (value != null) {
                        def associatedEntity = property.associatedEntity
                        if (associatedEntity) {
                            writtenObjects.add(value)
                            writeDomainWithEmbeddedAndLinks(associatedEntity, value, context, xml, writtenObjects, false)
                        }
                    }
                }
                else {
                    def associatedEntity = property.associatedEntity
                    if (associatedEntity) {
                        writer.startNode(property.name)
                        for (def obj in entry.value) {
                            writtenObjects.add(obj)
                            writeDomainWithEmbeddedAndLinks(associatedEntity, obj, context, xml, writtenObjects, false)
                        }
                        writer.end()
                    }
                }
            }
        }
        writer.end()
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    String getObjectId(PersistentEntity entity, Object object) {
        def name = entity.identity.name
        def objectId = object[name]
        def dateCreated = object.dateCreated
        def url = linkGenerator.link(resource: object, method: HttpMethod.GET, absolute: true)
        return generateIdForURI(url, dateCreated as Date, objectId)
    }

    protected static String formatDateCreated(Object object) {
        return object.hasProperty('dateCreated') ? formatAtomDate(object.invokeMethod('getDateCreated', null) as Date) : null
    }

    protected static String formatLastUpdated(Object object) {
        return object.hasProperty('lastUpdated') ? formatAtomDate(object.invokeMethod('getLastUpdated', null) as Date) : null
    }

    protected static String formatAtomDate(Date dateCreated) {
        if (dateCreated) {
            def dateFormat = ATOM_DATE_FORMAT.format(dateCreated)
            return dateFormat.substring(0, 19) + dateFormat.substring(22, dateFormat.length())
        }
        return null
    }
}
