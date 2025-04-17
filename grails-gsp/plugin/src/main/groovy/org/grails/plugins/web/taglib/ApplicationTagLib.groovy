/*
 * Copyright 2004-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.web.taglib

import grails.artefact.TagLibrary
import grails.config.Settings
import grails.gsp.TagLib
import grails.util.GrailsUtil
import grails.util.Metadata
import groovy.transform.CompileStatic

import grails.core.GrailsApplication
import grails.util.GrailsStringUtils
import grails.plugins.GrailsPluginManager
import grails.core.support.GrailsApplicationAware
import grails.web.mapping.LinkGenerator
import grails.web.mapping.UrlMappingsHolder
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.codehaus.groovy.runtime.InvokerHelper
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.web.servlet.support.RequestDataValueProcessor

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * The base application tag library for Grails many of which take inspiration from Rails helpers (thanks guys! :)
 * This tag library tends to get extended by others as tags within here can be re-used in said libraries
 *
 * @author Graeme Rocher
 */
@TagLib
class ApplicationTagLib implements ApplicationContextAware, InitializingBean, GrailsApplicationAware, TagLibrary {
    static returnObjectForTags = ['createLink', 'resource', 'createLinkTo', 'cookie', 'header', 'img', 'join', 'meta', 'set', 'applyCodec']

    ApplicationContext applicationContext
    GrailsPluginManager pluginManager
    GrailsApplication grailsApplication
    UrlMappingsHolder grailsUrlMappingsHolder

    @Autowired
    LinkGenerator linkGenerator

    RequestDataValueProcessor requestDataValueProcessor

    static final SCOPES = [page: 'pageScope',
                           application: 'servletContext',
                           request:'request',
                           session:'session',
                           flash:'flash']

    boolean useJsessionId = false
    boolean hasResourceProcessor = false

    void afterPropertiesSet() {
        def config = grailsApplication.config

        useJsessionId = config.getProperty(Settings.GRAILS_VIEWS_ENABLE_JSESSIONID, Boolean, false)
        hasResourceProcessor = applicationContext.containsBean('grailsResourceProcessor')

        if (applicationContext.containsBean('requestDataValueProcessor')) {
            requestDataValueProcessor = applicationContext.getBean('requestDataValueProcessor', RequestDataValueProcessor)
        }
    }

    /**
     * Obtains the value of a cookie.
     *
     * @emptyTag
     *
     * @attr name REQUIRED the cookie name
     */
    Closure cookie = { attrs ->
        request.cookies.find { it.name == attrs.name }?.value
    }

    /**
     * Renders the specified request header value.
     *
     * @emptyTag
     *
     * @attr name REQUIRED the header name
     */
    Closure header = { attrs ->
        attrs.name ? request.getHeader(attrs.name) : null
    }

    /**
     * Sets a variable in the pageContext or the specified scope.
     * The value can be specified directly or can be a bean retrieved from the applicationContext.
     *
     * @attr var REQUIRED the variable name
     * @attr value the variable value; if not specified uses the rendered body
     * @attr bean the name or the type of a bean in the applicationContext; the type can be an interface or superclass
     * @attr scope the scope name; defaults to pageScope
     */
    Closure set = { attrs, body ->
        def var = attrs.var
        if (!var) throw new IllegalArgumentException("[var] attribute must be specified to for <g:set>!")

        def scope = attrs.scope ? SCOPES[attrs.scope] : 'pageScope'
        if (!scope) throw new IllegalArgumentException("Invalid [scope] attribute for tag <g:set>!")

        def value
        if (attrs.bean) {
            value = applicationContext.getBean(attrs.bean)
        } else {
            value = attrs.value
            def containsValue = attrs.containsKey('value')
            if (!containsValue && body) value = body()
        }

        this."$scope"."$var" = value
        null
    }

    /**
     * Creates a link to a resource, generally used as a method rather than a tag.<br/>
     *
     * eg. &lt;link type="text/css" href="${createLinkTo(dir:'css',file:'main.css')}" /&gt;
     *
     * @emptyTag
     */
    Closure createLinkTo = { attrs ->
        GrailsUtil.deprecated "Tag [createLinkTo] is deprecated please use [resource] instead"
        return resource(attrs)
    }

    /**
     * Creates a link to a resource, generally used as a method rather than a tag.<br/>
     *
     * eg. &lt;link type="text/css" href="${resource(dir:'css',file:'main.css')}" /&gt;
     *
     * @emptyTag
     *
     * @attr base Sets the prefix to be added to the link target address, typically an absolute server URL. This overrides the behaviour of the absolute property, if both are specified.x≈
     * @attr contextPath the context path to use (relative to the application context path). Defaults to "" or path to the plugin for a plugin view or template.
     * @attr dir the name of the directory within the grails app to link to
     * @attr file the name of the file within the grails app to link to
     * @attr absolute If set to "true" will prefix the link target address with the value of the grails.serverURL property from Config, or http://localhost:&lt;port&gt; if no value in Config and not running in production.
     * @attr plugin The plugin to look for the resource in
     */
    Closure resource = { attrs ->
        if (!attrs.pluginContextPath && pageScope.pluginContextPath) {
            attrs.pluginContextPath = pageScope.pluginContextPath
        }
        // Use resources plugin if present, but only if file is specified - resources require files
        // But users often need to link to a folder just using dir
        def url = (hasResourceProcessor && attrs.file) ? r.resource(attrs) : linkGenerator.resource(attrs)

        return url ? processedUrl("$url", request) : url
    }

    /**
     * Render an img tag with src set to a static resource
     * @attr dir Optional name of resource directory, defaults to "images"
     * @attr file Name of resource file (optional if uri specified)
     * @attr plugin Optional the name of the grails plugin if the resource is not part of the application
     * @attr uri Optional app-relative URI path of the resource if not using dir/file attributes - only if Resources plugin is in use
     */
    Closure img = { attrs ->
        if (!attrs.uri && !attrs.dir) {
            attrs.dir = "images"
        }
        if (hasResourceProcessor) {
            return r.img(attrs)
        }

        def uri = attrs.uri ? processedUrl(attrs.uri, request) : resource(attrs)

        def excludes = ['dir', 'uri', 'file', 'plugin']
        def attrsAsString = attrsToString(attrs.findAll { !(it.key in excludes) })
        def imgSrc = uri.encodeAsHTML()
        return "<img src=\"${imgSrc}\"${attrsAsString} />"
    }

    /**
     * General linking to controllers, actions etc. Examples:<br/>
     *
     * &lt;g:link action="myaction"&gt;link 1&lt;/gr:link&gt;<br/>
     * &lt;g:link controller="myctrl" action="myaction"&gt;link 2&lt;/gr:link&gt;<br/>
     *
     * @attr action The name of the action to use in the link, if not specified the default action will be linked
     * @attr controller The name of the controller to use in the link, if not specified the current controller will be linked
     * @attr namespace The namespace of the controller to use in the link
     * @attr plugin The name of the plugin which provides the controller
     * @attr id The id to use in the link
     * @attr fragment The link fragment (often called anchor tag) to use
     * @attr mapping The named URL mapping to use to rewrite the link
     * @attr method The HTTP method specified in the corresponding URL mapping
     * @attr params A map containing URL query parameters for the link
     * @attr url A map containing the action, controller, id etc.
     * @attr uri A string for a relative path in the running app.
     * @attr relativeUri Used to specify a uri relative to the current path.
     * @attr absolute If set to "true" will prefix the link target address with the value of the grails.serverURL property from Config, or http://localhost:&lt;port&gt; if no value in Config and not running in production.
     * @attr base Sets the prefix to be added to the link target address, typically an absolute server URL. This overrides the behaviour of the absolute property, if both are specified.
     * @attr event Webflow _eventId parameter
     * @attr elementId DOM element id
     */
    Closure link = { attrs, body ->

        def writer = getOut()
        def elementId = attrs.remove('elementId')
        def linkAttrs
        if (attrs.params instanceof Map && attrs.params.containsKey('attrs')) {
            linkAttrs = attrs.params.remove('attrs').clone()
        }
        else {
            linkAttrs = [:]
        }
        writer <<  '<a href=\"'
        writer << createLink(attrs).encodeAsHTML()
        writer << '"'
        if (elementId) {
            writer << " id=\"${elementId}\""
        }
        def remainingKeys = attrs.keySet() - LinkGenerator.LINK_ATTRIBUTES
        for (key in remainingKeys) {
            writer << " " << key << "=\"" << attrs[key]?.encodeAsHTML() << "\""
        }
        for (entry in linkAttrs) {
            writer << " " << entry.key << "=\"" << entry.value?.encodeAsHTML() << "\""
        }
        writer << '>'
        writer << body()
        writer << '</a>'
    }

    @CompileStatic
    static String attrsToString(Map attrs) {
        // Output any remaining user-specified attributes
        StringBuilder sb=new StringBuilder()
        // For some strange reason Groovy creates ClassCastExceptions internally in PogoMetaMethodSite.checkCall without this hack
        for (Iterator i = InvokerHelper.asIterator(attrs); i.hasNext();) {
            Map.Entry e = (Map.Entry)i.next()
            if (e.value != null) {
                sb.append(' ')
                sb.append(e.key)
                sb.append('="')
                sb.append(InvokerHelper.invokeMethod(String.valueOf(e.value), "encodeAsHTML", null))
                sb.append('"')
            }
        }
        return sb.toString()
    }

    static LINK_WRITERS = [
        js: { url, constants, attrs ->
           return "<script src=\"${url}\"${getAttributesToRender(constants, attrs)}></script>"
        },

        link: { url, constants, attrs ->
           return "<link href=\"${url}\"${getAttributesToRender(constants, attrs)}/>"
        }
    ]

    static getAttributesToRender(constants, attrs) {
        StringBuilder sb = new StringBuilder()
        if (constants) {
            sb.append(attrsToString(constants))
        }
        if (attrs) {
            sb.append(attrsToString(attrs))
        }
        return sb.toString()
    }

    static SUPPORTED_TYPES = [
        css:[type:"text/css", rel:'stylesheet', media:'screen, projection'],
        js:[type:'text/javascript', writer:'js'],

        gif:[rel:'shortcut icon'],
        jpg:[rel:'shortcut icon'],
        png:[rel:'shortcut icon'],
        ico:[rel:'shortcut icon'],
        appleicon:[rel:'apple-touch-icon']

        // @todo add feed link types here too
    ]

    /**
     * Render the appropriate kind of external link for use in <head> based on the type of the URI.
     * For JS will render <script> tags, for CSS will render <link> with the correct rel, and so on for icons.
     * @attr uri
     * @attr dir
     * @attr file
     * @attr plugin
     * @attr type
     */
    Closure external = { attrs ->
        if (!attrs.uri) {
            attrs.uri = resource(attrs).toString()
        }
        renderResourceLink(attrs)
    }

    /**
     *
     * @attr uri
     * @attr type
     */
    protected renderResourceLink(attrs) {
        def uri = attrs.remove('uri')
        def type = attrs.remove('type')
        if (!type) {
            type = GrailsStringUtils.getFilenameExtension(uri)
        }

        def typeInfo = SUPPORTED_TYPES[type]?.clone()
        if (!typeInfo) {
            throwTagError "I can't work out the type of ${uri} with type [${type}]. Please check the URL, resource definition or specify [type] attribute"
        }

        def writerName = typeInfo.remove('writer')
        def writer = LINK_WRITERS[writerName ?: 'link']

        // Allow attrs to overwrite any constants
        attrs.each { typeInfo.remove(it.key) }

        out << writer(processedUrl(uri, request), typeInfo, attrs)
        out << "\r\n"
    }

    /**
     * Creates a grails application link from a set of attributes. This
     * link can then be included in links, ajax calls etc. Generally used as a method call
     * rather than a tag eg.<br/>
     *
     * &lt;a href="${createLink(action:'list')}"&gt;List&lt;/a&gt;
     *
     * @emptyTag
     *
     * @attr controller The name of the controller to use in the link, if not specified the current controller will be linked
     * @attr action The name of the action to use in the link, if not specified the default action will be linked
     * @attr uri relative URI
     * @attr url A map containing the action, controller, id etc.
     * @attr base Sets the prefix to be added to the link target address, typically an absolute server URL. This overrides the behaviour of the absolute property, if both are specified.
     * @attr absolute If set to "true" will prefix the link target address with the value of the grails.serverURL property from Config, or http://localhost:&lt;port&gt; if no value in Config and not running in production.
     * @attr id The id to use in the link
     * @attr fragment The link fragment (often called anchor tag) to use
     * @attr params A map containing URL query parameters
     * @attr mapping The named URL mapping to use to rewrite the link
     * @attr event Webflow _eventId parameter
     */
    Closure createLink = { attrs ->
       return doCreateLink(attrs instanceof  Map ? (Map) attrs : Collections.emptyMap())
    }

    @CompileStatic
    protected String doCreateLink(Map attrs) {
        Map urlAttrs = attrs
        if (attrs.url instanceof Map) {
            urlAttrs = (Map)attrs.url
        }
        Map params = urlAttrs.params && urlAttrs.params instanceof Map ? (Map)urlAttrs.params : [:]
        HttpServletRequest req = (HttpServletRequest)getProperty('request')
        HttpServletResponse res = (HttpServletResponse)getProperty('response')
        def flowExecutionKey = req.getAttribute('flowExecutionKey')
        if (flowExecutionKey) {
            if (attrs.controller == null && attrs.action == null && attrs.url == null && attrs.uri == null) {
                urlAttrs[LinkGenerator.ATTRIBUTE_ACTION] = GrailsWebRequest.lookup().actionName
            }
        }
        if (urlAttrs.event) {
            params."_eventId" = urlAttrs.remove('event')
            urlAttrs.params = params
        }

        String generatedLink = linkGenerator.link(urlAttrs, req.getAttribute('characterEncoding')?.toString())
        generatedLink = processedUrl(generatedLink, req)

        return useJsessionId ? res.encodeURL(generatedLink) : generatedLink
    }

    /**
     * Helper method for creating tags called like:<br/>
     * <pre>
     *    withTag(name:'script',attrs:[type:'text/javascript']) {
     *    ...
     *    }
     * </pre>
     * @attr name REQUIRED the tag name
     * @attr attrs tag attributes
     */
    Closure withTag = { attrs, body ->
        def writer = out
        writer << "<${attrs.name}"
        attrs.attrs?.each { k,v ->
            if (!v) return
            if (v instanceof Closure) {
                writer << " $k=\""
                v()
                writer << '"'
            }
            else {
                writer << " $k=\"$v\""
            }
        }
        writer << '>'
        writer << body()
        writer << "</${attrs.name}>"
    }

    /**
     * Uses the Groovy JDK join method to concatenate the toString() representation of each item
     * in this collection with the given separator.
     *
     * @emptyTag
     *
     * @attr REQUIRED in The collection to iterate over
     * @attr delimiter The value of the delimiter to use during the join. If no delimiter is specified then ", " (a comma followed by a space) will be used as the delimiter.
     */
    Closure join = { attrs ->
        def collection = attrs.'in'
        if (collection == null) {
            throwTagError('Tag ["join"] missing required attribute ["in"]')
        }

        def delimiter = attrs.delimiter == null ? ', ' : attrs.delimiter
        return collection.join(delimiter)
    }

    /**
     * Output application metadata that is loaded from application.yml and grails.build.info if it is present.
     *
     * @emptyTag
     *
     * @attr name REQUIRED the metadata key
     */
    Closure meta = { attrs ->
        if (!attrs.name) {
            throwTagError('Tag ["meta"] missing required attribute ["name"]')
        }
        return Metadata.current[attrs.name]
    }

    /**
     * Filters the url through the RequestDataValueProcessor bean if it is registered.
     */
    String processedUrl(String link, request) {
        if (requestDataValueProcessor == null) {
            return link
        }

        return requestDataValueProcessor.processUrl(request, link)
    }

    Closure applyCodec = { Map attrs, Closure body ->
        // encoding is handled in GroovyPage.invokeTag and GroovyPage.captureTagOutput
        body()
    }
}
