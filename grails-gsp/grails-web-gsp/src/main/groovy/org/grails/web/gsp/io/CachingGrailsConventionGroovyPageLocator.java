/*
 * Copyright 2011 SpringSource
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
package org.grails.web.gsp.io;

import grails.util.CacheEntry;
import org.grails.gsp.GroovyPageBinding;
import org.grails.gsp.io.GroovyPageCompiledScriptSource;
import org.grails.gsp.io.GroovyPageResourceScriptSource;
import org.grails.gsp.io.GroovyPageScriptSource;
import org.grails.taglib.TemplateVariableBinding;
import org.springframework.core.io.ByteArrayResource;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Extends GrailsConventionGroovyPageLocator adding caching of the located GrailsPageScriptSource.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class CachingGrailsConventionGroovyPageLocator extends GrailsConventionGroovyPageLocator {

    private static final GroovyPageResourceScriptSource NULL_SCRIPT = new GroovyPageResourceScriptSource("/null",new ByteArrayResource("".getBytes()));
    private ConcurrentMap<GroovyPageLocatorCacheKey, CacheEntry<GroovyPageScriptSource>> uriResolveCache = new ConcurrentHashMap<GroovyPageLocatorCacheKey, CacheEntry<GroovyPageScriptSource>>();
    private long cacheTimeout = -1;

    @Override
    public GroovyPageScriptSource findPageInBinding(final String uri, final TemplateVariableBinding binding) {
        if (uri == null) return null;

        Callable<GroovyPageScriptSource> updater = new Callable<GroovyPageScriptSource>() {
            public GroovyPageScriptSource call() {
                GroovyPageScriptSource scriptSource = CachingGrailsConventionGroovyPageLocator.super.findPageInBinding(uri, binding);
                if (scriptSource == null) {
                    scriptSource = NULL_SCRIPT;
                }
                return scriptSource;
            }
        };

        return lookupCache(GroovyPageLocatorCacheKey.build(uri, null, binding), updater);
    }

    @Override
    public GroovyPageScriptSource findPageInBinding(final String pluginName, final String uri, final TemplateVariableBinding binding) {
        if (uri == null || pluginName == null) return null;

        Callable<GroovyPageScriptSource> updater = new Callable<GroovyPageScriptSource>() {
            public GroovyPageScriptSource call() {
                GroovyPageScriptSource scriptSource = CachingGrailsConventionGroovyPageLocator.super.findPageInBinding(pluginName, uri, binding);
                if (scriptSource == null) {
                    scriptSource = NULL_SCRIPT;
                }
                return scriptSource;
            }
        };

        return lookupCache(GroovyPageLocatorCacheKey.build(uri, pluginName, binding), updater);
    }

    @Override
    public GroovyPageScriptSource findPage(final String uri) {
       if (uri == null) return null;

       Callable<GroovyPageScriptSource> updater = new Callable<GroovyPageScriptSource>() {
           public GroovyPageScriptSource call() {
               GroovyPageScriptSource scriptSource = CachingGrailsConventionGroovyPageLocator.super.findPage(uri);
               if (scriptSource == null) {
                   scriptSource = NULL_SCRIPT;
               }
               return scriptSource;
           }
       };

       return lookupCache(GroovyPageLocatorCacheKey.build(uri, null, null), updater);
    }

    @SuppressWarnings("rawtypes")
    protected GroovyPageScriptSource lookupCache(final GroovyPageLocatorCacheKey cacheKey, Callable<GroovyPageScriptSource> updater) {
        GroovyPageScriptSource scriptSource = null;
        if (cacheTimeout == 0) {
            try {
                scriptSource = updater.call();
            }
            catch (Exception e) {
                throw new CacheEntry.UpdateException(e);
            }
        } else {
            scriptSource = CacheEntry.getValue(uriResolveCache, cacheKey, cacheTimeout, updater, new Callable<CacheEntry>() {
                @Override
                public CacheEntry call() throws Exception {
                    return new CustomCacheEntry();
                }
            }, true, null);
        }
        return scriptSource == NULL_SCRIPT ? null : scriptSource;
    }

    public long getCacheTimeout() {
        return cacheTimeout;
    }

    public void setCacheTimeout(long cacheTimeout) {
        this.cacheTimeout = cacheTimeout;
    }

    private static final class GroovyPageLocatorCacheKey {
        private final String uri;
        private final String pluginName;
        private final String contextPath;

        private GroovyPageLocatorCacheKey(String uri, String pluginName, String contextPath) {
            this.uri = uri;
            this.pluginName = pluginName;
            this.contextPath = contextPath;
        }

        public static final GroovyPageLocatorCacheKey build(final String uri, final String pluginName, final TemplateVariableBinding binding) {
            String pluginNameInCacheKey = (pluginName == null) ? ( binding instanceof GroovyPageBinding && ((GroovyPageBinding)binding).getPagePlugin() != null ? ((GroovyPageBinding)binding).getPagePlugin().getName() : null) : pluginName;
            return new GroovyPageLocatorCacheKey(uri, pluginNameInCacheKey, binding instanceof GroovyPageBinding ? ((GroovyPageBinding)binding).getPluginContextPath() : null);
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            GroovyPageLocatorCacheKey that = (GroovyPageLocatorCacheKey) o;

            if (contextPath != null ? !contextPath.equals(that.contextPath) : that.contextPath != null) {
                return false;
            }
            if (pluginName != null ? !pluginName.equals(that.pluginName) : that.pluginName != null) {
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
            result = 31 * result + (pluginName != null ? pluginName.hashCode() : 0);
            result = 31 * result + (contextPath != null ? contextPath.hashCode() : 0);
            return result;
        }
    }

    @Override
    public void removePrecompiledPage(GroovyPageCompiledScriptSource scriptSource) {
        super.removePrecompiledPage(scriptSource);
        // remove the entry from uriResolveCache
        for (Map.Entry<GroovyPageLocatorCacheKey, CacheEntry<GroovyPageScriptSource>> entry : new HashSet<Map.Entry<GroovyPageLocatorCacheKey, CacheEntry<GroovyPageScriptSource>>>(uriResolveCache.entrySet())) {
            GroovyPageScriptSource ss=entry.getValue().getValue();
            if (ss == scriptSource || (ss instanceof GroovyPageCompiledScriptSource && scriptSource.getURI().equals(((GroovyPageCompiledScriptSource)ss).getURI()))) {
                uriResolveCache.remove(entry.getKey());
            }
        }
    }

    static class CustomCacheEntry<T> extends CacheEntry<T> {
        public CustomCacheEntry() {
            super();
        }

        @Override
        protected boolean shouldUpdate(long beforeLockingCreatedMillis, Object cacheRequestObject) {
            // Never expire GroovyPageCompiledScriptSource entry in cache
            if (getValue() instanceof GroovyPageCompiledScriptSource) {
                resetTimestamp(true);
                return false;
            } else {
                return super.shouldUpdate(beforeLockingCreatedMillis, cacheRequestObject);
            }
        }
    }
}
