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
package org.grails.web.mapping

import grails.config.Settings
import grails.plugins.GrailsPluginManager
import grails.plugins.PluginManagerAware
import grails.util.Environment
import grails.util.GrailsClassUtils
import grails.util.GrailsNameUtils
import grails.util.GrailsWebUtil
import grails.web.CamelCaseUrlConverter
import grails.web.UrlConverter
import grails.web.mapping.LinkGenerator
import grails.web.mapping.UrlCreator
import grails.web.mapping.UrlMapping
import grails.web.mapping.UrlMappingsHolder
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Slf4j
import jakarta.annotation.PostConstruct
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.web.servlet.mvc.DefaultRequestStateLookupStrategy
import org.grails.web.servlet.mvc.GrailsRequestStateLookupStrategy
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.WebUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpMethod

import java.util.regex.Pattern

/**
 * A link generating service for applications to use when generating links.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
@Slf4j
class DefaultLinkGenerator implements LinkGenerator, PluginManagerAware {

    private static final Pattern absoluteUrlPattern = Pattern.compile('^[A-Za-z][A-Za-z0-9+\\-.]*:.*$')

    String configuredServerBaseURL
    String contextPath
    String resourcePath

    GrailsRequestStateLookupStrategy requestStateLookupStrategy = new DefaultRequestStateLookupStrategy()

    GrailsPluginManager pluginManager

    @Autowired
    @Qualifier("grailsUrlMappingsHolder")
    UrlMappingsHolder urlMappingsHolder

    @Autowired(required = false)
    @Qualifier('grailsDomainClassMappingContext')
    MappingContext mappingContext

    @Autowired(required = false)
    UrlConverter grailsUrlConverter = new CamelCaseUrlConverter()

    @Value('${grails.resources.pattern:/static/**}')
    String resourcePattern = Settings.DEFAULT_RESOURCE_PATTERN


    DefaultLinkGenerator(String serverBaseURL, String contextPath) {
        configuredServerBaseURL = serverBaseURL
        this.contextPath = contextPath
    }

    DefaultLinkGenerator(String serverBaseURL) {
        configuredServerBaseURL = serverBaseURL
    }

    @PostConstruct
    void initializeResourcePath() {
        if (resourcePattern?.endsWith('/**')) {
            resourcePath = resourcePattern.substring(0, resourcePattern.length() - 3)
        }
    }

    /**
     * {@inheritDoc}
     */
    String link(Map attrs, String encoding = 'UTF-8') {
        def writer = new StringBuilder()
        // prefer URI attribute
        boolean includeContext = GrailsClassUtils.getBooleanFromMap(ATTRIBUTE_INCLUDE_CONTEXT, attrs, true)

        if (attrs.get(ATTRIBUTE_URI) != null) {
            def uri = attrs.get(ATTRIBUTE_URI).toString()
            if (!isUriAbsolute(uri)) {
                final base = handleAbsolute(attrs)
                if (base != null) {
                    writer.append base
                } else if (includeContext) {

                    def cp = attrs.get(ATTRIBUTE_CONTEXT_PATH)
                    if (cp == null) cp = getContextPath()
                    if (cp != null)
                        writer.append cp
                }
            }
            writer.append uri

            def params = attrs.get(ATTRIBUTE_PARAMS)

            if (params instanceof Map) {
                def charset = GrailsWebUtil.DEFAULT_ENCODING
                def paramString = params.collect { Map.Entry entry ->
                    def encodedKey = URLEncoder.encode(entry.key as String, charset)
                    def encodedValue = URLEncoder.encode(entry.value as String, charset)
                    "$encodedKey=$encodedValue"
                }.join('&')
                writer.append(uri.indexOf('?') >= 0 ? '&' : '?')
                        .append paramString
            }
        } else if (attrs.get(ATTRIBUTE_RELATIVE_URI) != null) {
            String relativeUri = attrs.get(ATTRIBUTE_RELATIVE_URI)
            String forwardUri = WebUtils.getForwardURI(requestStateLookupStrategy.webRequest.request)
            int index = forwardUri.lastIndexOf('/')
            if (index != -1) {
                writer.append forwardUri.substring(0, index + 1)
            }
            writer.append relativeUri
        } else {
            // prefer a URL attribute
            Map urlAttrs = attrs
            final urlAttribute = attrs.get(ATTRIBUTE_URL)
            if (urlAttribute instanceof Map) {
                urlAttrs = (Map) urlAttribute
            }
            if (!urlAttribute || urlAttribute instanceof Map) {
                final controllerAttribute = urlAttrs.get(ATTRIBUTE_CONTROLLER)
                final resourceAttribute = urlAttrs.get(ATTRIBUTE_RESOURCE)
                String controller
                String action = urlAttrs.get(ATTRIBUTE_ACTION)?.toString()
                def id = urlAttrs.get(ATTRIBUTE_ID)
                String httpMethod;
                final methodAttribute = urlAttrs.get(ATTRIBUTE_METHOD)
                final paramsAttribute = urlAttrs.get(ATTRIBUTE_PARAMS)
                Map params = paramsAttribute instanceof Map ? (Map) paramsAttribute : [:]

                if (resourceAttribute) {
                    String resource
                    if (resourceAttribute instanceof CharSequence)
                        resource = resourceAttribute.toString()
                    else {
                        PersistentEntity persistentEntity = (mappingContext != null) ? mappingContext.getPersistentEntity(resourceAttribute.getClass().getName()) : null
                        boolean hasId = false
                        if (persistentEntity != null) {
                            resource = persistentEntity.getDecapitalizedName()
                            hasId = true
                        } else if (DomainClassArtefactHandler.isDomainClass(resourceAttribute.getClass(), true)) {
                            resource = GrailsNameUtils.getPropertyName(resourceAttribute.getClass())
                            hasId = true
                        } else if (resourceAttribute instanceof Class) {
                            resource = GrailsNameUtils.getPropertyName(resourceAttribute)
                        } else {
                            resource = resourceAttribute.toString()
                        }
                        if (!id && hasId) {
                            id = getResourceId(resourceAttribute)
                        }
                    }
                    List tokens = resource.contains('/') ? resource.tokenize('/') : [resource]
                    controller = controllerAttribute ?: tokens[-1]
                    if (tokens.size() > 1) {
                        for (t in tokens[0..-2]) {
                            final key = "${t}Id".toString()
                            final attr = urlAttrs.remove(key)
                            // the params value might not be null
                            // only overwrite if urlAttrs actually had the key
                            if (attr) {
                                params[key] = attr
                            }
                        }
                    }
                    if (!methodAttribute && action) {
                        httpMethod = REST_RESOURCE_ACTION_TO_HTTP_METHOD_MAP.get(action.toString())
                        if (!httpMethod) {
                            httpMethod = HttpMethod.GET.toString()
                        }
                    } else if (methodAttribute && !action) {
                        def method = methodAttribute.toString().toUpperCase()
                        httpMethod = method
                        if (method == 'GET' && id) method = "${method}_ID".toString()
                        action = REST_RESOURCE_HTTP_METHOD_TO_ACTION_MAP[method]
                    } else {
                        httpMethod = methodAttribute == null ? requestStateLookupStrategy.getHttpMethod() ?: UrlMapping.ANY_HTTP_METHOD : methodAttribute.toString()
                    }

                } else {
                    controller = controllerAttribute == null ? requestStateLookupStrategy.getControllerName() : controllerAttribute.toString()
                    httpMethod = methodAttribute == null ? requestStateLookupStrategy.getHttpMethod() ?: UrlMapping.ANY_HTTP_METHOD : methodAttribute.toString()
                }

                String convertedControllerName = grailsUrlConverter.toUrlElement(controller)

                boolean isDefaultAction = false
                if (controller && !action) {
                    action = requestStateLookupStrategy.getActionName(convertedControllerName)
                    isDefaultAction = true
                }
                String convertedActionName = action
                if (action) {
                    convertedActionName = grailsUrlConverter.toUrlElement(action)
                }

                String frag = urlAttrs.get(ATTRIBUTE_FRAGMENT)?.toString()

                def mappingName = urlAttrs.get(ATTRIBUTE_MAPPING)
                if (mappingName != null) {
                    params.mappingName = mappingName
                }
                def url
                if (id != null) {
                    params.put(ATTRIBUTE_ID, id)
                }
                def pluginName = attrs.get(UrlMapping.PLUGIN)?.toString()
                def namespace = attrs.get(UrlMapping.NAMESPACE)?.toString()
                if (namespace == null) {
                    if (controller == requestStateLookupStrategy.controllerName) {
                        namespace = requestStateLookupStrategy.controllerNamespace
                    }
                }
                UrlCreator mapping = urlMappingsHolder.getReverseMappingNoDefault(controller, action, namespace, pluginName, httpMethod, params)
                if (mapping == null && isDefaultAction) {
                    mapping = urlMappingsHolder.getReverseMappingNoDefault(controller, null, namespace, pluginName, httpMethod, params)
                }
                if (mapping == null) {
                    mapping = urlMappingsHolder.getReverseMapping(controller, action, namespace, pluginName, httpMethod, params)
                }

                boolean absolute = isAbsolute(attrs)

                if (!absolute) {
                    url = mapping.createRelativeURL(convertedControllerName, convertedActionName, namespace, pluginName, params, encoding, frag)
                    final contextPathAttribute = attrs.get(ATTRIBUTE_CONTEXT_PATH)
                    final cp = contextPathAttribute == null ? getContextPath() : contextPathAttribute
                    if (attrs.get(ATTRIBUTE_BASE) || cp == null) {
                        attrs.put(ATTRIBUTE_ABSOLUTE, true)
                        writer.append handleAbsolute(attrs)
                    } else if (includeContext) {
                        writer.append cp
                    }
                    writer.append url
                } else {
                    url = mapping.createRelativeURL(convertedControllerName, convertedActionName, namespace, pluginName, params, encoding, frag)
                    writer.append handleAbsolute(attrs)
                    writer.append url
                }
            } else {
                writer.append urlAttribute
            }
        }
        return writer.toString()
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected String getResourceId(resourceAttribute) {
        try {
            // Three options for using indent():
            // 1. Check instanceof GormEntity, but that would require coupling web-common to grails-datastore-gorm
            // 2. GrailsMetaClassUtils.invokeMethodIfExists(o, "ident", new Object[0]); Slow?
            // 3. Just assuming resource is a GormEntity or has ident() implemented and catching an exception if it is not.
            def ident = resourceAttribute.ident()
            if (ident) {
                return ident.toString()
            }
        } catch (MissingMethodException | IllegalStateException ignored) {
            // An IllegalStateException occurs if GORM is not initialized.
            // A MissingMethodException if it is not a GormEntity
        }

        final id = resourceAttribute.id
        if (id) {
            return id.toString()
        }
        return null
    }

    protected boolean isAbsolute(Map attrs) {
        boolean absolute = false
        def o = attrs.get(ATTRIBUTE_ABSOLUTE)
        if (o instanceof Boolean) {
            absolute = o
        } else {
            if (o != null) {
                try {
                    def str = o.toString()
                    if (str) {
                        absolute = Boolean.parseBoolean(str)
                    }
                } catch (e) {
                }
            }
        }
        return absolute
    }

    /**
     * {@inheritDoc}
     */
    String resource(Map attrs) {
        def absolutePath = handleAbsolute(attrs)

        final contextPathAttribute = attrs.contextPath?.toString()
        if (absolutePath == null) {
            final cp = contextPathAttribute == null ? getContextPath() : contextPathAttribute
            if (cp == null) {
                absolutePath = handleAbsolute(absolute: true)
            } else {
                absolutePath = cp
            }
        }

        StringBuilder url = new StringBuilder(absolutePath?.toString() ?: '')
        def dir = attrs.dir?.toString()
        if (attrs.plugin) {
            url.append pluginManager?.getPluginPath(attrs.plugin?.toString()) ?: ''
        } else {
            if (contextPathAttribute == null) {
                def pluginContextPath = attrs.pluginContextPath?.toString()
                if (pluginContextPath != null && dir != pluginContextPath) {
                    url << pluginContextPath
                }
            }
        }

        def slash = '/'
        if (resourcePath != null) {
            url.append(resourcePath)
        }
        if (dir) {
            if (!dir.startsWith(slash)) {
                url.append slash
            }
            url.append dir
        }

        def file = attrs.file?.toString()
        if (file) {
            if (!(file.startsWith(slash) || (dir != null && dir.endsWith(slash)))) {
                url.append slash
            }
            url.append file
        }

        return url.toString()
    }

    String getContextPath() {
        if (contextPath == null) {
            contextPath = requestStateLookupStrategy.getContextPath()
        }
        return contextPath
    }

    /**
     * Check for "absolute" attribute and render server URL if available from Config or deducible in non-production.
     */
    private handleAbsolute(Map attrs) {
        def base = attrs.base
        if (base) {
            return base
        }

        if (isAbsolute(attrs)) {
            def u = makeServerURL()
            if (u) {
                return u
            }

            throw new IllegalStateException("Attribute absolute='true' specified but no grails.serverURL set in Config")
        }
    }

    private boolean isUriAbsolute(String uri) {
        // not using new URI(uri).absolute in order to avoid create the URI object, which is slow
        return absoluteUrlPattern.matcher(uri).matches()
    }

    /**
     * Get the declared URL of the server from config, or guess at localhost for non-production.
     */
    String makeServerURL() {
        def u = configuredServerBaseURL
        if (!u) {
            // Leave it null if we're in production so we can throw
            final webRequest = GrailsWebRequest.lookup()

            u = webRequest?.baseUrl
            if (!u && !Environment.isWarDeployed()) {
                u = "http://localhost:${System.getProperty('server.port') ?: '8080'}${contextPath ?: ''}"
            }
        }
        log.trace("Resolved base server URL: $u")
        return u
    }

    String getServerBaseURL() {
        return makeServerURL()
    }

    void setPluginManager(GrailsPluginManager pluginManager) {
        this.pluginManager = pluginManager
    }
}
