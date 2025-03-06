/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.views

import grails.core.support.proxy.DefaultProxyHandler
import grails.core.support.proxy.ProxyHandler
import grails.web.mapping.LinkGenerator
import grails.web.mime.MimeUtility
import groovy.text.TemplateEngine
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.grails.datastore.gorm.proxy.ProxyHandlerAdapter
import org.grails.datastore.mapping.model.MappingContext
import org.springframework.context.MessageSource


/**
 * @author Graeme Rocher
 * @since 1.0
 */
@InheritConstructors
@CompileStatic
class GrailsViewTemplate extends WritableScriptTemplate {
    /**
     * The GORM mapping context
     */
    MappingContext mappingContext

    /**
     * Handlers for proxies
     */
    @Lazy ProxyHandler proxyHandler =  {
        if(mappingContext != null ) {
            def proxyHandler = mappingContext.getProxyHandler()
            if(proxyHandler != null) {
                return (ProxyHandler)new ProxyHandlerAdapter(proxyHandler)
            }
            else {
                return (ProxyHandler)new DefaultProxyHandler()
            }
        }
        else {
            return (ProxyHandler)new DefaultProxyHandler()
        }
    }()

    /**
     * The link generator
     */
    LinkGenerator linkGenerator

    /**
     * The mime utility
     */
    MimeUtility mimeUtility

    /**
     * The template engine
     */
    TemplateEngine templateEngine

    /**
     * The message source object
     */
    MessageSource messageSource

    /**
     * Whether to use absolute links
     */
    boolean useAbsoluteLinks = false
}
