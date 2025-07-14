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
package org.grails.web.mapping;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Weigher;
import grails.core.GrailsControllerClass;
import grails.gorm.validation.Constrained;
import grails.gorm.validation.ConstrainedProperty;
import grails.util.CollectionUtils;
import grails.util.Holders;
import grails.web.mapping.UrlCreator;
import grails.web.mapping.UrlMapping;
import grails.web.mapping.UrlMappingEvaluator;
import grails.web.mapping.UrlMappingInfo;
import grails.web.mapping.UrlMappings;
import groovy.lang.Closure;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.style.ToStringCreator;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.context.WebApplicationContext;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default implementation of the grails.web.mapping.UrlMappingsHolder interface that takes a list of mappings and
 * then sorts them according to their precedence rules as defined in the implementation of Comparable.
 *
 * @author Graeme Rocher
 * @see grails.web.mapping.UrlMapping
 * @see Comparable
 * @since 0.4
 */
@SuppressWarnings("rawtypes")
public class DefaultUrlMappingsHolder implements UrlMappings {

    public static final UrlMappingInfo[] EMPTY_RESULTS = new UrlMappingInfo[0];
    private static final Log LOG = LogFactory.getLog(DefaultUrlMappingsHolder.class);
    private static final int DEFAULT_MAX_WEIGHTED_CAPACITY = 5000;

    private int maxWeightedCacheCapacity = DEFAULT_MAX_WEIGHTED_CAPACITY;
    private Cache<String, UrlMappingInfo> cachedMatches;
    private Cache<UriToUrlMappingKey, List<UrlMappingInfo>> cachedListMatches;

    private enum CustomListWeigher implements Weigher<UriToUrlMappingKey, List<UrlMappingInfo>> {
        INSTANCE;

        @Override
        public int weigh(UriToUrlMappingKey key, List<UrlMappingInfo> value) {
            return value.size() + 1;
        }
    }

    private List<UrlMapping> urlMappings = new ArrayList<>();
    private UrlMapping[] mappings;
    private UrlCreatorCache urlCreatorCache;
    // capacity of the UrlCreatoreCache is the estimated number of char's stored in cached objects
    private int urlCreatorMaxWeightedCacheCapacity = 160000;
    private final List excludePatterns;
    private final Map<UrlMappingKey, UrlMapping> mappingsLookup = new HashMap<>();
    private final Map<String, UrlMapping> namedMappings = new HashMap<>();
    private final UrlMappingsList mappingsListLookup = new UrlMappingsList();
    private final Set<String> DEFAULT_NAMESPACE_PARAMS = CollectionUtils.newSet(UrlMapping.NAMESPACE, UrlMapping.CONTROLLER, UrlMapping.ACTION);
    private final Set<String> DEFAULT_CONTROLLER_PARAMS = CollectionUtils.newSet(UrlMapping.CONTROLLER, UrlMapping.ACTION);
    private final Set<String> DEFAULT_CONTROLLER_ONLY_PARAMS = CollectionUtils.newSet(UrlMapping.CONTROLLER);
    private final Set<String> DEFAULT_ACTION_PARAMS = CollectionUtils.newSet(UrlMapping.ACTION);
    private final PathMatcher pathMatcher = new AntPathMatcher();
    private final AtomicInteger initCounter = new AtomicInteger();

    public DefaultUrlMappingsHolder(List<UrlMapping> mappings) {
        this(mappings, null, false);
    }

    public DefaultUrlMappingsHolder(List<UrlMapping> mappings, List excludePatterns) {
        this(mappings, excludePatterns, false);
    }

    public DefaultUrlMappingsHolder(List<UrlMapping> mappings, List excludePatterns, boolean doNotCallInit) {
        urlMappings = mappings;
        this.excludePatterns = excludePatterns;
        if (!doNotCallInit) {
            initialize();
        }
    }

    @Override
    public Collection<UrlMapping> addMappings(Closure mappings) {
        WebApplicationContext applicationContext = (WebApplicationContext) Holders.findApplicationContext();

        UrlMappingEvaluator evaluator = new DefaultUrlMappingEvaluator(applicationContext);

        List<UrlMapping> newMappings = evaluator.evaluateMappings(mappings);
        this.urlMappings.addAll(newMappings);
        initialize();
        return newMappings;
    }

    public void initialize() {
        sortMappings();

        cachedMatches = Caffeine.newBuilder()
                .maximumSize(maxWeightedCacheCapacity)
                .build();
        cachedListMatches = Caffeine.newBuilder()
                .maximumWeight(maxWeightedCacheCapacity)
                .weigher(CustomListWeigher.INSTANCE)
                .build();
        if (urlCreatorMaxWeightedCacheCapacity > 0) {
            urlCreatorCache = new UrlCreatorCache(urlCreatorMaxWeightedCacheCapacity);
        }

        mappings = urlMappings.toArray(new UrlMapping[urlMappings.size()]);

        for (UrlMapping mapping : mappings) {
            String mappingName = mapping.getMappingName();
            if (mappingName != null) {
                namedMappings.put(mappingName, mapping);
            }
            String controllerName = mapping.getControllerName() instanceof String ? mapping.getControllerName().toString() : null;
            String actionName = mapping.getActionName() instanceof String ? mapping.getActionName().toString() : null;
            String pluginName = mapping.getPluginName() instanceof String ? mapping.getPluginName().toString() : null;
            String httpMethod = mapping.getHttpMethod();
            String version = mapping.getVersion();
            String namespace = mapping.getNamespace() instanceof String ? mapping.getNamespace().toString() : null;

            Constrained[] params = mapping.getConstraints();
            Set<String> requiredParams = new HashSet<String>();
            int optionalIndex = -1;
            for (int j = 0; j < params.length; j++) {
                Constrained param = params[j];
                if (param instanceof ConstrainedProperty) {
                    if (!param.isNullable()) {
                        requiredParams.add(((ConstrainedProperty) param).getPropertyName());
                    } else {
                        optionalIndex = j;
                        break;
                    }
                }
            }
            UrlMappingKey key = new UrlMappingKey(controllerName, actionName, namespace, pluginName, httpMethod, version, requiredParams);
            mappingsLookup.put(key, mapping);

            UrlMappingsListKey listKey = new UrlMappingsListKey(controllerName, actionName, namespace, pluginName, httpMethod, version);
            mappingsListLookup.put(listKey, key);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Reverse mapping: " + key + " -> " + mapping);
            }
            Set<String> requiredParamsAndOptionals = new HashSet<String>(requiredParams);
            if (optionalIndex > -1) {
                for (int j = optionalIndex; j < params.length; j++) {
                    Constrained constrained = params[j];
                    if (constrained instanceof ConstrainedProperty) {

                        ConstrainedProperty param = (ConstrainedProperty) constrained;
                        requiredParamsAndOptionals.add(param.getPropertyName());
                        key = new UrlMappingKey(controllerName, actionName, namespace, pluginName, httpMethod, version, new HashSet<>(requiredParamsAndOptionals));
                        mappingsLookup.put(key, mapping);

                        listKey = new UrlMappingsListKey(controllerName, actionName, namespace, pluginName, httpMethod, version);
                        mappingsListLookup.put(listKey, key);

                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Reverse mapping: " + key + " -> " + mapping);
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void sortMappings() {
        List<ResponseCodeUrlMapping> responseCodeUrlMappings = new ArrayList<ResponseCodeUrlMapping>();
        Iterator<UrlMapping> iter = urlMappings.iterator();
        while (iter.hasNext()) {
            UrlMapping mapping = iter.next();
            if (mapping instanceof ResponseCodeUrlMapping) {
                responseCodeUrlMappings.add((ResponseCodeUrlMapping) mapping);
                iter.remove();
            }
        }
        if (initCounter.getAndIncrement() == 0) {
            Collections.reverse(responseCodeUrlMappings);
        }

        Collections.sort(urlMappings);
        Collections.reverse(urlMappings);
        urlMappings.addAll(0, responseCodeUrlMappings);
    }

    public UrlMapping[] getUrlMappings() {
        return mappings;
    }

    public List getExcludePatterns() {
        return excludePatterns;
    }

    public UrlCreator getReverseMapping(final String controller, final String action, Map params) {
        return getReverseMapping(controller, action, null, null, params);
    }

    @Override
    public UrlCreator getReverseMapping(String controller, String action, String pluginName, Map params) {
        return getReverseMapping(controller, action, null, pluginName, null, params);
    }

    @Override
    public UrlCreator getReverseMapping(String controller, String action, String namespace, String pluginName, String httpMethod, Map params) {
        return getReverseMapping(controller, action, namespace, pluginName, httpMethod, UrlMapping.ANY_VERSION, params);
    }

    @Override
    public UrlCreator getReverseMapping(String controller, String action, String namespace, String pluginName, String httpMethod, String version, Map params) {
        if (params == null) {
            params = Collections.emptyMap();
        }

        if (urlCreatorCache != null) {
            UrlCreatorCache.ReverseMappingKey key = urlCreatorCache.createKey(controller, action, namespace, pluginName, httpMethod, params);
            UrlCreator creator = urlCreatorCache.lookup(key);
            if (creator == null) {
                creator = resolveUrlCreator(controller, action, namespace, pluginName, httpMethod, version, params, true);
                creator = urlCreatorCache.putAndDecorate(key, creator);
            }
            // preserve previous side-effect, remove mappingName from params
            params.remove("mappingName");
            return creator;
        }
        // cache is disabled
        return resolveUrlCreator(controller, action, namespace, pluginName, httpMethod, version, params, true);
    }

    /**
     * @see grails.web.mapping.UrlMappingsHolder#getReverseMapping(String, String, java.util.Map)
     */
    public UrlCreator getReverseMapping(final String controller, final String action, final String namespace, final String pluginName, Map params) {
        return getReverseMapping(controller, action, namespace, pluginName, null, params);
    }

    public UrlCreator getReverseMappingNoDefault(String controller, String action, Map params) {
        return getReverseMappingNoDefault(controller, action, null, null, null, params);
    }

    @Override
    public UrlCreator getReverseMappingNoDefault(String controller, String action, String namespace, String pluginName, String httpMethod, Map params) {
        return getReverseMappingNoDefault(controller, action, namespace, pluginName, httpMethod, UrlMapping.ANY_VERSION, params);
    }

    @Override
    public UrlCreator getReverseMappingNoDefault(String controller, String action, String namespace, String pluginName, String httpMethod, String version, Map params) {
        if (params == null) {
            params = Collections.emptyMap();
        }

        if (urlCreatorCache != null) {
            UrlCreatorCache.ReverseMappingKey key = urlCreatorCache.createKey(controller, action, namespace, pluginName, httpMethod, params);
            UrlCreator creator = urlCreatorCache.lookup(key);
            if (creator == null) {
                creator = resolveUrlCreator(controller, action, namespace, pluginName, httpMethod, version, params, false);
                if (creator != null) {
                    creator = urlCreatorCache.putAndDecorate(key, creator);
                }
            }
            // preserve previous side-effect, remove mappingName from params
            params.remove("mappingName");
            return creator;
        }
        // cache is disabled
        return resolveUrlCreator(controller, action, namespace, pluginName, httpMethod, version, params, true);
    }

    @SuppressWarnings("unchecked")
    private UrlCreator resolveUrlCreator(final String controller,
                                         final String action,
                                         final String namespace,
                                         final String pluginName,
                                         String httpMethod,
                                         String version,
                                         Map params,
                                         boolean useDefault) {
        UrlMapping mapping = null;

        if (httpMethod == null) {
            httpMethod = UrlMapping.ANY_HTTP_METHOD;
        }
        mapping = namedMappings.get(params.remove("mappingName"));
        if (mapping == null) {
            mapping = lookupMapping(controller, action, namespace, pluginName, httpMethod, version, params);
            if (mapping == null) {
                lookupMapping(controller, action, namespace, pluginName, UrlMapping.ANY_HTTP_METHOD, version, params);
            }
        }
        if (mapping == null || (mapping instanceof ResponseCodeUrlMapping)) {
            UrlMappingKey lookupKey = new UrlMappingKey(controller, action, namespace, pluginName, httpMethod, version, Collections.<String>emptySet());
            mapping = mappingsLookup.get(lookupKey);
            if (mapping == null) {
                lookupKey.httpMethod = UrlMapping.ANY_HTTP_METHOD;
                mapping = mappingsLookup.get(lookupKey);
            }
        }
        if (mapping == null || (mapping instanceof ResponseCodeUrlMapping)) {
            Set<String> lookupParams = new HashSet<String>(DEFAULT_ACTION_PARAMS);
            Set<String> paramKeys = new HashSet<String>(params.keySet());
            paramKeys.removeAll(lookupParams);
            lookupParams.addAll(paramKeys);

            UrlMappingKey lookupKey = new UrlMappingKey(controller, null, namespace, pluginName, httpMethod, version, lookupParams);
            mapping = mappingsLookup.get(lookupKey);
            if (mapping == null) {
                lookupKey.httpMethod = UrlMapping.ANY_HTTP_METHOD;
                mapping = mappingsLookup.get(lookupKey);
            }
            if (mapping == null) {
                lookupParams.removeAll(paramKeys);
                UrlMappingKey lookupKeyModifiedParams = new UrlMappingKey(controller, null, namespace, pluginName, httpMethod, version, lookupParams);
                mapping = mappingsLookup.get(lookupKeyModifiedParams);
                if (mapping == null) {
                    lookupKeyModifiedParams.httpMethod = UrlMapping.ANY_HTTP_METHOD;
                    mapping = mappingsLookup.get(lookupKeyModifiedParams);
                }
            }
        }

        if (mapping == null || (mapping instanceof ResponseCodeUrlMapping)) {
            Set<String> lookupParams = new HashSet<String>(DEFAULT_CONTROLLER_ONLY_PARAMS);
            Set<String> paramKeys = new HashSet<String>(params.keySet());
            paramKeys.removeAll(lookupParams);
            lookupParams.addAll(paramKeys);

            UrlMappingKey lookupKey = new UrlMappingKey(null, action, namespace, pluginName, httpMethod, version, lookupParams);
            mapping = mappingsLookup.get(lookupKey);
            if (mapping == null) {
                lookupKey.httpMethod = UrlMapping.ANY_HTTP_METHOD;
                mapping = mappingsLookup.get(lookupKey);
            }
            if (mapping == null) {
                lookupParams.removeAll(paramKeys);
                UrlMappingKey lookupKeyModifiedParams = new UrlMappingKey(null, action, namespace, pluginName, httpMethod, version, lookupParams);
                mapping = mappingsLookup.get(lookupKeyModifiedParams);
                if (mapping == null) {
                    lookupKeyModifiedParams.httpMethod = UrlMapping.ANY_HTTP_METHOD;
                    mapping = mappingsLookup.get(lookupKeyModifiedParams);
                }
            }
        }

        if (mapping == null || (mapping instanceof ResponseCodeUrlMapping)) {
            Set<String> lookupParams = new HashSet<String>(DEFAULT_CONTROLLER_PARAMS);
            Set<String> paramKeys = new HashSet<String>(params.keySet());
            paramKeys.removeAll(lookupParams);
            lookupParams.addAll(paramKeys);

            UrlMappingKey lookupKey = new UrlMappingKey(null, null, namespace, pluginName, httpMethod, version, lookupParams);
            mapping = mappingsLookup.get(lookupKey);
            if (mapping == null) {
                lookupKey.httpMethod = UrlMapping.ANY_HTTP_METHOD;
                mapping = mappingsLookup.get(lookupKey);
            }
            if (mapping == null) {
                lookupParams.removeAll(paramKeys);
                UrlMappingKey lookupKeyModifiedParams = new UrlMappingKey(null, null, namespace, pluginName, httpMethod, version, lookupParams);
                mapping = mappingsLookup.get(lookupKeyModifiedParams);
                if (mapping == null) {
                    lookupKeyModifiedParams.httpMethod = UrlMapping.ANY_HTTP_METHOD;
                    mapping = mappingsLookup.get(lookupKeyModifiedParams);
                }
            }
        }
        if (mapping == null || (mapping instanceof ResponseCodeUrlMapping)) {
            Set<String> lookupParams = new HashSet<>(DEFAULT_NAMESPACE_PARAMS);
            Set<String> paramKeys = new HashSet<String>(params.keySet());
            paramKeys.removeAll(lookupParams);
            lookupParams.addAll(paramKeys);

            UrlMappingKey lookupKey = new UrlMappingKey(null, null, null, pluginName, httpMethod, version, lookupParams);
            mapping = mappingsLookup.get(lookupKey);
            if (mapping == null) {
                lookupKey.httpMethod = UrlMapping.ANY_HTTP_METHOD;
                mapping = mappingsLookup.get(lookupKey);
            }
            if (mapping == null) {
                lookupParams.removeAll(paramKeys);
                UrlMappingKey lookupKeyModifiedParams = new UrlMappingKey(null, null, null, pluginName, httpMethod, version, lookupParams);
                mapping = mappingsLookup.get(lookupKeyModifiedParams);
                if (mapping == null) {
                    lookupKeyModifiedParams.httpMethod = UrlMapping.ANY_HTTP_METHOD;
                    mapping = mappingsLookup.get(lookupKeyModifiedParams);
                }
            }
        }

        if (mapping == null || (mapping instanceof ResponseCodeUrlMapping)) {
            Set<String> lookupParams = new HashSet<String>();
            UrlMappingKey lookupKey = new UrlMappingKey(controller, null, namespace, pluginName, httpMethod, version, lookupParams);
            mapping = mappingsLookup.get(lookupKey);
            if (mapping == null) {
                lookupKey.httpMethod = UrlMapping.ANY_HTTP_METHOD;
                mapping = mappingsLookup.get(lookupKey);
            }
        }

        UrlCreator creator = null;
        if (mapping == null || (mapping instanceof ResponseCodeUrlMapping)) {
            if (useDefault) {
                creator = new DefaultUrlCreator(controller, action);
            }
        } else {
            creator = mapping;
        }
        return creator;
    }

    /**
     * Performs a match uses reverse mappings to looks up a mapping from the
     * controller, action and params. This is refactored to use a list of mappings
     * identified by only controller and action and then matches the mapping to select
     * the mapping that best matches the params (most possible matches).
     *
     * @param controller The controller name
     * @param action     The action name
     * @param httpMethod The HTTP method
     * @param version
     * @param params     The params  @return A UrlMapping instance or null
     */
    @SuppressWarnings("unchecked")
    protected UrlMapping lookupMapping(String controller, String action, String namespace, String pluginName, String httpMethod, String version, Map params) {
        final UrlMappingsListKey lookupKey = new UrlMappingsListKey(controller, action, namespace, pluginName, httpMethod, version);
        Collection mappingKeysSet = mappingsListLookup.get(lookupKey);

        final String actionName = lookupKey.action;
        boolean secondAttempt = false;
        final boolean isIndexAction = GrailsControllerClass.INDEX_ACTION.equals(actionName);
        if (null == mappingKeysSet) {
            lookupKey.httpMethod = UrlMapping.ANY_HTTP_METHOD;
            mappingKeysSet = mappingsListLookup.get(lookupKey);
        }
        if (null == mappingKeysSet && actionName != null) {
            lookupKey.action = null;

            mappingKeysSet = mappingsListLookup.get(lookupKey);
            secondAttempt = true;
        }
        if (null == mappingKeysSet) {
            return null;
        }

        Set<String> lookupParams = new HashSet<String>(params.keySet());
        if (secondAttempt) {
            lookupParams.removeAll(DEFAULT_ACTION_PARAMS);
            lookupParams.addAll(DEFAULT_ACTION_PARAMS);
        }

        UrlMappingKey[] mappingKeys = (UrlMappingKey[]) mappingKeysSet.toArray(new UrlMappingKey[mappingKeysSet.size()]);
        for (int i = mappingKeys.length; i > 0; i--) {
            UrlMappingKey mappingKey = mappingKeys[i - 1];
            if (lookupParams.containsAll(mappingKey.paramNames)) {
                final UrlMapping mapping = mappingsLookup.get(mappingKey);
                if (canInferAction(actionName, secondAttempt, isIndexAction, mapping)) {
                    return mapping;
                }
                if (!secondAttempt) {
                    return mapping;
                }
            }
        }
        return null;
    }

    private boolean canInferAction(String actionName, boolean secondAttempt, boolean indexAction, UrlMapping mapping) {
        return secondAttempt && (indexAction || mapping.hasRuntimeVariable(GrailsControllerClass.ACTION));
    }

    /**
     * @see grails.web.mapping.UrlMappingsHolder#match(String)
     */
    public UrlMappingInfo match(String uri) {
        UrlMappingInfo info = cachedMatches.getIfPresent(uri);
        if (info != null) {
            return info;
        }

        for (UrlMapping mapping : mappings) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Attempting to match URI [" + uri + "] with pattern [" + mapping.getUrlData().getUrlPattern() + "]");
            }

            info = mapping.match(uri);
            if (info != null) {
                cachedMatches.put(uri, info);
                break;
            }
        }

        return info;
    }

    public UrlMappingInfo[] matchAll(String uri) {
        return matchAll(uri, (String) null);
    }

    public UrlMappingInfo[] matchAll(String uri, String httpMethod) {
        if (isExcluded(uri)) {
            return EMPTY_RESULTS;
        }

        boolean anyHttpMethod = httpMethod != null && httpMethod.equalsIgnoreCase(UrlMapping.ANY_HTTP_METHOD);
        UriToUrlMappingKey cacheKey = new UriToUrlMappingKey(uri, httpMethod, UrlMapping.ANY_VERSION);
        List<UrlMappingInfo> matchingUrls = cachedListMatches.getIfPresent(cacheKey);
        if (matchingUrls == null) {
            matchingUrls = new ArrayList<>();
            for (UrlMapping mapping : mappings) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Attempting to match URI [" + uri + "] with pattern [" + mapping.getUrlData().getUrlPattern() + "]");
                }

                UrlMappingInfo current = mapping.match(uri);
                if (current != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Matched URI [" + uri + "] with pattern [" + mapping.getUrlData().getUrlPattern() + "], adding to possibilities");
                    }

                    String mappingHttpMethod = current.getHttpMethod();
                    if (mappingHttpMethod == null || anyHttpMethod || mappingHttpMethod.equalsIgnoreCase(UrlMapping.ANY_HTTP_METHOD) || mappingHttpMethod.equalsIgnoreCase(httpMethod)) {
                        matchingUrls.add(current);
                    }
                }
            }
            cachedListMatches.put(cacheKey, matchingUrls);
        }
        return matchingUrls.toArray(new UrlMappingInfo[0]);
    }

    private boolean isExcluded(String uri) {
        if (excludePatterns != null) {
            for (Object excludePattern : excludePatterns) {
                if (pathMatcher.match(excludePattern.toString(), uri)) {
                    return true;
                }
            }
        }
        return false;
    }

    public UrlMappingInfo[] matchAll(String uri, String httpMethod, String version) {
        if (isExcluded(uri)) {
            return EMPTY_RESULTS;
        }

        UriToUrlMappingKey cacheKey = new UriToUrlMappingKey(uri, httpMethod, version);
        List<UrlMappingInfo> matchingUrls = cachedListMatches.getIfPresent(cacheKey);
        if (matchingUrls == null) {
            matchingUrls = new ArrayList<>();
            boolean anyHttpMethod = httpMethod != null && httpMethod.equals(UrlMapping.ANY_HTTP_METHOD);
            boolean anyVersion = version != null && version.equals(UrlMapping.ANY_VERSION);
            for (UrlMapping mapping : mappings) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Attempting to match URI [" + uri + "] with pattern [" + mapping.getUrlData().getUrlPattern() + "]");
                }

                UrlMappingInfo current = mapping.match(uri);
                if (current != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Matched URI [" + uri + "] with pattern [" + mapping.getUrlData().getUrlPattern() + "], adding to possibilities");
                    }

                    String mappingHttpMethod = current.getHttpMethod();
                    String mappingVersion = current.getVersion();
                    boolean isValidHttpMethod = mappingHttpMethod == null || anyHttpMethod || mappingHttpMethod.equalsIgnoreCase(UrlMapping.ANY_HTTP_METHOD) || mappingHttpMethod.equalsIgnoreCase(httpMethod);
                    boolean isValidVersion = mappingVersion == null || anyVersion || mappingVersion.equals(UrlMapping.ANY_VERSION) || mappingVersion.equals(version);
                    if (isValidHttpMethod && isValidVersion) {
                        matchingUrls.add(current);
                    }
                }
            }
            cachedListMatches.put(cacheKey, matchingUrls);
        }
        return matchingUrls.toArray(new UrlMappingInfo[matchingUrls.size()]);
    }

    @Override
    public UrlMappingInfo[] matchAll(String uri, HttpMethod httpMethod) {
        return matchAll(uri, httpMethod.toString(), UrlMapping.ANY_VERSION);
    }

    @Override
    public UrlMappingInfo[] matchAll(String uri, HttpMethod httpMethod, String version) {
        return matchAll(uri, httpMethod.toString(), version);
    }

    public UrlMappingInfo matchStatusCode(int responseCode) {
        for (UrlMapping mapping : mappings) {
            if (mapping instanceof ResponseCodeUrlMapping) {
                ResponseCodeUrlMapping responseCodeUrlMapping = (ResponseCodeUrlMapping) mapping;
                if (responseCodeUrlMapping.getExceptionType() != null) {
                    continue;
                }
                final UrlMappingInfo current = responseCodeUrlMapping.match(responseCode);
                if (current != null) {
                    return current;
                }
            }
        }

        return null;
    }

    @Override
    public Set<HttpMethod> allowedMethods(String uri) {
        UrlMappingInfo[] urlMappingInfos = matchAll(uri, UrlMapping.ANY_HTTP_METHOD);
        Set<HttpMethod> methods = new HashSet<>();

        for (UrlMappingInfo urlMappingInfo : urlMappingInfos) {
            if (urlMappingInfo.getHttpMethod() == null || urlMappingInfo.getHttpMethod().equals(UrlMapping.ANY_HTTP_METHOD)) {
                methods.addAll(Arrays.asList(HttpMethod.values()));
                break;
            } else {
                HttpMethod method = HttpMethod.valueOf(urlMappingInfo.getHttpMethod().toUpperCase());
                methods.add(method);
            }
        }

        return Collections.unmodifiableSet(methods);
    }

    public UrlMappingInfo matchStatusCode(int responseCode, Throwable e) {
        for (UrlMapping mapping : mappings) {
            if (mapping instanceof ResponseCodeUrlMapping) {
                ResponseCodeUrlMapping responseCodeUrlMapping = (ResponseCodeUrlMapping) mapping;
                final UrlMappingInfo current = responseCodeUrlMapping.match(responseCode);
                if (current != null) {
                    if (responseCodeUrlMapping.getExceptionType() != null &&
                            responseCodeUrlMapping.getExceptionType().isInstance(e)) {
                        return current;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("URL Mappings");
        pw.println("------------");
        for (UrlMapping mapping : mappings) {
            pw.println(mapping);
        }
        pw.flush();
        return sw.toString();
    }

    public void setMaxWeightedCacheCapacity(int maxWeightedCacheCapacity) {
        this.maxWeightedCacheCapacity = maxWeightedCacheCapacity;
    }

    public void setUrlCreatorMaxWeightedCacheCapacity(int urlCreatorMaxWeightedCacheCapacity) {
        this.urlCreatorMaxWeightedCacheCapacity = urlCreatorMaxWeightedCacheCapacity;
    }

    class UriToUrlMappingKey {

        String uri;
        String httpMethod;
        String version;

        UriToUrlMappingKey(String uri, String httpMethod, String version) {
            this.uri = uri;
            this.httpMethod = httpMethod;
            this.version = version;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            UriToUrlMappingKey that = (UriToUrlMappingKey) o;

            if (httpMethod != null ? !httpMethod.equals(that.httpMethod) : that.httpMethod != null) {
                return false;
            }
            if (version != null ? !version.equals(that.version) : that.version != null) {
                return false;
            }
            if (uri != null ? !uri.equals(that.uri) : that.uri != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = uri != null ? uri.hashCode() : 0;
            result = 31 * result + (httpMethod != null ? httpMethod.hashCode() : 0);
            result = 31 * result + (version != null ? version.hashCode() : 0);
            return result;
        }
    }

    /**
     * A class used as a key to lookup a UrlMapping based on controller, action and parameter names
     */
    @SuppressWarnings("unchecked")
    class UrlMappingKey implements Comparable {

        String controller;
        String action;
        String namespace;
        String pluginName;
        String httpMethod;
        String version;
        Set<String> paramNames;

        public UrlMappingKey(String controller, String action, String namespace, String pluginName, String httpMethod, String version, Set<String> paramNames) {
            this.controller = controller;
            this.action = action;
            this.namespace = namespace;
            this.pluginName = pluginName;
            if (httpMethod != null) {
                this.httpMethod = httpMethod;
            }
            if (version != null) {
                this.version = version;
            }
            this.paramNames = paramNames;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            UrlMappingKey that = (UrlMappingKey) o;

            if (action != null && !action.equals(that.action)) {
                return false;
            }
            if (controller != null && !controller.equals(that.controller)) {
                return false;
            }
            if (namespace != null && !namespace.equals(that.namespace)) {
                return false;
            }
            if (pluginName != null && !pluginName.equals(that.pluginName)) {
                return false;
            }
            if (httpMethod != null && !httpMethod.equals(that.httpMethod)) {
                return false;
            }
            if (version != null && !version.equals(that.version)) {
                return false;
            }
            if (!paramNames.equals(that.paramNames)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result;
            result = (controller != null ? controller.hashCode() : 0);
            result = 31 * result + (action != null ? action.hashCode() : 0);
            result = 31 * result + (namespace != null ? namespace.hashCode() : 0);
            result = 31 * result + (pluginName != null ? pluginName.hashCode() : 0);
            result = 31 * result + (httpMethod != null ? httpMethod.hashCode() : 0);
            result = 31 * result + (version != null ? version.hashCode() : 0);
            result = 31 * result + paramNames.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return new ToStringCreator(this).append(UrlMapping.CONTROLLER, controller)
                    .append(UrlMapping.ACTION, action)
                    .append(UrlMapping.NAMESPACE, namespace)
                    .append(UrlMapping.PLUGIN, pluginName)
                    .append("httpMethod", httpMethod)
                    .append("params", paramNames)
                    .toString();
        }

        public int compareTo(Object o) {
            final int BEFORE = -1;
            final int EQUAL = 0;
            final int AFTER = 1;

            //this optimization is usually worthwhile, and can always be added
            if (this == o) {
                return EQUAL;
            }

            final UrlMappingKey other = (UrlMappingKey) o;

            if (paramNames.size() < other.paramNames.size()) {
                return BEFORE;
            }
            if (paramNames.size() > other.paramNames.size()) {
                return AFTER;
            }

            int comparison = controller != null ? controller.compareTo(other.controller) : EQUAL;
            if (comparison != EQUAL) {
                return comparison;
            }

            comparison = action != null ? action.compareTo(other.action) : EQUAL;
            if (comparison != EQUAL) {
                return comparison;
            }

            comparison = namespace != null ? namespace.compareTo(other.namespace) : EQUAL;
            if (comparison != EQUAL) {
                return comparison;
            }

            comparison = pluginName != null ? pluginName.compareTo(other.pluginName) : EQUAL;
            if (comparison != EQUAL) {
                return comparison;
            }

            comparison = httpMethod != null ? httpMethod.compareTo(other.httpMethod) : EQUAL;
            if (comparison != EQUAL) {
                return comparison;
            }

            return EQUAL;
        }
    }

    /**
     * A class used as a key to lookup a all UrlMappings based on only controller and action.
     */
    class UrlMappingsListKey {

        String controller;
        String action;
        String namespace;
        String pluginName;
        String httpMethod;
        String version;

        public UrlMappingsListKey(String controller, String action, String namespace, String pluginName, String httpMethod, String version) {
            this.controller = controller;
            this.action = action;
            this.namespace = namespace;
            this.pluginName = pluginName;
            if (httpMethod != null) {
                this.httpMethod = httpMethod;
            }
            if (version != null) {
                this.version = version;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            UrlMappingsListKey that = (UrlMappingsListKey) o;

            if (action != null && !action.equals(that.action)) {
                return false;
            }
            if (controller != null && !controller.equals(that.controller)) {
                return false;
            }
            if (namespace != null && !namespace.equals(that.namespace)) {
                return false;
            }
            if (pluginName != null && !pluginName.equals(that.pluginName)) {
                return false;
            }
            if (httpMethod != null && !httpMethod.equals(that.httpMethod)) {
                return false;
            }
            if (version != null && !version.equals(that.version)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result;
            result = (controller != null ? controller.hashCode() : 0);
            result = 31 * result + (action != null ? action.hashCode() : 0);
            result = 31 * result + (namespace != null ? namespace.hashCode() : 0);
            result = 31 * result + (pluginName != null ? pluginName.hashCode() : 0);
            result = 31 * result + (httpMethod != null ? httpMethod.hashCode() : 0);
            result = 31 * result + (version != null ? version.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return new ToStringCreator(this).append(UrlMapping.CONTROLLER, controller)
                    .append(UrlMapping.ACTION, action)
                    .append(UrlMapping.NAMESPACE, namespace)
                    .append(UrlMapping.PLUGIN, pluginName)
                    .append(UrlMapping.HTTP_METHOD, httpMethod)
                    .append(UrlMapping.VERSION, version)
                    .toString();
        }
    }

    class UrlMappingsList {

        // A map from a UrlMappingsListKey to a list of UrlMappingKeys
        private Map<UrlMappingsListKey, List<UrlMappingKey>> lookup =
                new HashMap<UrlMappingsListKey, List<UrlMappingKey>>();

        public void put(UrlMappingsListKey key, UrlMappingKey mapping) {
            List<UrlMappingKey> mappingsList = lookup.get(key);
            if (null == mappingsList) {
                mappingsList = new ArrayList<UrlMappingKey>();
                lookup.put(key, mappingsList);
            }
            if (!mappingsList.contains(mapping)) {

                mappingsList.add(mapping);
                Collections.sort(mappingsList);
            }
        }

        public Collection<UrlMappingKey> get(UrlMappingsListKey key) {
            return lookup.get(key);
        }
    }
}
