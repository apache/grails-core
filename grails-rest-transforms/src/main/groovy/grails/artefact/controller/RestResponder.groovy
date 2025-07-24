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
package grails.artefact.controller

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Generated
import groovy.transform.TypeCheckingMode

import jakarta.servlet.http.HttpServletResponse

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors

import grails.artefact.Controller
import grails.artefact.controller.support.ResponseRenderer
import grails.core.support.proxy.ProxyHandler
import grails.rest.Resource
import grails.rest.render.Renderer
import grails.rest.render.RendererRegistry
import grails.web.mime.MimeType
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.plugins.web.rest.render.DefaultRendererRegistry
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.web.util.GrailsApplicationAttributes

/**
 *
 * @author Jeff Brown
 * @since 3.0
 *
 */
@CompileStatic
trait RestResponder {

    private String PROPERTY_RESPONSE_FORMATS = "responseFormats"

    private RendererRegistry rendererRegistry
    private ProxyHandler proxyHandler

    @Generated
    @Autowired(required = false)
    void setRendererRegistry(RendererRegistry rendererRegistry) {
        this.rendererRegistry = rendererRegistry
    }

    @Generated
    RendererRegistry getRendererRegistry() {
        return this.rendererRegistry
    }

    @Generated
    @Autowired(required = false)
    void setProxyHandler(ProxyHandler proxyHandler) {
        this.proxyHandler = proxyHandler
    }

    @Generated
    ProxyHandler getProxyHandler() {
        return this.proxyHandler
    }

    /**
     * Same as {@link RestResponder#respond(java.lang.Object, java.lang.Object, java.util.Map)}, but here to support Groovy named arguments
     */
    @Generated
    def respond(Map args, value) {
        internalRespond value, args
    }

    /**
     * The respond method will attempt to delivery an appropriate response for the
     * requested response format and Map value.
     *
     * If the value is null then a 404 will be returned. Otherwise the {@link RendererRegistry}
     * will be consulted for an appropriate response renderer for the requested response format.
     *
     * @param value The value
     * @return
     */
    @Generated
    def respond(Map value) {
        internalRespond value
    }

    /**
     * Same as {@link RestResponder#respond(java.util.Map)}, but here to support Groovy named arguments
     */
    @Generated
    def respond(Map namedArgs, Map value) {
        internalRespond value, namedArgs
    }

    /**
     * The respond method will attempt to delivery an appropriate response for the
     * requested response format and value.
     *
     * If the value is null then a 404 will be returned. Otherwise the {@link RendererRegistry}
     * will be consulted for an appropriate response renderer for the requested response format.
     *
     * @param value The value
     * @param args The arguments
     * @return
     */
    @Generated
    def respond(value, Map args = [:]) {
        internalRespond value, args
    }

    private internalRespond(value, Map args=[:]) {
        Integer statusCode
        if (args.status) {
            final statusValue = args.status
            if (statusValue instanceof Number) {
                statusCode = statusValue.intValue()
            } else {
                if (statusValue instanceof HttpStatus) {
                    statusCode = ((HttpStatus)statusValue).value()
                } else {
                    statusCode = statusValue.toString().toInteger()
                }
            }
        }
        if (value == null) {
            return callRender([status:statusCode ?: 404 ])
        }

        if (proxyHandler != null) {
            value = proxyHandler.unwrapIfProxy(value)
        }

        final webRequest = ((Controller)this).getWebRequest()
        List<String> formats = calculateFormats(webRequest.actionName, value, args)
        final response = webRequest.getCurrentResponse()
        MimeType[] mimeTypes = getResponseFormat(response)
        def registry = rendererRegistry
        if (registry == null) {
            registry = new DefaultRendererRegistry()
            registry.initialize()
        }

        Renderer renderer = null

        for(MimeType mimeType in mimeTypes) {
            if (mimeType == MimeType.ALL && formats) {
                final allMimeTypes = MimeType.getConfiguredMimeTypes()
                final firstFormat = formats[0]
                mimeType = allMimeTypes.find { MimeType mt -> mt.extension == firstFormat}
                if(mimeType) {
                    webRequest.currentRequest.setAttribute(GrailsApplicationAttributes.RESPONSE_MIME_TYPE, mimeType)
                }
            }

            if (mimeType && formats.contains(mimeType.extension)) {
                Errors errors = value.hasProperty(GormProperties.ERRORS) ? getDomainErrors(value) : null


                if (errors && errors.hasErrors()) {
                    def target = errors instanceof BeanPropertyBindingResult ? errors.getTarget() : null
                    if (proxyHandler != null && target != null) {
                        target = proxyHandler.unwrapIfProxy(target)
                    }
                    Renderer<Errors> errorsRenderer = registry.findContainerRenderer(mimeType, Errors.class, target)
                    if (errorsRenderer) {
                        final context = new ServletRenderContext(webRequest, [model: args.model])
                        if (args.view) {
                            context.viewName = args.view as String
                        }
                        if(statusCode != null) {
                            context.setStatus(HttpStatus.valueOf(statusCode))
                        }
                        errorsRenderer.render(errors, context)
                        if(context.wasWrittenTo() && !response.isCommitted()) {
                            response.flushBuffer()
                        }
                        return
                    }

                    return callRender([status: statusCode ?: 404 ])
                }

                final valueType = value.getClass()
                if (registry.isContainerType(valueType)) {
                    renderer = registry.findContainerRenderer(mimeType, valueType, value)
                    if (renderer == null) {
                        renderer = registry.findRenderer(mimeType, value)
                    }
                } else {
                    renderer = registry.findRenderer(mimeType, value)
                }
            }

            if(renderer) break
        }

        if (renderer) {
            final context = new ServletRenderContext(webRequest, args)
            if(statusCode != null) {
                context.setStatus(HttpStatus.valueOf(statusCode))
            }
            renderer.render(value, context)
            if(context.wasWrittenTo() && !response.isCommitted()) {
                response.flushBuffer()
            }
            return
        }
        callRender([status: statusCode ?: HttpStatus.NOT_ACCEPTABLE.value() ])
    }

    private callRender(Map args) {
        ((ResponseRenderer)this).render args
    }

    private List<String> calculateFormats(String actionName, value, Map args) {
        if (args.formats) {
            return (List<String>) args.formats
        }

        if (this.hasProperty(PROPERTY_RESPONSE_FORMATS)) {
            final responseFormatsProperty = this[PROPERTY_RESPONSE_FORMATS]
            if (responseFormatsProperty instanceof List) {
                return (List<String>) responseFormatsProperty
            }
            if ((responseFormatsProperty instanceof Map) && actionName) {
                Map<String, Object> responseFormatsMap = (Map<String, Object>) responseFormatsProperty

                final responseFormatsForAction = responseFormatsMap.get(actionName)
                if (responseFormatsForAction instanceof List) {
                    return (List<String>) responseFormatsForAction
                }
                return getDefaultResponseFormats(value)
            }
            return getDefaultResponseFormats(value)
        }
        return getDefaultResponseFormats(value)
    }

    @CompileDynamic
    private MimeType[] getResponseFormat(HttpServletResponse response) {
        response.mimeTypesFormatAware
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private Errors getDomainErrors(object) {
        if (object instanceof Errors) {
            return object
        }
        final errors = object.errors
        if (errors instanceof Errors) {
            return errors
        }
        return null
    }

    private List<String> getDefaultResponseFormats(value) {
        Resource resAnn = value != null ? value.getClass().getAnnotation(Resource) : null
        if (resAnn) {
            return resAnn.formats().toList()
        }
        return MimeType.getConfiguredMimeTypes().collect { MimeType mt -> mt.extension }
    }
}
