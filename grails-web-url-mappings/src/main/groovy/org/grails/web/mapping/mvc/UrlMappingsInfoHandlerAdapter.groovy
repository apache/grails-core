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
package org.grails.web.mapping.mvc

import grails.core.GrailsControllerClass
import grails.util.Environment
import grails.web.mapping.LinkGenerator
import grails.web.mapping.ResponseRedirector
import grails.web.mapping.UrlMappingInfo
import grails.web.mvc.FlashScope
import groovy.transform.CompileStatic
import org.grails.web.servlet.mvc.ActionResultTransformer
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.web.servlet.HandlerAdapter
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.InternalResourceView

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.concurrent.ConcurrentHashMap

/**
 * A {@link HandlerAdapter} that takes a matched {@link UrlMappingInfo} and executes the underlying controller producing an appropriate model
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class UrlMappingsInfoHandlerAdapter implements HandlerAdapter, ApplicationContextAware{


    ApplicationContext applicationContext

    protected Collection<ActionResultTransformer> actionResultTransformers = Collections.emptyList();
    protected Map<String, Object> controllerCache = new ConcurrentHashMap<>()
    protected ResponseRedirector redirector

    void setApplicationContext(ApplicationContext applicationContext) {
        this.actionResultTransformers = applicationContext.getBeansOfType(ActionResultTransformer.class).values();
        this.applicationContext = applicationContext
        this.redirector = new ResponseRedirector(applicationContext.getBean(LinkGenerator))
    }

    @Override
    boolean supports(Object handler) { handler instanceof UrlMappingInfo }

    @Override
    ModelAndView handle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        UrlMappingInfo info = (UrlMappingInfo)handler

        GrailsWebRequest webRequest = GrailsWebRequest.lookup(request)

        boolean isAsyncRequest = WebUtils.isAsync(request) && !WebUtils.isError(request);
        if(isAsyncRequest) {
            Object modelAndView = request.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW);
            if(modelAndView instanceof ModelAndView) {
                return (ModelAndView)modelAndView
            }
        }
        else {
            if(info instanceof GrailsControllerUrlMappingInfo) {
                GrailsControllerUrlMappingInfo controllerUrlMappingInfo = (GrailsControllerUrlMappingInfo)info
                GrailsControllerClass controllerClass = controllerUrlMappingInfo.controllerClass
                Object controller

                def fullName = controllerClass.fullName
                if( controllerClass.isSingleton() ) {
                    controller = controllerCache.get(fullName)
                    if(controller == null) {
                        controller = applicationContext ? applicationContext.getBean(fullName) : controllerClass.newInstance()
                        if( !Environment.isReloadingAgentEnabled() ) {
                            // don't cache when reloading active
                            controllerCache.put(fullName, controller)
                        }
                    }
                }
                else {
                    controller = applicationContext ? applicationContext.getBean(fullName) : controllerClass.newInstance()
                }

                def action = controllerUrlMappingInfo.actionName ?: controllerClass.defaultAction
                if (!webRequest.actionName) {
                    webRequest.actionName = action
                }
                webRequest.controllerNamespace = controllerClass.namespace
                request.setAttribute(GrailsApplicationAttributes.CONTROLLER, controller)
                def result = controllerClass.invoke(controller, action)

                if(actionResultTransformers) {
                    for(transformer in actionResultTransformers) {
                        result = transformer.transformActionResult(webRequest, action, result)
                    }
                }

                def modelAndView = request.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)
                if(modelAndView instanceof ModelAndView) {
                    return (ModelAndView) modelAndView
                }
                else if(result instanceof Map) {
                    String viewName = controllerClass.actionUriToViewName(action)
                    def finalModel = new HashMap<String, Object>()
                    def flashScope = webRequest.getFlashScope()
                    if(!flashScope.isEmpty()) {
                        def chainModel = flashScope.get(FlashScope.CHAIN_MODEL)
                        if(chainModel instanceof Map) {
                            finalModel.putAll((Map)chainModel)
                        }
                    }
                    finalModel.putAll((Map)result)

                    return new ModelAndView(viewName, finalModel)
                }
                else if(result instanceof ModelAndView) {
                    return (ModelAndView) result
                } else if(result == null &&
                          webRequest.renderView) {
                    return new ModelAndView(controllerClass.actionUriToViewName(action))
                }
            }
            else if(info.viewName) {
                return new ModelAndView(info.viewName)
            }
            else if(info.redirectInfo) {
                def i = info.redirectInfo
                if(i instanceof Map) {
                    redirector?.redirect((Map) i)
                }
                else {
                    redirector?.redirect(uri: i.toString())
                }
            } else if (info.getURI()) {
                return new ModelAndView(new InternalResourceView(info.getURI()))
            }
        }
        return null
    }

    @Override
    long getLastModified(HttpServletRequest request, Object handler) { -1 }
}
