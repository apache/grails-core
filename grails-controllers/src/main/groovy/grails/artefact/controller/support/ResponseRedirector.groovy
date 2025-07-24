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
package grails.artefact.controller.support

import groovy.transform.CompileStatic
import groovy.transform.Generated

import jakarta.servlet.http.HttpServletRequest

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.web.servlet.support.RequestDataValueProcessor

import grails.util.CollectionUtils
import grails.util.GrailsNameUtils
import grails.web.api.WebAttributes
import grails.web.mapping.LinkGenerator
import grails.web.mapping.UrlCreator
import grails.web.mapping.UrlMappings
import grails.web.mapping.UrlMappingsHolder
import grails.web.mapping.mvc.RedirectEventListener
import grails.web.mapping.mvc.exceptions.CannotRedirectException
import grails.web.mvc.FlashScope
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.datastore.mapping.model.config.GormProperties

/**
 * A trait for objects that redirect the response
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
trait ResponseRedirector implements WebAttributes {


    private LinkGenerator linkGenerator

    private boolean useJsessionId = false

    private RequestDataValueProcessor requestDataValueProcessor
    private Collection<RedirectEventListener> redirectListeners

    @Generated
    @Autowired(required=false)
    void setRedirectListeners(Collection<RedirectEventListener> redirectListeners) {
        this.redirectListeners = redirectListeners
    }

    @Generated
    @Autowired(required = false)
    void setRequestDataValueProcessor(RequestDataValueProcessor requestDataValueProcessor) {
        this.requestDataValueProcessor = requestDataValueProcessor
    }

    @Generated
    @Autowired
    void setGrailsLinkGenerator(LinkGenerator linkGenerator) {
        this.linkGenerator = linkGenerator
    }

    @Generated
    LinkGenerator getGrailsLinkGenerator() {
        if(this.linkGenerator == null) {
            this.linkGenerator = webRequest.getApplicationContext().getBean(LinkGenerator)
        }
        return this.linkGenerator
    }

    /**
     * Redirects for the given arguments.
     *
     * @param object A domain class
     * @return null
     */
    @Generated
    void redirect(object) {
        if(object) {

            Class<?> objectClass = object.getClass()
            boolean isDomain = DomainClassArtefactHandler.isDomainClass(objectClass) && object instanceof GroovyObject
            if(isDomain) {
                def id = ((GroovyObject)object).getProperty(GormProperties.IDENTITY)
                if(id != null) {
                    def args = [:]
                    args.put LinkGenerator.ATTRIBUTE_RESOURCE, object
                    args.put LinkGenerator.ATTRIBUTE_METHOD, HttpMethod.GET.toString()
                    redirect(args)
                    return
                }
            }
        }
        throw new CannotRedirectException("Cannot redirect for object [${object}] it is not a domain or has no identifier. Use an explicit redirect instead ")
    }

    /**
     * Redirects for the given arguments.
     *
     * @param argMap The arguments
     * @return null
     */
    @Generated
    void redirect(Map argMap) {

        if (argMap.isEmpty()) {
            throw new IllegalArgumentException("Invalid arguments for method 'redirect': $argMap")
        }

        grails.web.mapping.ResponseRedirector redirector = new grails.web.mapping.ResponseRedirector(grailsLinkGenerator)
        redirector.setRedirectListeners redirectListeners
        redirector.setRequestDataValueProcessor requestDataValueProcessor
        redirector.setUseJessionId useJsessionId

        def webRequest = webRequest
        redirector.redirect webRequest.getRequest(), webRequest.getResponse(), argMap
    }

    /**
     * Obtains the chain model which is used to chain request attributes from one request to the next via flash scope
     * @return The chainModel
     */
    @Generated
    Map getChainModel() {
        (Map)getFlash().get(FlashScope.CHAIN_MODEL)
    }


    /**
     * Chains from one action to another via an HTTP redirect. The model is retained in the following request in the 'chainModel' property within flash scope.
     *
     * @param args The arguments
     *
     * @return Result of the redirect call
     */
    @Generated
    void chain(Map args) {
        String controller = (args.controller ?: GrailsNameUtils.getLogicalPropertyName( getClass().name, ControllerArtefactHandler.TYPE)).toString()
        String action = args.action?.toString()
        String namespace = args.remove('namespace')
        String plugin = args.remove('plugin')?.toString()
        def id = args.id
        def params = CollectionUtils.getOrCreateChildMap(args, "params")
        def model = CollectionUtils.getOrCreateChildMap(args, "model")

        def actionParams = params.findAll { Map.Entry it -> it.key?.toString()?.startsWith('_action_') }
        actionParams.each { Map.Entry it -> params.remove(it.key) }


        def currentWebRequest = webRequest
        def currentFlash = currentWebRequest.flashScope
        def chainModel = currentFlash.chainModel
        if (chainModel instanceof Map) {
            chainModel.putAll(model)
            model = chainModel
        }
        currentFlash.chainModel = model


        def appCtx = currentWebRequest.applicationContext

        UrlMappings mappings = appCtx.getBean(UrlMappingsHolder.BEAN_ID, UrlMappings)

        // Make sure that if an ID was given, it is used to evaluate
        // the reverse URL mapping.
        if (id) params.id = id

        UrlCreator creator = mappings.getReverseMapping(controller, action, namespace, plugin, params)
        def response = currentWebRequest.getCurrentResponse()

        String url = creator.createURL(controller, action, namespace, plugin, params, 'utf-8')

        if (requestDataValueProcessor) {
            HttpServletRequest request = currentWebRequest.getCurrentRequest()
            url = response.encodeRedirectURL(requestDataValueProcessor.processUrl(request, url))
        } else {
            url = response.encodeRedirectURL(url)
        }
        response.sendRedirect url
    }

    @Generated
    boolean isUseJsessionId() {
        return useJsessionId
    }

    @Generated
    void setUseJsessionId(boolean useJsessionId) {
        this.useJsessionId = useJsessionId
    }
}
