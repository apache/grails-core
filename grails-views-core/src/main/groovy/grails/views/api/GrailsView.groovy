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

package grails.views.api

import groovy.transform.CompileStatic

import org.springframework.context.MessageSource

import grails.config.Config
import grails.core.support.proxy.ProxyHandler
import grails.views.GrailsViewTemplate
import grails.views.ResolvableGroovyTemplateEngine
import grails.views.WritableScript
import grails.views.WriterProvider
import grails.views.api.internal.DefaultGrailsViewHelper
import grails.web.mapping.LinkGenerator
import grails.web.mime.MimeUtility
import org.grails.datastore.mapping.model.MappingContext

/**
 * A trait for all view types to extend to add methods to generate links, render other templates and so on
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
trait GrailsView extends HttpView implements WriterProvider, WritableScript {

    /**
     * The view template
     */
    GrailsViewTemplate viewTemplate

    /**
     * Whether to pretty print
     */
    boolean prettyPrint = false

    /**
     * The GORM mapping context
     */
    MappingContext getMappingContext() {
        viewTemplate.mappingContext
    }

    /**
     * Handlers for proxies
     */
    ProxyHandler getProxyHandler() {
        viewTemplate.proxyHandler
    }

    /**
     * The link generator
     */
    LinkGenerator getLinkGenerator() {
        viewTemplate.linkGenerator
    }

    /**
     * The mime utility
     */
    MimeUtility getMimeUtility() {
        viewTemplate.mimeUtility
    }

    /**
     * The template engine
     */
    ResolvableGroovyTemplateEngine getTemplateEngine() {
        (ResolvableGroovyTemplateEngine)viewTemplate.templateEngine
    }

    /**
     * The message source object
     */
    MessageSource getMessageSource() {
        viewTemplate.messageSource
    }

    /**
     * @return The current controller namespace
     */
    String controllerNamespace
    /**
     * @return The current controller name
     */
    String controllerName
    /**
     * @return The current action name
     */
    String actionName

    /**
     * @return The configuration
     */
    Config config

    private GrailsViewHelper viewHelper = new DefaultGrailsViewHelper(this)

    /**
     * @return Obtain the view helper
     */
    GrailsViewHelper getG() {
        return this.viewHelper
    }

    /**
     * Defines the model
     *
     * @param modelDefinition
     */
    void model(Closure modelDefinition) {
        // no-op, added at compile time
    }
}
