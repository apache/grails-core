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

import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Slf4j

import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation

import jakarta.annotation.PostConstruct

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ResolvableType
import org.springframework.http.HttpMethod

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
import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.core.GrailsControllerClass
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.web.servlet.mvc.DefaultRequestStateLookupStrategy
import org.grails.web.servlet.mvc.GrailsRequestStateLookupStrategy
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.WebUtils

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
    @Qualifier('grailsUrlMappingsHolder')
    UrlMappingsHolder urlMappingsHolder

    @Autowired(required=false)
    @Qualifier('grailsDomainClassMappingContext')
    MappingContext mappingContext

    @Autowired(required = false)
    UrlConverter grailsUrlConverter = new CamelCaseUrlConverter()

    @Autowired(required = false)
    GrailsApplication grailsApplication

    private volatile ControllerIndex controllerIndex

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
                    writer.append(base)
                }
                else if (includeContext) {

                    def cp = attrs.get(ATTRIBUTE_CONTEXT_PATH)
                    if (cp == null) cp = getContextPath()
                    if (cp != null)
                        writer.append(cp)
                }
            }
            writer.append(uri)

            def params = attrs.get(ATTRIBUTE_PARAMS)

            if (params instanceof Map) {
                def charset = GrailsWebUtil.DEFAULT_ENCODING
                def paramString = params.collect { Map.Entry entry ->
                    def encodedKey = URLEncoder.encode(entry.key as String, charset)
                    def encodedValue = URLEncoder.encode(entry.value as String, charset)
                    "$encodedKey=$encodedValue"
                }.join('&')
                writer.append(uri.indexOf('?') >= 0 ? '&' : '?')
                      .append(paramString)
            }
        }
        else if (attrs.get(ATTRIBUTE_RELATIVE_URI) != null) {
            String relativeUri = attrs.get(ATTRIBUTE_RELATIVE_URI)
            String forwardUri = WebUtils.getForwardURI(requestStateLookupStrategy.webRequest.request)
            int index = forwardUri.lastIndexOf('/')
            if (index != -1) {
                writer.append(forwardUri.substring(0, index + 1))
            }
            writer.append(relativeUri)
        }
        else {
            // prefer a URL attribute
            Map urlAttrs = attrs
            final urlAttribute = attrs.get(ATTRIBUTE_URL)
            if (urlAttribute instanceof Map) {
                urlAttrs = (Map) urlAttribute
            }
            if (!urlAttribute || urlAttribute instanceof Map) {
                LinkTarget target = resolveLinkTarget(attrs, urlAttrs)
                String controller = target.controller
                String action = target.action
                String namespace = target.namespace
                String httpMethod = target.httpMethod
                boolean isDefaultAction = target.defaultAction
                final paramsAttribute = urlAttrs.get(ATTRIBUTE_PARAMS)
                Map params = paramsAttribute instanceof Map ? (Map) paramsAttribute : [:]
                for (String parent in target.parentResources) {
                    final key = "${parent}Id".toString()
                    final attr = urlAttrs.remove(key)
                    // the params value might not be null
                    // only overwrite if urlAttrs actually had the key
                    if (attr) {
                        params[key] = attr
                    }
                }

                String convertedControllerName = grailsUrlConverter.toUrlElement(controller)
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
                if (target.id != null) {
                    params.put(ATTRIBUTE_ID, target.id)
                }
                def pluginName = attrs.get(UrlMapping.PLUGIN)?.toString()
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
                        writer.append(handleAbsolute(attrs))
                    }
                    else if (includeContext) {
                        writer.append(cp)
                    }
                    writer.append(url)
                }
                else {
                    url = mapping.createRelativeURL(convertedControllerName, convertedActionName, namespace, pluginName, params, encoding, frag)
                    writer.append(handleAbsolute(attrs))
                    writer.append(url)
                }
            } else {
                writer.append(urlAttribute)
            }
        }
        return writer.toString()
    }

    /**
     * Resolves what a link built from the given attributes targets, without changing them: the controller,
     * action and namespace, the HTTP method its reverse mapping is chosen for, and the id and parent
     * resources a {@code resource} contributes. Each can depend on the request the link is generated in.
     */
    private LinkTarget resolveLinkTarget(Map attrs, Map urlAttrs) {
        final controllerAttribute = urlAttrs.get(ATTRIBUTE_CONTROLLER)
        final resourceAttribute = urlAttrs.get(ATTRIBUTE_RESOURCE)
        String controller
        ResourceTarget resourceTarget = null
        String action = urlAttrs.get(ATTRIBUTE_ACTION)?.toString()
        def id = urlAttrs.get(ATTRIBUTE_ID)
        String httpMethod
        final methodAttribute = urlAttrs.get(ATTRIBUTE_METHOD)
        List<String> parentResources = Collections.emptyList()

        if (truthy(resourceAttribute)) {
            // A link naming its controller targets that controller, so the controllers serving the resource
            // are not resolved, and no ambiguity among them is reported.
            boolean namesController = truthy(controllerAttribute)
            String resource
            if (resourceAttribute instanceof CharSequence)
                resource = resourceAttribute.toString()
            else {
                PersistentEntity persistentEntity = (mappingContext != null) ? mappingContext.getPersistentEntity(resourceAttribute.getClass().getName()) : null
                boolean hasId = persistentEntity != null || DomainClassArtefactHandler.isDomainClass(resourceAttribute.getClass(), true)
                if (!truthy(id) && hasId) {
                    id = getResourceId(resourceAttribute)
                }
                if (persistentEntity != null && !namesController) {
                    resourceTarget = resolveResourceTarget(persistentEntity, attrs, resourceAction(action, methodAttribute, id))
                    resource = resourceTarget.controller
                } else if (hasId) {
                    resource = GrailsNameUtils.getPropertyName(resourceAttribute.getClass())
                } else if (resourceAttribute instanceof Class) {
                    // A domain class rather than an instance, used where loading the instance would
                    // defeat the point, such as an uninitialised association rendered from its proxy.
                    PersistentEntity classEntity = (mappingContext != null) ?
                            mappingContext.getPersistentEntity(((Class) resourceAttribute).name) : null
                    if (classEntity != null && !namesController) {
                        resourceTarget = resolveResourceTarget(classEntity, attrs, resourceAction(action, methodAttribute, id))
                        resource = resourceTarget.controller
                    } else {
                        resource = GrailsNameUtils.getPropertyName(resourceAttribute)
                    }
                } else {
                    resource = resourceAttribute.toString()
                }
            }
            List<String> tokens = resource.contains('/') ? resource.tokenize('/') : [resource]
            controller = namesController ? controllerAttribute.toString() : tokens[-1]
            if (tokens.size() > 1) {
                parentResources = tokens[0..-2]
            }
            if (!truthy(methodAttribute) && truthy(action)) {
                httpMethod =  REST_RESOURCE_ACTION_TO_HTTP_METHOD_MAP.get(action)
                if (!truthy(httpMethod)) {
                    httpMethod = HttpMethod.GET.toString()
                }
            }
            else if (truthy(methodAttribute) && !truthy(action)) {
                httpMethod = methodAttribute.toString().toUpperCase()
                action = resourceAction(null, methodAttribute, id)
            }
            else {
                httpMethod = methodAttribute == null ? requestHttpMethod() : methodAttribute.toString()
            }

        }
        else {
            controller = controllerAttribute == null ? requestStateLookupStrategy.getControllerName() : controllerAttribute.toString()
            httpMethod = methodAttribute == null ? requestHttpMethod() : methodAttribute.toString()
        }

        boolean defaultAction = false
        if (truthy(controller) && !truthy(action)) {
            action = requestStateLookupStrategy.getActionName(grailsUrlConverter.toUrlElement(controller))
            defaultAction = true
        }
        String pluginName = attrs.get(UrlMapping.PLUGIN)?.toString()
        // A resource link resolved from the request context carries the namespace that chose it. One naming
        // its controller is not resolved, so its namespace is inferred for that controller as any other's.
        String namespace = resourceTarget != null && resourceTarget.pinsNamespace ?
                resourceTarget.namespace :
                resolveNamespace(controller, pluginName, attrs)
        return new LinkTarget(controller, action, defaultAction, namespace, httpMethod, id, parentResources)
    }

    /**
     * Describes what a link built from the given attributes resolves to, for a cache of generated links to
     * key on alongside the attributes themselves: the controller, namespace, action and HTTP method the
     * link targets, any of which can depend on the request it is generated in. Links that target a URI
     * rather than a controller resolve to nothing.
     */
    @PackageScope
    String resolvedLinkKey(Map attrs) {
        if (attrs.get(ATTRIBUTE_URI) != null || attrs.get(ATTRIBUTE_RELATIVE_URI) != null) {
            return ''
        }
        def urlAttribute = attrs.get(ATTRIBUTE_URL)
        if (truthy(urlAttribute) && !(urlAttribute instanceof Map)) {
            return ''
        }
        LinkTarget target = resolveLinkTarget(attrs, urlAttribute instanceof Map ? (Map) urlAttribute : attrs)
        return new StringBuilder(96).append('target[controller:').append(target.controller)
                .append(', namespace:').append(target.namespace)
                .append(', action:').append(target.action)
                .append(', method:').append(target.httpMethod).append(']').toString()
    }

    /**
     * @return the current request's HTTP method, or {@link UrlMapping#ANY_HTTP_METHOD} outside a request
     */
    private String requestHttpMethod() {
        String method = requestStateLookupStrategy.getHttpMethod()
        return truthy(method) ? method : UrlMapping.ANY_HTTP_METHOD
    }

    /**
     * Groovy truth, as {@code if (value)} applies it. Statically compiled code applies it to a String or a
     * number by dispatching {@code asBoolean} through the metaclass, which the cache of generated links
     * would otherwise pay several times on every hit, as it resolves each link to key on it.
     */
    private static boolean truthy(Object value) {
        if (value == null) {
            return false
        }
        if (value instanceof CharSequence) {
            return ((CharSequence) value).length() > 0
        }
        if (value instanceof Boolean) {
            return (Boolean) value
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0
        }
        if (value instanceof Collection) {
            return !((Collection) value).isEmpty()
        }
        if (value instanceof Map) {
            return !((Map) value).isEmpty()
        }
        return DefaultTypeTransformation.castToBoolean(value)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String getDefaultNamespace(String controller, String pluginName) {
        if (controller == null) {
            return null
        }
        // Preserve the historical behaviour of reusing the current request namespace when the link
        // targets the controller currently handling the request.
        if (Objects.equals(controller, requestStateLookupStrategy.controllerName)) {
            return requestStateLookupStrategy.controllerNamespace
        }

        // A plugin-provided target is resolved within that plugin, so do not infer a namespace from
        // the application's own controllers, which could pick an unrelated same-named controller.
        if (pluginName != null) {
            return null
        }

        ControllerIndex index = currentControllerIndex()
        Set<ControllerRef> candidates = index.named(controller)
        if (candidates.isEmpty()) {
            // No registered controller has the name, so nothing is nearer than the namespace the link is
            // made from: stay in it, as an unqualified reference resolves against where it is made.
            return requestStateLookupStrategy.controllerNamespace
        }
        ControllerRef nearest = nearestController(candidates, controller, null, false)
        if (nearest == null) {
            reportAmbiguousNamespace(index, controller, candidates)
            return null
        }
        return nearest.namespace
    }

    /**
     * Warns, once per controller name for the controllers the index holds, that a link named a controller
     * defined in several namespaces without saying which, from outside all of them, so no namespace could be
     * inferred.
     */
    private void reportAmbiguousNamespace(ControllerIndex index, String controller, Set<ControllerRef> candidates) {
        if (index.reportedControllerNames.add(controller)) {
            Set<String> namespaces = new TreeSet<>()
            for (ControllerRef candidate in candidates) {
                namespaces.add(candidate.namespace)
            }
            log.warn('A link to controller [{}] names no namespace, but the controller is defined in the namespaces {} and in neither the default namespace nor the namespace of the current request. No namespace was inferred; pass a namespace attribute to choose one.',
                    controller, namespaces)
        }
    }

    /**
     * The registered controllers, indexed for resolving links. {@code getArtefacts} returns a cached array
     * that is replaced with a new instance whenever the set of controllers changes (late registration in
     * tests, a development-mode reload, or a namespace edit), so comparing the array identity rebuilds the
     * index on any such change while staying O(1) on the common path where nothing changed.
     */
    private ControllerIndex currentControllerIndex() {
        GrailsApplication application = grailsApplication
        if (application == null) {
            return ControllerIndex.EMPTY
        }
        GrailsClass[] controllers = application.getArtefacts(ControllerArtefactHandler.TYPE)
        MappingContext context = mappingContext
        ControllerIndex index = controllerIndex
        if (index == null || !index.isFor(controllers, context)) {
            index = buildControllerIndex(controllers, context)
            controllerIndex = index
        }
        return index
    }

    private ControllerIndex buildControllerIndex(GrailsClass[] controllers, MappingContext context) {
        Map<String, Set<ControllerRef>> byName = new HashMap<>()
        Map<String, Set<ControllerRef>> byDomainClass = new HashMap<>()
        Map<ControllerRef, Set<String>> actions = new HashMap<>()
        for (GrailsClass gc in controllers) {
            GrailsControllerClass controllerClass = (GrailsControllerClass) gc
            String name = controllerClass.logicalPropertyName
            if (name == null) {
                continue
            }
            ControllerRef ref = new ControllerRef(name, controllerClass.namespace)
            indexUnder(byName, name, ref)
            Set<String> refActions = actions.get(ref)
            if (refActions == null) {
                refActions = new HashSet<>()
                actions.put(ref, refActions)
            }
            refActions.addAll(controllerClass.actions)
            String domainClassName = context != null ? domainClassNameFor(controllerClass.clazz, context) : null
            if (domainClassName != null) {
                indexUnder(byDomainClass, domainClassName, ref)
            }
        }
        return new ControllerIndex(controllers, context, byName, byDomainClass, actions)
    }

    private static void indexUnder(Map<String, Set<ControllerRef>> index, String key, ControllerRef ref) {
        Set<ControllerRef> refs = index.get(key)
        if (refs == null) {
            refs = new HashSet<>()
            index.put(key, refs)
        }
        refs.add(ref)
    }

    /**
     * Clears the cached index of controllers, and with it the ambiguities reported against them, so it is
     * rebuilt on next use. The index is also rebuilt whenever the registered controllers change, as during
     * a development-mode reload.
     */
    void resetControllerNamespaceCache() {
        controllerIndex = null
    }

    /**
     * Resolves the controller a {@code resource} link for the given entity targets: the nearest controller
     * serving the domain class, as {@link #nearestController} chooses it. A controller serves a domain
     * class when it is named after it, or when it declares it as a generic type argument, as
     * {@code PeopleController extends RestfulController<Person>} does, and it defines the action the link
     * targets, so a controller declaring the domain class for another purpose, such as a report, is not
     * sent links it cannot handle. The link targets the explicit {@code namespace} when one is given, and
     * otherwise the request's own namespace.
     *
     * <p>When no candidate is unambiguous, the link targets the controller name the candidates share, if
     * they share one, and otherwise the domain class name, as before; either way the namespace is then
     * inferred for that name. Falling back to the domain class name while more than one candidate was
     * equally near is reported, as the link may then target a controller that does not exist.</p>
     *
     * @param entity the domain class being linked to
     * @param attrs the link attributes, which may carry an explicit {@code namespace}
     * @param action the action the link targets, as {@link #resourceAction} determines it
     * @return the target controller, and its namespace when the scope chain found it
     */
    private ResourceTarget resolveResourceTarget(PersistentEntity entity, Map attrs, String action) {
        String derivedName = entity.getDecapitalizedName()
        ControllerIndex index = currentControllerIndex()
        Set<ControllerRef> serving = servingControllers(index, entity, derivedName, action)
        if (serving.isEmpty()) {
            return new ResourceTarget(derivedName, null, false)
        }
        boolean explicitNamespace = attrs != null && attrs.containsKey(ATTRIBUTE_NAMESPACE)
        String targetNamespace = explicitNamespace ? resolveNamespace(derivedName, null, attrs) : null
        ControllerRef nearest = nearestController(serving, derivedName, targetNamespace, explicitNamespace)
        if (nearest != null) {
            // An explicit namespace is applied by the caller already; otherwise carry the one found.
            return new ResourceTarget(nearest.name, nearest.namespace, !explicitNamespace)
        }
        Set<String> names = new HashSet<>()
        for (ControllerRef ref in serving) {
            names.add(ref.name)
        }
        if (names.size() == 1) {
            return new ResourceTarget(names.iterator().next(), null, false)
        }
        // An explicit namespace with no candidate in it is the caller's choice rather than an ambiguity.
        Set<ControllerRef> tied = new HashSet<>()
        for (ControllerRef ref in serving) {
            if (!explicitNamespace || Objects.equals(ref.namespace, targetNamespace)) {
                tied.add(ref)
            }
        }
        if (tied.size() > 1) {
            reportAmbiguousResource(index, entity, derivedName, tied)
        }
        return new ResourceTarget(derivedName, null, false)
    }

    /**
     * Warns, once per domain class for the controllers the index holds, that a resource link named no
     * controller and more than one controller serving the domain class was equally near, so the controller
     * named after it was assumed.
     */
    private void reportAmbiguousResource(ControllerIndex index, PersistentEntity entity, String derivedName,
                                         Set<ControllerRef> tied) {
        if (index.reportedDomainClasses.add(entity.name)) {
            Set<String> controllers = new TreeSet<>()
            for (ControllerRef ref in tied) {
                controllers.add(ref.namespace != null ? "${ref.namespace}/${ref.name}".toString() : ref.name)
            }
            log.warn('A link to a [{}] names no controller, and the controllers serving it, {}, are equally near to where it is rendered. The controller named after the domain class, [{}], was assumed; pass a controller attribute to choose one.',
                    entity.name, controllers, derivedName)
        }
    }

    /**
     * Chooses, among the controllers a link could target, the one nearest to where the link is rendered,
     * trying scopes from the most specific to the least, as code resolves a name:
     *
     * <ol>
     *   <li>the controller handling the current request, if it is a candidate in the targeted
     *       namespace</li>
     *   <li>the candidate in the targeted namespace</li>
     *   <li>the candidate in the default namespace</li>
     *   <li>the candidate in any namespace</li>
     * </ol>
     *
     * <p>A scope holding more than one candidate chooses the one with the conventional name, the
     * controller named after the domain class for a resource link, and is otherwise ambiguous, so the
     * next scope is tried rather than guessing. An explicit namespace confines the choice to that
     * namespace.</p>
     *
     * @param candidates the controllers the link could target
     * @param conventionalName the name that settles a tie within a scope
     * @param namespace the namespace the link names, when {@code explicitNamespace} is set
     * @param explicitNamespace whether the link names a namespace, rather than targeting the request's
     * @return the chosen controller, or {@code null} when no scope settles on one
     */
    private ControllerRef nearestController(Set<ControllerRef> candidates, String conventionalName,
                                            String namespace, boolean explicitNamespace) {
        if (!explicitNamespace && candidates.size() == 1) {
            // Every scope ends at the only candidate, so there is nothing to choose between.
            return candidates.iterator().next()
        }
        String currentNamespace = requestStateLookupStrategy.controllerNamespace
        String targetNamespace = explicitNamespace ? namespace : currentNamespace
        String currentController = requestStateLookupStrategy.controllerName
        if (currentController != null) {
            ControllerRef current = new ControllerRef(currentController, currentNamespace)
            if (Objects.equals(current.namespace, targetNamespace) && candidates.contains(current)) {
                return current
            }
        }
        ControllerRef chosen = chooseWithin(candidates, conventionalName, true, targetNamespace)
        if (chosen != null || explicitNamespace) {
            return chosen
        }
        if (targetNamespace != null) {
            chosen = chooseWithin(candidates, conventionalName, true, null)
            if (chosen != null) {
                return chosen
            }
        }
        return chooseWithin(candidates, conventionalName, false, null)
    }

    /**
     * @return the only candidate in the scope, or failing that the only one in it with the conventional
     *         name, or {@code null}; the scope is the given namespace, or every namespace when
     *         {@code inNamespace} is {@code false}
     */
    private static ControllerRef chooseWithin(Set<ControllerRef> candidates, String conventionalName,
                                              boolean inNamespace, String namespace) {
        ControllerRef only = null
        ControllerRef conventional = null
        int count = 0
        int conventionalCount = 0
        for (ControllerRef candidate in candidates) {
            if (inNamespace && !Objects.equals(candidate.namespace, namespace)) {
                continue
            }
            count++
            only = candidate
            if (Objects.equals(candidate.name, conventionalName)) {
                conventionalCount++
                conventional = candidate
            }
        }
        if (count == 1) {
            return only
        }
        return conventionalCount == 1 ? conventional : null
    }

    /**
     * @return the controllers named after the entity or declaring it that define the given action, or
     *         every such controller when the action is not known
     */
    private Set<ControllerRef> servingControllers(ControllerIndex index, PersistentEntity entity, String derivedName,
                                                  String action) {
        String actionElement = action != null && grailsUrlConverter != null ? grailsUrlConverter.toUrlElement(action) : action
        Set<ControllerRef> serving = new HashSet<>()
        for (Set<ControllerRef> candidates in [index.byName.get(derivedName), index.byDomainClass.get(entity.name)]) {
            if (candidates == null) {
                continue
            }
            for (ControllerRef candidate in candidates) {
                if (action == null || index.defines(candidate, action, actionElement)) {
                    serving.add(candidate)
                }
            }
        }
        return serving
    }

    /**
     * The action a resource link targets, for choosing a controller that handles it: the action it names,
     * or else the one its HTTP method maps to, a link naming neither being followed with a {@code GET}.
     *
     * @return the action, or {@code null} for an HTTP method no action maps to
     */
    private static String resourceAction(String action, Object methodAttribute, Object id) {
        if (truthy(action)) {
            return action
        }
        String method = truthy(methodAttribute) ? methodAttribute.toString().toUpperCase() : HttpMethod.GET.toString()
        if (HttpMethod.GET.name().equals(method) && truthy(id)) {
            method = 'GET_ID'
        }
        return REST_RESOURCE_HTTP_METHOD_TO_ACTION_MAP.get(method)
    }

    /**
     * Walks a controller's supertypes looking for a generic type argument that the mapping context
     * recognises as a persistent entity. Superclasses and interfaces are both walked, so a domain class
     * declared by an intermediate base class or by a Groovy trait is still found, and matching on the
     * mapping context rather than on a known base type keeps this class free of any dependency on the
     * REST controller hierarchy.
     *
     * <p>A supertype that declares more than one persistent entity is ambiguous and is skipped rather
     * than guessed at, so a base class parameterised on both a parent and a child resource does not
     * index the controller under the wrong one.</p>
     */
    private String domainClassNameFor(Class<?> controllerClass, MappingContext context) {
        Deque<ResolvableType> queue = new ArrayDeque<>()
        Set<Class<?>> seen = new HashSet<>()
        queue.add(ResolvableType.forClass(controllerClass))
        while (!queue.isEmpty()) {
            ResolvableType type = queue.poll()
            Class<?> raw = type.resolve()
            if (raw == null || !seen.add(raw)) {
                continue
            }
            String found = singleEntityGeneric(type, context)
            if (found != null) {
                return found
            }
            queue.add(type.superType)
            queue.addAll(type.interfaces)
        }
        return null
    }

    /**
     * @return the name of the only persistent entity among the type's generic arguments, or
     *         {@code null} when there is none or more than one
     */
    private static String singleEntityGeneric(ResolvableType type, MappingContext context) {
        String found = null
        for (ResolvableType generic in type.generics) {
            Class<?> resolved = generic.resolve()
            if (resolved != null && context.getPersistentEntity(resolved.name) != null) {
                if (found != null) {
                    return null
                }
                found = resolved.name
            }
        }
        return found
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
                } catch (Exception e) {
                    log.debug('Unable to parse absolute link attribute', e)
                }
            }
        }
        return absolute
    }

    /**
     * {@inheritDoc }
     */
    String resource(Map attrs) {
        def absolutePath = handleAbsolute(attrs)

        final contextPathAttribute = attrs.contextPath?.toString()
        if (absolutePath == null) {
            final cp = contextPathAttribute == null ? getContextPath() : contextPathAttribute
            if (cp == null) {
                absolutePath = handleAbsolute(absolute: true)
            }
            else {
                absolutePath = cp
            }
        }

        StringBuilder url = new StringBuilder(absolutePath?.toString() ?: '')
        def dir = attrs.dir?.toString()
        if (attrs.plugin) {
            url.append(pluginManager?.getPluginPath(attrs.plugin?.toString()) ?: '')
        }
        else {
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
                url.append(slash)
            }
            url.append(dir)
        }

        def file = attrs.file?.toString()
        if (file) {
            if (!(file.startsWith(slash) || (dir != null && dir.endsWith(slash)))) {
                url.append(slash)
            }
            url.append(file)
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
                u = "http://localhost:${System.getProperty('server.port') ?: '8080'}${contextPath ?: '' }"
            }
        }
        log.trace('Resolved base server URL: {}', u)
        return u
    }

    String getServerBaseURL() {
        return makeServerURL()
    }

    void setPluginManager(GrailsPluginManager pluginManager) {
        this.pluginManager = pluginManager
    }

    /**
     * The registered controllers indexed for resolving links: by logical name, by the domain class each
     * declares, and with the actions each defines, paired with the artefact array and mapping context it
     * was built from.
     *
     * <p>All of it is published together through a single volatile reference. Publishing parts of it as
     * separate fields let a request that raced a controller reload store an index built from the old
     * controllers next to the new array, after which the identity check passed and the stale index was
     * served until the controllers changed again.</p>
     */
    private static final class ControllerIndex {

        static final ControllerIndex EMPTY = new ControllerIndex(null, null, Collections.<String, Set<ControllerRef>>emptyMap(),
                Collections.<String, Set<ControllerRef>>emptyMap(), Collections.<ControllerRef, Set<String>>emptyMap())

        final GrailsClass[] controllers
        final MappingContext mappingContext
        final Map<String, Set<ControllerRef>> byName
        final Map<String, Set<ControllerRef>> byDomainClass
        final Map<ControllerRef, Set<String>> actions

        /**
         * The controller names and domain classes whose ambiguity has been reported against these
         * controllers, so that each is reported once, and again once the controllers change.
         */
        final Set<String> reportedControllerNames = ConcurrentHashMap.newKeySet()
        final Set<String> reportedDomainClasses = ConcurrentHashMap.newKeySet()

        ControllerIndex(GrailsClass[] controllers, MappingContext mappingContext, Map<String, Set<ControllerRef>> byName,
                        Map<String, Set<ControllerRef>> byDomainClass, Map<ControllerRef, Set<String>> actions) {
            this.controllers = controllers
            this.mappingContext = mappingContext
            this.byName = byName
            this.byDomainClass = byDomainClass
            this.actions = actions
        }

        boolean isFor(GrailsClass[] current, MappingContext context) {
            current.is(controllers) && context.is(mappingContext)
        }

        /**
         * @return the controllers with the given logical name, in every namespace
         */
        Set<ControllerRef> named(String name) {
            Set<ControllerRef> refs = byName.get(name)
            refs != null ? refs : Collections.<ControllerRef>emptySet()
        }

        /**
         * @return whether the controller defines the action, given by name or as the URL converter writes
         *         it, since registering a converter renames the actions it records
         */
        boolean defines(ControllerRef controller, String action, String actionElement) {
            Set<String> defined = actions.get(controller)
            defined != null && (defined.contains(action) || defined.contains(actionElement))
        }
    }

    /**
     * A controller as a link addresses it: by logical name and namespace, {@code null} being the
     * default namespace.
     */
    private static final class ControllerRef {

        final String name
        final String namespace

        ControllerRef(String name, String namespace) {
            this.name = name
            this.namespace = namespace
        }

        @Override
        boolean equals(Object other) {
            other instanceof ControllerRef &&
                    Objects.equals(name, ((ControllerRef) other).name) &&
                    Objects.equals(namespace, ((ControllerRef) other).namespace)
        }

        @Override
        int hashCode() {
            31 * Objects.hashCode(name) + Objects.hashCode(namespace)
        }
    }

    /**
     * The controller a resource link resolved to. When the request context chose it, the namespace it
     * was found in is carried as well, so the link stays in that namespace.
     */
    private static final class ResourceTarget {

        final String controller
        final String namespace
        final boolean pinsNamespace

        ResourceTarget(String controller, String namespace, boolean pinsNamespace) {
            this.controller = controller
            this.namespace = namespace
            this.pinsNamespace = pinsNamespace
        }
    }

    /**
     * What a link resolves to from its attributes and the request it is generated in.
     */
    private static final class LinkTarget {

        final String controller
        final String action
        final boolean defaultAction
        final String namespace
        final String httpMethod
        final Object id
        final List<String> parentResources

        LinkTarget(String controller, String action, boolean defaultAction, String namespace, String httpMethod,
                   Object id, List<String> parentResources) {
            this.controller = controller
            this.action = action
            this.defaultAction = defaultAction
            this.namespace = namespace
            this.httpMethod = httpMethod
            this.id = id
            this.parentResources = parentResources
        }
    }
}
