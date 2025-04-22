/*
 * Copyright 2004-2005 the original author or authors.
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Weigher;
import grails.web.mapping.UrlCreator;
import grails.web.mapping.UrlMapping;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

/**
 * Implements caching layer for UrlCreator
 *
 * The "weight" of the cache is the estimated number of characters all cache entries will consume in memory.
 * The estimate is not accurate. It's just used as a hard limit for limiting the cache size.
 *
 * You can tune the maximum weight of the cache by setting "grails.urlcreator.cache.maxsize" in Config.groovy.
 * The default value is 160000 .
 *
 * @author Lari Hotari
 * @since 1.3.5
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class UrlCreatorCache {
    private final Cache<ReverseMappingKey, CachingUrlCreator> cacheMap;

    private enum CachingUrlCreatorWeigher implements Weigher<ReverseMappingKey, CachingUrlCreator> {
        INSTANCE;

        @Override
        public int weigh(ReverseMappingKey key, CachingUrlCreator value) {
            return value.weight() + 1;
        }
    }

    public UrlCreatorCache(int maxSize) {
        cacheMap = Caffeine.newBuilder()
                .maximumWeight(maxSize).weigher(CachingUrlCreatorWeigher.INSTANCE).build();
    }

    public void clear() {
        cacheMap.invalidateAll();
    }

    public ReverseMappingKey createKey(String controller, String action, String namespace, String pluginName, String httpMethod, Map params) {
        return new ReverseMappingKey(controller, action, namespace, pluginName,httpMethod, params);
    }

    public UrlCreator lookup(ReverseMappingKey key) {
        return cacheMap.getIfPresent(key);
    }

    public UrlCreator putAndDecorate(ReverseMappingKey key, UrlCreator delegate) {
        CachingUrlCreator cachingUrlCreator = new CachingUrlCreator(delegate, key.weight() * 2);
        CachingUrlCreator prevCachingUrlCreator = cacheMap.asMap()
                .putIfAbsent(key, cachingUrlCreator);
        if (prevCachingUrlCreator != null) {
            return prevCachingUrlCreator;
        }
        return cachingUrlCreator;
    }

    private class CachingUrlCreator implements UrlCreator {
        private UrlCreator delegate;
        private ConcurrentHashMap<UrlCreatorKey, String> cache = new ConcurrentHashMap<UrlCreatorKey, String>();
        private final int weight;

        public CachingUrlCreator(UrlCreator delegate, int weight) {
            this.delegate = delegate;
            this.weight = weight;
        }

        public int weight() {
            return weight;
        }

        public String createRelativeURL(String controller, String action, Map parameterValues,
                String encoding, String fragment) {
            return createRelativeURL(controller, action, null, null, parameterValues, encoding, fragment);
        }

        @Override
        public String createRelativeURL(String controller, String action,
                String pluginName, Map parameterValues, String encoding) {
            return createRelativeURL(controller, action, null, pluginName, parameterValues, encoding);
        }

        public String createRelativeURL(String controller, String action, String namespace, String pluginName, Map parameterValues,
                String encoding, String fragment) {
            UrlCreatorKey key = new UrlCreatorKey(controller, action, namespace, pluginName, null,parameterValues, encoding, fragment, 0);
            String url = cache.get(key);
            if (url == null) {
                url = delegate.createRelativeURL(controller, action, namespace, pluginName, parameterValues, encoding, fragment);
                cache.put(key, url);
            }
            return url;
        }

        public String createRelativeURL(String controller, String action, Map parameterValues, String encoding) {
            return createRelativeURL(controller, action, null, null, parameterValues, encoding);
        }

        public String createRelativeURL(String controller, String action, String namespace, String pluginName, Map parameterValues, String encoding) {
            UrlCreatorKey key = new UrlCreatorKey(controller, action, namespace, pluginName, null,parameterValues, encoding, null, 0);
            String url = cache.get(key);
            if (url == null) {
                url = delegate.createRelativeURL(controller, action, namespace, pluginName, parameterValues, encoding);
                cache.put(key, url);
            }
            return url;
        }

        public String createURL(String controller, String action, Map parameterValues, String encoding, String fragment) {
            return createURL(controller, action, null, null, parameterValues, encoding, fragment);
        }

        public String createURL(String controller, String action, String pluginName, Map parameterValues, String encoding) {
            return createURL(controller, action, null, pluginName, parameterValues, encoding);
        }

        public String createURL(String controller, String action, String namespace, String pluginName, Map parameterValues, String encoding, String fragment) {
            UrlCreatorKey key = new UrlCreatorKey(controller, action, namespace, pluginName,null, parameterValues, encoding, fragment, 1);
            String url = cache.get(key);
            if (url == null) {
                url = delegate.createURL(controller, action, namespace, pluginName, parameterValues, encoding, fragment);
                cache.put(key, url);
            }
            return url;
        }

        public String createURL(String controller, String action, Map parameterValues, String encoding) {
            return createURL(controller, action, null, null, parameterValues, encoding);
        }

        public String createURL(String controller, String action, String namespace, String pluginName, Map parameterValues, String encoding) {
            UrlCreatorKey key = new UrlCreatorKey(controller, action, namespace, pluginName,null, parameterValues, encoding, null, 1);
            String url = cache.get(key);
            if (url == null) {
                url = delegate.createURL(controller, action, namespace, pluginName, parameterValues, encoding);
                cache.put(key, url);
            }
            return url;
        }

        // don't cache these methods at all

        public String createURL(Map parameterValues, String encoding, String fragment) {
            return delegate.createURL(parameterValues, encoding, fragment);
        }

        public String createURL(Map parameterValues, String encoding) {
            return delegate.createURL(parameterValues, encoding);
        }
    }

    public static class ReverseMappingKey {
        protected final String controller;
        protected final String action;
        protected final String namespace;
        protected final String pluginName;
        protected final String httpMethod;
        protected final String[] paramKeys;
        protected final String[] paramValues;

        public ReverseMappingKey(String controller, String action, String namespace, String pluginName, String httpMethod, Map<Object, Object> params) {
            this.controller = controller;
            this.action = action;
            this.namespace = namespace;
            this.pluginName = pluginName;
            if (httpMethod != null && !UrlMapping.ANY_HTTP_METHOD.equalsIgnoreCase(httpMethod)) {
                this.httpMethod = httpMethod;
            }
            else {
                this.httpMethod = null;
            }
            if (params != null) {
                paramKeys = new String[params.size()];
                paramValues = new String[params.size()];
                int i = 0;
                for (Map.Entry entry : params.entrySet()) {
                    paramKeys[i] = String.valueOf(entry.getKey());
                    String value = null;
                    if (entry.getValue() instanceof CharSequence) {
                        value = String.valueOf(entry.getValue());
                    }
                    else if (entry.getValue() instanceof Collection) {
                        value = DefaultGroovyMethods.join((Iterable) entry.getValue(), ",");
                    }
                    else if (entry.getValue() instanceof Object[]) {
                        value = DefaultGroovyMethods.join((Object[])entry.getValue(), ",");
                    }
                    else {
                        value = String.valueOf(entry.getValue());
                    }
                    paramValues[i] = value;
                    i++;
                }
            }
            else {
                paramKeys = new String[0];
                paramValues = new String[0];
            }
        }

        public int weight() {
            int weight = 0;
            weight += (controller != null) ? controller.length() : 0;
            weight += (action != null) ? action.length() : 0;
            weight += (namespace != null) ? namespace.length() : 0;
            weight += (pluginName != null) ? pluginName.length() : 0;
            for (int i = 0; i < paramKeys.length; i++) {
                weight += (paramKeys[i] != null) ? paramKeys[i].length() : 0;
            }
            for (int i = 0; i < paramValues.length; i++) {
                weight += (paramValues[i] != null) ? paramValues[i].length() : 0;
            }
            return weight;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((action == null) ? 0 : action.hashCode());
            result = prime * result + ((namespace == null) ? 0 : namespace.hashCode());
            result = prime * result + ((pluginName == null) ? 0 : pluginName.hashCode());
            result = prime * result + ((controller == null) ? 0 : controller.hashCode());
            result = prime * result + Arrays.hashCode(paramKeys);
            result = prime * result + Arrays.hashCode(paramValues);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ReverseMappingKey other = (ReverseMappingKey)obj;
            if (action == null) {
                if (other.action != null) {
                    return false;
                }
            }
            else if (!action.equals(other.action)) {
                return false;
            }
            if (controller == null) {
                if (other.controller != null) {
                    return false;
                }
            }
            else if (!controller.equals(other.controller)) {
                return false;
            }
            if (namespace == null) {
                if (other.namespace != null) {
                    return false;
                }
            }
            else if (!namespace.equals(other.namespace)) {
                return false;
            }
            if (pluginName == null) {
                if (other.pluginName != null) {
                    return false;
                }
            }
            else if (!pluginName.equals(other.pluginName)) {
                return false;
            }
            if (httpMethod == null) {
                if (other.httpMethod != null) {
                    return false;
                }
            }
            else if (!httpMethod.equals(other.httpMethod)) {
                return false;
            }

            if (!Arrays.equals(paramKeys, other.paramKeys)) {
                return false;
            }
            if (!Arrays.equals(paramValues, other.paramValues)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "UrlCreatorCache.ReverseMappingKey [action=" + action + ", controller=" + controller + ", namespace=" + namespace + ", plugin=" + pluginName +
                ", paramKeys=" + Arrays.toString(paramKeys) + ", paramValues=" +
                Arrays.toString(paramValues) + "]";
        }
    }

    private static class UrlCreatorKey extends ReverseMappingKey {
        protected final String encoding;
        protected final String fragment;
        protected final int urlType;

        public UrlCreatorKey(String controller, String action, String namespace, String pluginName, String httpMethod, Map<Object, Object> params, String encoding,
                String fragment, int urlType) {
            super(controller, action, namespace, pluginName, httpMethod,params);
            this.encoding = (encoding != null) ? encoding.toLowerCase() : null;
            this.fragment = fragment;
            this.urlType = urlType;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((encoding == null) ? 0 : encoding.hashCode());
            result = prime * result + ((fragment == null) ? 0 : fragment.hashCode());
            result = prime * result + urlType;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!super.equals(obj)) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            UrlCreatorKey other = (UrlCreatorKey)obj;
            if (encoding == null) {
                if (other.encoding != null) {
                    return false;
                }
            }
            else if (!encoding.equals(other.encoding)) {
                return false;
            }
            if (fragment == null) {
                if (other.fragment != null) {
                    return false;
                }
            }
            else if (!fragment.equals(other.fragment)) {
                return false;
            }
            if (urlType != other.urlType) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "UrlCreatorCache.UrlCreatorKey [encoding=" + encoding + ", fragment=" + fragment +
                ", urlType=" + urlType + ", action=" + action + ", controller=" + controller + ", namespace=" + namespace + ", plugin=" + pluginName +
                ", paramKeys=" + Arrays.toString(paramKeys) + ", paramValues=" +
                Arrays.toString(paramValues) + "]";
        }
    }
}
