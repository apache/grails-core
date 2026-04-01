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

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.http.HttpMethod

import grails.config.Settings
import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.core.GrailsControllerClass
import grails.util.GrailsNameUtils
import grails.web.UrlConverter
import grails.web.mapping.UrlCreator
import grails.web.mapping.UrlMapping
import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappings
import grails.web.mapping.UrlMappingsHolder
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * A {@link UrlMappingsHolder} implementation that matches URLs directly onto controller instances
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@Slf4j
abstract class AbstractGrailsControllerUrlMappings implements UrlMappings {

    UrlMappings urlMappingsHolderDelegate
    UrlConverter urlConverter
    boolean validateWildcardMappings
    ConcurrentHashMap<ControllerKey, GrailsControllerClass> mappingsToGrailsControllerMap = new ConcurrentHashMap<>()
    ConcurrentHashMap<ControllerKey, GrailsControllerClass> deferredMappings = new ConcurrentHashMap<>()

    AbstractGrailsControllerUrlMappings(GrailsApplication grailsApplication, UrlMappings urlMappingsHolderDelegate, UrlConverter urlConverter = null) {
        this.urlMappingsHolderDelegate = urlMappingsHolderDelegate
        this.urlConverter = urlConverter
        this.validateWildcardMappings = grailsApplication.config.getProperty(
                Settings.URL_MAPPING_VALIDATE_WILDCARDS, Boolean, true)
        def controllerArtefacts = grailsApplication.getArtefacts(ControllerArtefactHandler.TYPE)
        for (GrailsClass gc in controllerArtefacts) {
            registerController((GrailsControllerClass) gc)
        }

        for (Map.Entry<ControllerKey, GrailsControllerClass> entry: deferredMappings.entrySet()) {
            ControllerKey key = entry.key
            GrailsControllerClass deferredController = entry.value
            GrailsControllerClass existingController = mappingsToGrailsControllerMap.get(key)

            if (existingController == null) {
                mappingsToGrailsControllerMap.put(key, deferredController)
            } else if (key.namespace == null && deferredController.namespace == null && existingController.namespace != null) {
                // A non-namespaced controller is a better match for a null-namespace key
                // than a namespaced controller that registered here as a fallback
                mappingsToGrailsControllerMap.put(key, deferredController)
            }
        }
    }

    Collection<UrlMapping> addMappings(Closure mappings) {
        urlMappingsHolderDelegate.addMappings(mappings)
    }

    Set<HttpMethod> allowedMethods(String uri) {
        urlMappingsHolderDelegate.allowedMethods(uri)
    }

    @Override
    List getExcludePatterns() {
        urlMappingsHolderDelegate.getExcludePatterns()
    }

    @Override
    UrlCreator getReverseMapping(String controller, String action, String pluginName, Map params) {
        urlMappingsHolderDelegate.getReverseMapping(controller, action, pluginName, params)
    }

    @Override
    UrlCreator getReverseMapping(String controller, String action, String namespace, String pluginName, String httpMethod, Map params) {
        urlMappingsHolderDelegate.getReverseMapping(controller, action, namespace, pluginName, httpMethod, params)
    }

    @Override
    UrlCreator getReverseMapping(String controller, String action, String namespace, String pluginName, String httpMethod, String version, Map params) {
        urlMappingsHolderDelegate.getReverseMapping(controller, action, namespace, pluginName, httpMethod, version, params)
    }

    @Override
    UrlCreator getReverseMapping(String controller, String action, String namespace, String pluginName, Map params) {
        urlMappingsHolderDelegate.getReverseMapping(controller, action, namespace, pluginName, params)
    }

    @Override
    UrlCreator getReverseMapping(String controller, String action, Map params) {
        urlMappingsHolderDelegate.getReverseMapping(controller, action, params)
    }

    @Override
    UrlCreator getReverseMappingNoDefault(String controller, String action, Map params) {
        urlMappingsHolderDelegate.getReverseMappingNoDefault(controller, action, params)
    }

    @Override
    UrlCreator getReverseMappingNoDefault(String controller, String action, String namespace, String pluginName, String httpMethod, Map params) {
        urlMappingsHolderDelegate.getReverseMappingNoDefault(controller, action, namespace, pluginName, httpMethod, params)
    }

    @Override
    UrlCreator getReverseMappingNoDefault(String controller, String action, String namespace, String pluginName, String httpMethod, String version, Map params) {
        urlMappingsHolderDelegate.getReverseMappingNoDefault(controller, action, namespace, pluginName, httpMethod, version, params)
    }

    @Override
    UrlMappingInfo match(String uri) {
        def info = urlMappingsHolderDelegate.match(uri)
        return collectControllerMapping(info)
    }

    @Override
    UrlMappingInfo matchStatusCode(int responseCode) {
        return collectControllerMapping(urlMappingsHolderDelegate.matchStatusCode(responseCode))
    }

    @Override
    UrlMappingInfo matchStatusCode(int responseCode, Throwable e) {
        return collectControllerMapping(urlMappingsHolderDelegate.matchStatusCode(responseCode, e))
    }

    void registerController(GrailsControllerClass controller) {
        boolean hasUrlConverter = urlConverter != null
        if (hasUrlConverter) {
            controller.registerUrlConverter(urlConverter)
        }
        def namespace = hasUrlConverter ? urlConverter.toUrlElement(controller.namespace) : controller.namespace
        def plugin = hasUrlConverter ? urlConverter.toUrlElement(controller.pluginName) : controller.pluginName
        final boolean hasNamespace = namespace != null
        final boolean hasPlugin = plugin != null

        def controllerName = hasUrlConverter ? urlConverter.toUrlElement(controller.logicalPropertyName) : controller.logicalPropertyName
        String pluginNameToRegister = hasPlugin ? GrailsNameUtils.getPropertyNameForLowerCaseHyphenSeparatedName(plugin) : null

        def defaultActionKey = new ControllerKey(namespace, controllerName, null, pluginNameToRegister)

        mappingsToGrailsControllerMap.put(defaultActionKey, controller)

        //Plugins should override others. Application controllers defaults should be deferred to ensure the right controller/action is chosen due to order being non deterministic
        Map<ControllerKey, GrailsControllerClass> mapToUse = plugin ? mappingsToGrailsControllerMap : deferredMappings

        if (hasNamespace) {
            def noNamespaceDefaultActionKey = new ControllerKey(null, controllerName, null, pluginNameToRegister)
            mapToUse.put(noNamespaceDefaultActionKey, controller)
            if (hasPlugin) {
                def noNamespaceNoPluginDefaultActionKey = new ControllerKey(null, controllerName, null, null)
                mapToUse.put(noNamespaceNoPluginDefaultActionKey, controller)
            }
        }
        if (hasPlugin) {
            def noPluginDefaultActionKey = new ControllerKey(namespace, controllerName, null, null)
            mapToUse.put(noPluginDefaultActionKey, controller)
        }

        log.debug('Registering controller: namespace={}, name={}, plugin={}, actions={}', namespace, controllerName, pluginNameToRegister, controller.actions)
        for (action in controller.actions) {
            action = hasUrlConverter ? urlConverter.toUrlElement(action) : action
            def withPluginKey = new ControllerKey(namespace, controllerName, action, pluginNameToRegister)

            mappingsToGrailsControllerMap.put(withPluginKey, controller)
            if (hasNamespace) {
                def withPluginKeyWithoutNamespaceKey = new ControllerKey(null, controllerName, action, pluginNameToRegister)

                mapToUse.put(withPluginKeyWithoutNamespaceKey, controller)
                if (hasPlugin) {
                    def withoutPluginKeyWithoutNamespace = new ControllerKey(null, controllerName, action, null)
                    mapToUse.put(withoutPluginKeyWithoutNamespace, controller)
                }
            }

            if (hasPlugin) {
                def withoutPluginKey = new ControllerKey(namespace, controllerName, action, null)
                mapToUse.put(withoutPluginKey, controller)
            }
        }
    }

    private static final Set<String> ROUTING_PARAMS = ['controller', 'action', 'namespace', 'plugin', 'format', 'id', 'version'] as Set

    protected UrlMappingInfo[] collectControllerMappings(UrlMappingInfo[] infos) {
        def webRequest = GrailsWebRequest.lookup()
        List<UrlMappingInfo> wildcardActionMatches = []
        List<UrlMappingInfo> otherMatches = []
        boolean hasPureLiteralMatch = false
        Set<String> explicitControllers = new HashSet<>()
        for (UrlMappingInfo info : infos) {
            if (info.redirectInfo) {
                otherMatches.add(info)
                continue
            }
            if (webRequest != null) {
                webRequest.resetParams()
                info.configure(webRequest)
            }
            ControllerKey controllerKey = new ControllerKey(info.namespace, info.controllerName, info.actionName, info.pluginName)
            GrailsControllerClass controllerClass = info ? mappingsToGrailsControllerMap.get(controllerKey) : null
            if (controllerClass) {
                def wrapped = new GrailsControllerUrlMappingInfo(controllerClass, info)
                if (validateWildcardMappings && info.hasWildcardCaptures()) {
                    wildcardActionMatches.add(wrapped)
                } else {
                    otherMatches.add(wrapped)
                    explicitControllers.add(info.controllerName)
                    if (!hasPureLiteralMatch) {
                        // A pure literal match has no URL-captured parameters beyond standard
                        // routing params (e.g., "/users" but not "/users/$username")
                        def params = info.parameters
                        hasPureLiteralMatch = params == null || params.keySet().every { it in ROUTING_PARAMS }
                    }
                }
            } else if (!validateWildcardMappings || !info.hasWildcardCaptures()) {
                otherMatches.add(info)
            }
            // else: wildcard-captured values didn't match a registered controller/action — skip
        }
        if (hasPureLiteralMatch) {
            // Pure literal path (e.g., /community) — demote all wildcard matches
            (otherMatches + wildcardActionMatches) as UrlMappingInfo[]
        } else {
            // Parameterized path (e.g., /users/$username) — only demote wildcard
            // matches that target the same controller as an explicit mapping
            // (e.g., post /invites → create demotes /invites/$action? → index,
            //  but $controller=feed is promoted over $username=feed)
            List<UrlMappingInfo> promoted = wildcardActionMatches.findAll { !(it.controllerName in explicitControllers) }
            List<UrlMappingInfo> demoted = wildcardActionMatches.findAll { it.controllerName in explicitControllers }
            (promoted + otherMatches + demoted) as UrlMappingInfo[]
        }
    }

    protected UrlMappingInfo collectControllerMapping(UrlMappingInfo info) {
        GrailsControllerClass  controllerClass = info ? mappingsToGrailsControllerMap.get(new ControllerKey(info.namespace, info.controllerName, info.actionName, info.pluginName)) : null

        if (controllerClass && info) {
            return new GrailsControllerUrlMappingInfo(controllerClass, info)
        } else {
            return info
        }
    }

    @Canonical
    static class ControllerKey {
        String namespace
        String controller
        String action
        String plugin
    }
}

