/*
 * Copyright 2011 SpringSource
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
package org.grails.web.mapping;

import java.util.LinkedHashMap;
import java.util.Map;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import grails.util.GrailsStringUtils;
import grails.web.mapping.LinkGenerator;
import grails.web.mapping.UrlMapping;
import grails.util.GrailsMetaClassUtils;
import grails.web.servlet.mvc.GrailsParameterMap;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

/**
 * A link generator that uses a LRU cache to cache generated links.
 *
 * @since 2.0
 * @author Graeme Rocher
 */
@SuppressWarnings("rawtypes")
public class CachingLinkGenerator extends DefaultLinkGenerator {

    private static final int MAX_SIZE = 5000;
    public static final String LINK_PREFIX = "link";
    public static final String RESOURCE_PREFIX = "resource";
    public static final String USED_ATTRIBUTES_SUFFIX = "-used-attributes";
    public static final String EMPTY_MAP_STRING = "[:]";
    private static final String OPENING_BRACKET = "[";
    private static final String CLOSING_BRACKET = "]";
    private static final String COMMA_SEPARATOR = ", ";
    private static final String KEY_VALUE_SEPARATOR = ":";
    private static final String THIS_MAP = "(this Map)";

    private Cache<String, Object> linkCache;

    public CachingLinkGenerator(String serverBaseURL, String contextPath) {
        super(serverBaseURL, contextPath);
        this.linkCache = createDefaultCache();
    }

    public CachingLinkGenerator(String serverBaseURL) {
        super(serverBaseURL);
        this.linkCache = createDefaultCache();
    }

    @Override
    public String link(Map attrs, String encoding) {
        if (!isCacheable(attrs)) {
            return super.link(attrs, encoding);
        }

        final String key = makeKey(LINK_PREFIX, attrs);
        Object resourceLink = linkCache.getIfPresent(key);
        if (resourceLink == null) {
            resourceLink = super.link(attrs, encoding);
            linkCache.put(key, resourceLink);
        }
        return resourceLink.toString();
    }

    protected boolean isCacheable(Map attrs) {
        if(attrs.get(LinkGenerator.ATTRIBUTE_PARAMS) instanceof GrailsParameterMap) {
            return false;
        }
        Object urlAttr = attrs.get(ATTRIBUTE_URL);
        if (urlAttr instanceof Map) {
            return isCacheable((Map) urlAttr);
        }

        return attrs.get(UrlMapping.CONTROLLER) != null ||
                attrs.get(UrlMapping.ACTION) != null ||
                urlAttr != null ||
                attrs.get(ATTRIBUTE_URI) != null;
    }

    // Based on DGM toMapString, but with StringBuilder instead of StringBuffer
    protected void appendMapKey(StringBuilder buffer, Map<String, Object> params) {
        if (params==null || params.isEmpty()) {
            buffer.append(EMPTY_MAP_STRING);
            buffer.append(OPENING_BRACKET);
        } else {
            buffer.append(OPENING_BRACKET);
            Map map = new LinkedHashMap<>(params);
            final String requestControllerName = getRequestStateLookupStrategy().getControllerName();
            if (map.get(UrlMapping.ACTION) != null && map.get(UrlMapping.CONTROLLER) == null && map.get(RESOURCE_PREFIX) == null) {
                Object action = map.remove(UrlMapping.ACTION);
                map.put(UrlMapping.CONTROLLER, requestControllerName);
                map.put(UrlMapping.ACTION, action);
            }
            if (map.get(UrlMapping.NAMESPACE) == null && map.get(UrlMapping.CONTROLLER) == requestControllerName) {
                String namespace = getRequestStateLookupStrategy().getControllerNamespace();
                if (GrailsStringUtils.isNotEmpty(namespace)) {
                    map.put(UrlMapping.NAMESPACE, namespace);
                }
            }
            boolean first = true;
            for (Object o : map.entrySet()) {
                Map.Entry entry = (Map.Entry) o;
                Object value = entry.getValue();
                if (value == null) continue;
                first = appendCommaIfNotFirst(buffer, first);
                Object key = entry.getKey();
                if (RESOURCE_PREFIX.equals(key)) {
                    value = getCacheKeyValueForResource(value);
                }
                appendKeyValue(buffer, map, key, value);
            }
        }
        buffer.append(CLOSING_BRACKET);
    }
    
    protected String getCacheKeyValueForResource(Object o) {
        StringBuilder builder = new StringBuilder(o.getClass().getName());
        builder.append("->");
        Object idValue = GrailsMetaClassUtils.invokeMethodIfExists(o, "ident", new Object[0]);
        if(idValue != null) {
            builder.append(idValue.toString());
        } else {
            builder.append(o);
        }
        return builder.toString();
    }

    private boolean appendCommaIfNotFirst(StringBuilder buffer, boolean first) {
        if (first) {
            first = false;
        } else {
            buffer.append(COMMA_SEPARATOR);
        }
        return first;
    }

    protected void appendKeyValue(StringBuilder buffer, Map map, Object key, Object value) {
        buffer.append(key)
              .append(KEY_VALUE_SEPARATOR);
        if (value == map) {
            buffer.append(THIS_MAP);
        } else {
            buffer.append(DefaultGroovyMethods.toString(value));
        }
    }

    @Override
    public String resource(Map attrs) {
        final String key = makeKey(RESOURCE_PREFIX, attrs);
        Object resourceLink = linkCache.getIfPresent(key);
        if (resourceLink == null) {
            resourceLink = super.resource(attrs);
            linkCache.put(key, resourceLink);
        }
        return resourceLink.toString();
    }

    protected String makeKey(String prefix, Map attrs) {
        StringBuilder sb=new StringBuilder();
        sb.append(prefix);
        if (getConfiguredServerBaseURL()==null && isAbsolute(attrs)) {
            if (attrs.get(ATTRIBUTE_BASE) != null) {
                sb.append(attrs.get(ATTRIBUTE_BASE));
            } else {
                GrailsWebRequest webRequest = GrailsWebRequest.lookup();
            if(webRequest != null) {
                    sb.append(webRequest.getBaseUrl());
                }
            }
        }
        appendMapKey(sb, attrs);
        return sb.toString();
    }

    private Cache<String, Object> createDefaultCache() {
        return Caffeine.newBuilder()
                                .maximumSize(MAX_SIZE)
                                .build();
    }

    public void clearCache() {
        linkCache.invalidateAll();
    }
}
