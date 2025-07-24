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
package org.grails.plugins.web.rest.render.html

import groovy.transform.CompileStatic

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors

import grails.core.support.proxy.ProxyHandler
import grails.rest.render.RenderContext
import grails.rest.render.Renderer
import grails.util.GrailsNameUtils
import grails.web.mime.MimeType

/**
 * A default renderer for HTML that returns an appropriate model
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class DefaultHtmlRenderer<T> implements Renderer<T> {
    protected Class<T> targetType
    protected MimeType[] mimeTypes = [MimeType.XHTML, MimeType.HTML] as MimeType[]
    @Autowired(required = false)
    ProxyHandler proxyHandler

    String suffix = ""

    DefaultHtmlRenderer(Class<T> targetType) {
        this.targetType = targetType
    }

    DefaultHtmlRenderer(Class<T> targetType, MimeType mimeType) {
        this.targetType = targetType
        this.mimeTypes = [mimeType] as MimeType[]
    }

    Class<T> getTargetType() {
        return targetType
    }

    @Override
    void render(Object object, RenderContext context) {
        final mimeType = context.acceptMimeType ?: MimeType.HTML
        if (!mimeType.equals(MimeType.ALL)) {
            context.setContentType(mimeType.name)
        }

        if (context.arguments?.view) {
            context.viewName = context.arguments.view.toString()
        }

        if (!context.viewName) {
            context.setViewName(context.actionName)
        }

        if (object instanceof Errors) {
            Errors errors = (Errors)object
            def target = errors instanceof BeanPropertyBindingResult ? errors.getTarget() : null
            if (target) {
                applyModel(context, target)
            }
        } else {
            applyModel(context, object)
        }
    }

    protected void applyModel(RenderContext context, Object object) {
        if(object instanceof Map) {
            context.setModel((Map)object)
        }
        else {
            context.setModel([(resolveModelVariableName(object)): object])
        }
    }

    protected String resolveModelVariableName(Object object) {
        if(object != null) {
            if (proxyHandler != null) {
                object = proxyHandler.unwrapIfProxy(object)
            }

            Class<?> type = object.getClass()
            if (type.isArray()) {
                return GrailsNameUtils.getPropertyName(type.getComponentType()) + suffix + "Array"
            }

            if (object instanceof Collection) {
                Collection coll = (Collection) object
                if (coll.isEmpty()) {
                    return "emptyCollection"
                }

                Object first = coll.iterator().next()
                if (proxyHandler != null) {
                    first = proxyHandler.unwrapIfProxy(first)
                }
                if(coll instanceof List) {
                    return GrailsNameUtils.getPropertyName(first.getClass()) + suffix + "List"
                }
                if(coll instanceof Set) {
                    return GrailsNameUtils.getPropertyName(first.getClass()) + suffix + "Set"
                }
                return GrailsNameUtils.getPropertyName(first.getClass()) + suffix + "Collection"
            }

            if (object instanceof Map) {
                Map map = (Map)object

                if (map.isEmpty()) {
                    return "emptyMap"
                }

                Object entry = map.values().iterator().next()
                if (entry != null) {
                    if (proxyHandler != null) {
                        entry = proxyHandler.unwrapIfProxy(entry)
                    }
                    return GrailsNameUtils.getPropertyName(entry.getClass()) + suffix + "Map"
                }
            }
            else {
                return GrailsNameUtils.getPropertyName(object.getClass()) + suffix
            }
        }
        return null
    }

    MimeType[] getMimeTypes() {
        return mimeTypes
    }
}
