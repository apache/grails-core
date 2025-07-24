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

import groovy.json.JsonOutput
import groovy.json.StreamingJsonBuilder
import groovy.transform.CompileStatic

import jakarta.annotation.PostConstruct
import jakarta.xml.bind.DatatypeConverter

import org.springframework.beans.PropertyAccessorFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod

import grails.rest.Link
import grails.rest.render.RenderContext
import grails.rest.render.util.AbstractLinkingRenderer
import grails.web.mime.MimeType
import org.grails.datastore.mapping.model.MappingFactory
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.web.databinding.bindingsource.DataBindingSourceRegistry
import org.grails.web.databinding.bindingsource.HalJsonDataBindingSourceCreator

/**
 * Renders domain instances in HAL JSON format (see http://tools.ietf.org/html/draft-kelly-json-hal-05)
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class HalJsonRenderer<T> extends AbstractLinkingRenderer<T> {

    public static final MimeType MIME_TYPE = MimeType.HAL_JSON
    public static final String LINKS_ATTRIBUTE = "_links"
    public static final String EMBEDDED_ATTRIBUTE = "_embedded"

    private static final MimeType[] DEFAULT_MIME_TYPES = [MIME_TYPE] as MimeType[]

    private static class UTCDateConverter implements Converter<Date, String> {
        private final static TimeZone UtcTZ = TimeZone.getTimeZone('UTC')
        @Override
        String convert(Date source) {
            final GregorianCalendar cal = new GregorianCalendar()
            cal.setTime(source)
            cal.setTimeZone(UtcTZ)
            DatatypeConverter.printDateTime(cal)
        }
    }

    String collectionName

    HalJsonRenderer(Class<T> targetType) {
        super(targetType, DEFAULT_MIME_TYPES)
    }

    HalJsonRenderer(Class<T> targetType, MimeType... mimeTypes) {
        super(targetType, mimeTypes)
    }


    @Autowired(required = false)
    DataBindingSourceRegistry dataBindingSourceRegistry

    Converter<Date, String> dateToStringConverter = new UTCDateConverter()

    @Autowired(required = false)
    void setDateToStringConverter(Converter<Date, String> converter) {
        this.dateToStringConverter = converter
    }

    @PostConstruct
    void initialize() {
        if (dataBindingSourceRegistry != null) {
            final thisType = getTargetType()
            final thisMimeTypes = getMimeTypes()
            final halDataBindingSourceCreator = new HalJsonDataBindingSourceCreator() {
                @Override
                Class getTargetType() {
                    thisType
                }

                @Override
                MimeType[] getMimeTypes() {
                    thisMimeTypes
                }
            }
            dataBindingSourceRegistry.addDataBindingSourceCreator(halDataBindingSourceCreator)
        }
    }

    @Override
    void renderInternal(T object, RenderContext context) {
        final mimeType = context.acceptMimeType ?: mimeTypes[0]
        final responseWriter = context.writer
        Writer targetWriter = prettyPrint ? new StringWriter() : responseWriter
        StreamingJsonBuilder writer = new StreamingJsonBuilder(targetWriter)


        try {
            final clazz = object.class

            if (isDomainResource(clazz)) {
                writer.call {
                    writeDomainWithEmbeddedAndLinks(context, clazz, object, delegate, context.locale, mimeType, [] as Set,
                            new Stack())
                }

            } else if (object instanceof Collection) {
                writer.call {

                    call(LINKS_ATTRIBUTE) {

                        writeLinkForCurrentPath(context, mimeType, delegate)
                    }

                    if(collectionName != null) {

                        call(EMBEDDED_ATTRIBUTE) {
                            renderEmbeddedAttributes(delegate, object, context, mimeType)
                        }
                    }
                    else {
                        final writtenObjects = [] as Set
                        call(EMBEDDED_ATTRIBUTE,((Collection)object)) { o ->
                            if (o) {
                                if(isDomainResource(o.getClass())) {
                                    writeDomainWithEmbeddedAndLinks(context, o.class, o, (StreamingJsonBuilder.StreamingJsonDelegate)delegate, context.locale, mimeType, writtenObjects
                                            , new Stack())
                                } else {
                                    writeSimpleObjectAndLink(o, context, (StreamingJsonBuilder.StreamingJsonDelegate)delegate, mimeType)
                                }
                            }
                        }
                    }

                }

            } else {
                writer.call {
                    writeSimpleObjectAndLink(object, context, delegate, mimeType)
                }

            }
        } finally {
            targetWriter.flush()
        }

        if(prettyPrint) {
            responseWriter.write(JsonOutput.prettyPrint(targetWriter.toString()))
        }

    }

    protected renderEmbeddedAttributes(StreamingJsonBuilder.StreamingJsonDelegate writer, object, RenderContext context, MimeType mimeType) {
        final writtenObjects = [] as Set
        writer.call(collectionName, ((Collection)object)) { o ->
            if (o) {
                if(isDomainResource(o.getClass())) {
                    writeDomainWithEmbeddedAndLinks(context, o.class, o, (StreamingJsonBuilder.StreamingJsonDelegate)delegate, context.locale, mimeType, writtenObjects
                            , new Stack())
                } else {
                    writeSimpleObjectAndLink(o, context, (StreamingJsonBuilder.StreamingJsonDelegate)delegate, mimeType)
                }
            }
        }
    }

    protected writeSimpleObjectAndLink(Object o, RenderContext context, StreamingJsonBuilder.StreamingJsonDelegate writer, MimeType mimeType) {
        writer.call(LINKS_ATTRIBUTE) {
            writeLinkForCurrentPath(context, mimeType, delegate)
            writeExtraLinks(o, context.locale, delegate)
        }

        writeSimpleObject(o, context, writer)
    }

    protected void writeSimpleObject(Object object, RenderContext context, StreamingJsonBuilder.StreamingJsonDelegate writer) {
        final bean = PropertyAccessorFactory.forBeanPropertyAccess(object)
        final propertyDescriptors = bean.propertyDescriptors
        for (pd in propertyDescriptors) {
            final propertyName = pd.name
            if (DEFAULT_EXCLUDES.contains(propertyName)) continue
            if (shouldIncludeProperty(context, object, propertyName)) {
                if (pd.readMethod && pd.writeMethod) {
                    final value = bean.getPropertyValue(propertyName)
                    if (value instanceof Number) {
                        writer.call (propertyName,(Number) value)
                    }
                    else if (value instanceof CharSequence) {
                        writer.call(propertyName, ((CharSequence) value).toString())
                    } else if (value instanceof Enum) {
                        writer.call(propertyName, ((Enum) value).toString())
                    }
                    else {
                        if (MappingFactory.isSimpleType(pd.getPropertyType().getName())) {
                            writer.call (propertyName, value)
                        }
                        else {
                            writer.call (propertyName) {
                                writeSimpleObject(value, context, delegate)
                            }
                        }
                    }
                }
            }
        }
    }

    protected void writeLinkForCurrentPath(RenderContext context, MimeType mimeType, StreamingJsonBuilder.StreamingJsonDelegate writer) {
        final href = linkGenerator.link(uri: context.resourcePath, method: HttpMethod.GET.toString(), absolute: absoluteLinks)
        final resourceRef = href
        final locale = context.locale
        def link = new Link(RELATIONSHIP_SELF, href)
        link.title = getResourceTitle(resourceRef, locale)
        link.contentType = mimeType ? mimeType.name : null

        writeLink(link, locale, writer)
    }

    protected void writeDomainWithEmbeddedAndLinks(RenderContext context, Class clazz, Object object, StreamingJsonBuilder.StreamingJsonDelegate writer, Locale locale, MimeType contentType, Set writtenObjects,
                                                   Stack referenceStack) {

        PersistentEntity entity = mappingContext.getPersistentEntity(clazz.name)
        final metaClazz = GroovySystem.metaClassRegistry.getMetaClass(entity.javaClass)
        //If the object was already serialized , simply write its link for it and return.
        if (referenceStack.contains(object)) {
            writeLinks(context,metaClazz, object, entity, locale, contentType, writer, false)
            return
        }
        //Push the current object to referenceStack for  handling circular references. Once all its fields are handled,
        //the object is removed from the stack.
        referenceStack.push object
        Map<Association, Object> associationMap = writeLinks(context,metaClazz, object, entity, locale, contentType, writer, true)

        writeDomain(context, metaClazz, entity, object, writer)


        if (associationMap) {
            writer.call(EMBEDDED_ATTRIBUTE) {

                for (entry in associationMap.entrySet()) {
                    final property = entry.key
                    final isSingleEnded = property instanceof ToOne

                    if (isSingleEnded) {
                        Object value = entry.value
                        if (value != null) {
                            delegate.call (property.name) {
                                final associatedEntity = property.associatedEntity
                                if (associatedEntity) {
                                    writtenObjects << value
                                    writeDomainWithEmbeddedAndLinks(context, associatedEntity.javaClass, value, (StreamingJsonBuilder.StreamingJsonDelegate)delegate , locale, null, writtenObjects,
                                            referenceStack)
                                }

                            }
                        }
                    } else {
                        Iterable iterable = (Iterable) entry.value

                        delegate.call(property.name, iterable == null ? Collections.emptyList() : iterable) { obj ->
                            final associatedEntity = property.associatedEntity
                            if (associatedEntity) {
                                writtenObjects << obj
                                writeDomainWithEmbeddedAndLinks(context, associatedEntity.javaClass, obj, (StreamingJsonBuilder.StreamingJsonDelegate)delegate, locale,null, writtenObjects,
                                        referenceStack)
                            }
                        }


                    }

                }
            }

        }

        referenceStack.pop()
    }

    protected Map<Association, Object> writeLinks(RenderContext context, MetaClass metaClass, object, PersistentEntity entity, Locale locale, MimeType contentType, StreamingJsonBuilder.StreamingJsonDelegate writer,
                                                  boolean associationLinks = true) {
        Map<Association, Object> associationMap
        final entityHref = linkGenerator.link(resource: object, method: HttpMethod.GET.toString(), absolute: absoluteLinks)
        final title = getLinkTitle(entity, locale)



        writer.call(LINKS_ATTRIBUTE) {
            def link = new Link(RELATIONSHIP_SELF, entityHref)
            link.contentType = contentType ? contentType.name : null
            link.title = title
            link.hreflang = locale
            writeLink(link, locale, delegate)
            associationMap = associationLinks ?
                    writeAssociationLinks(context,object, locale, delegate, entity, metaClass) : [:] as Map<Association,Object>
            associationMap
        }
        return associationMap
    }

    protected void writeLink(Link link, Locale locale, writer) {
        StreamingJsonBuilder.StreamingJsonDelegate links = (StreamingJsonBuilder.StreamingJsonDelegate )writer

        links.call(link.rel) {
            call(HREF_ATTRIBUTE,link.href)
            call(HREFLANG_ATTRIBUTE,(link.hreflang ?: locale).language)
            final title = link.title
            if (title) {
                call(TITLE_ATTRIBUTE,title)
            }
            final type = link.contentType
            if (type) {
                call(TYPE_ATTRIBUTE,type)
            }
            if (link.templated) {
                call(TEMPLATED_ATTRIBUTE, true)
            }
            if (link.deprecated) {
                call(DEPRECATED_ATTRIBUTE,true)
            }
        }



    }

    protected void writeDomainProperty(value, String propertyName, jsonWriter) {
        StreamingJsonBuilder.StreamingJsonDelegate builder = (StreamingJsonBuilder.StreamingJsonDelegate)jsonWriter
        builder.call(propertyName, value)
    }
}
