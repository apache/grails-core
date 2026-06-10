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
package org.grails.plugins.sitemesh3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import groovy.lang.GroovyObject;

import jakarta.servlet.http.HttpServletRequest;

import org.sitemesh.DecoratorSelector;
import org.sitemesh.SiteMeshContext;
import org.sitemesh.content.Content;
import org.sitemesh.content.ContentProperty;
import org.sitemesh.webapp.WebAppContext;

import grails.util.Environment;
import grails.util.GrailsClassUtils;
import grails.util.GrailsNameUtils;
import grails.util.GrailsStringUtils;
import org.grails.core.artefact.ControllerArtefactHandler;
import org.grails.gsp.io.GroovyPageScriptSource;
import org.grails.io.support.GrailsResourceUtils;
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.util.GrailsApplicationAttributes;
import org.grails.web.util.WebUtils;

/**
 * {@link DecoratorSelector} that resolves layout paths using Grails
 * conventions. The resolution order is:
 *
 * <ol>
 *   <li>Request attribute {@link WebUtils#LAYOUT_ATTRIBUTE}</li>
 *   <li>Content {@code meta.layout} property</li>
 *   <li>Controller's {@code static layout = 'x'} property</li>
 *   <li>{@code /layouts/<controllerName>/<actionUri>.gsp}</li>
 *   <li>{@code /layouts/<controllerName>.gsp}</li>
 *   <li>Configured default (e.g. {@code grails.sitemesh.default.layout})</li>
 * </ol>
 *
 * <p>Results are cached by (controllerName, actionUri) outside of the
 * DEVELOPMENT environment. Both caches are unbounded in entry count,
 * but are naturally bounded by the number of distinct (controllerName,
 * actionUri) pairs in the application — typically a small, fixed set.</p>
 */
public class Sitemesh3LayoutFinder implements DecoratorSelector<SiteMeshContext> {

    private static final String LAYOUTS_PATH = "/layouts";

    private final GrailsConventionGroovyPageLocator groovyPageLocator;

    private String defaultDecoratorName;
    private boolean gspReloadEnabled;
    private boolean cacheEnabled = (Environment.getCurrent() != Environment.DEVELOPMENT);
    // Configurable via grails.sitemesh.layout.cache.interval (milliseconds).
    // Defaults to 5000 ms. Only consulted when gspReloadEnabled is true.
    private long layoutCacheExpirationMillis = 5000L;

    private final Map<String, LayoutCacheValue> namedDecoratorCache = new ConcurrentHashMap<>();
    private final Map<LayoutCacheKey, LayoutCacheValue> layoutDecoratorCache = new ConcurrentHashMap<>();

    public Sitemesh3LayoutFinder(GrailsConventionGroovyPageLocator groovyPageLocator) {
        this.groovyPageLocator = groovyPageLocator;
    }

    public void setDefaultDecoratorName(String defaultDecoratorName) {
        this.defaultDecoratorName = defaultDecoratorName;
    }

    public void setGspReloadEnabled(boolean gspReloadEnabled) {
        this.gspReloadEnabled = gspReloadEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public void setLayoutCacheExpirationMillis(long layoutCacheExpirationMillis) {
        this.layoutCacheExpirationMillis = layoutCacheExpirationMillis;
    }

    @Override
    public String[] selectDecoratorPaths(Content content, SiteMeshContext context) {
        if (!(context instanceof WebAppContext)) {
            return toArray(resolveByName(defaultDecoratorName));
        }
        HttpServletRequest request = ((WebAppContext) context).getRequest();

        Object layoutAttribute = request.getAttribute(WebUtils.LAYOUT_ATTRIBUTE);
        String layoutName = layoutAttribute == null ? null : layoutAttribute.toString();

        if (GrailsStringUtils.isBlank(layoutName) && content != null) {
            layoutName = extractMetaLayout(content);
        }

        if (!GrailsStringUtils.isBlank(layoutName)) {
            return resolveMany(layoutName);
        }

        GroovyObject controller = (GroovyObject) request.getAttribute(GrailsApplicationAttributes.CONTROLLER);
        if (controller == null) {
            return toArray(resolveByName(defaultDecoratorName));
        }

        GrailsWebRequest webRequest = GrailsWebRequest.lookup(request);
        String controllerName = webRequest != null ? webRequest.getControllerName() : null;
        if (controllerName == null) {
            controllerName = GrailsNameUtils.getLogicalPropertyName(controller.getClass().getName(), ControllerArtefactHandler.TYPE);
        }
        String actionUri = webRequest != null ? webRequest.getAttributes().getControllerActionUri(request) : null;

        if (controllerName == null || actionUri == null) {
            return toArray(resolveByName(defaultDecoratorName));
        }

        LayoutCacheKey cacheKey = null;
        if (cacheEnabled) {
            cacheKey = new LayoutCacheKey(controllerName, actionUri);
            LayoutCacheValue cached = layoutDecoratorCache.get(cacheKey);
            if (cached != null && (!gspReloadEnabled || !cached.isExpired(layoutCacheExpirationMillis))) {
                return toArray(cached.path);
            }
        }

        String resolved = resolveByConvention(controller, controllerName, actionUri);
        if (cacheEnabled) {
            layoutDecoratorCache.put(cacheKey, new LayoutCacheValue(resolved));
        }
        return toArray(resolved);
    }

    private String extractMetaLayout(Content content) {
        ContentProperty props = content.getExtractedProperties();
        if (props == null) {
            return null;
        }
        if (!props.hasChild("meta") || !props.getChild("meta").hasChild("layout")) {
            return null;
        }
        return props.getChild("meta").getChild("layout").getValue();
    }

    private String resolveByConvention(GroovyObject controller, String controllerName, String actionUri) {
        Object layoutProperty = GrailsClassUtils.getStaticPropertyValue(controller.getClass(), "layout");
        if (layoutProperty instanceof CharSequence) {
            return resolveByName(layoutProperty.toString());
        }
        if (!GrailsStringUtils.isBlank(actionUri)) {
            String actionLayout = actionUri.startsWith("/") ? actionUri.substring(1) : actionUri;
            String resolved = resolveByName(actionLayout);
            if (resolved != null) {
                return resolved;
            }
        }
        String resolved = resolveByName(controllerName);
        if (resolved != null) {
            return resolved;
        }
        return resolveByName(defaultDecoratorName);
    }

    private String resolveByName(String name) {
        if (GrailsStringUtils.isBlank(name) || WebUtils.NONE_LAYOUT.equals(name)) {
            return null;
        }

        LayoutCacheValue cached = namedDecoratorCache.get(name);
        if (cacheEnabled && cached != null && (!gspReloadEnabled || !cached.isExpired(layoutCacheExpirationMillis))) {
            return cached.path;
        }

        String path = GrailsResourceUtils.cleanPath(GrailsResourceUtils.appendPiecesForUri(LAYOUTS_PATH, name));
        GroovyPageScriptSource view = groovyPageLocator != null ? groovyPageLocator.findViewByPath(path) : null;
        String resolved = view != null ? path : null;

        if (cacheEnabled) {
            namedDecoratorCache.put(name, new LayoutCacheValue(resolved));
        }
        return resolved;
    }

    private static String[] toArray(String value) {
        return value == null ? new String[0] : new String[] {value};
    }

    // Handles comma-separated decorator names from meta/attribute sources
    // (e.g. <meta name="layout" content="a, b"/>), resolving each to a layout
    // path. Matches SiteMesh 3's convention of applying multiple decorators
    // in order.
    private String[] resolveMany(String layoutName) {
        if (layoutName.indexOf(',') < 0) {
            return toArray(resolveByName(layoutName));
        }
        List<String> resolved = new ArrayList<>();
        for (String part : layoutName.split(",")) {
            String trimmed = part.trim();
            if (GrailsStringUtils.isBlank(trimmed)) {
                continue;
            }
            String path = resolveByName(trimmed);
            if (path != null) {
                resolved.add(path);
            }
        }
        return resolved.toArray(new String[0]);
    }

    private static final class LayoutCacheKey {
        final String controllerName;
        final String actionUri;

        LayoutCacheKey(String controllerName, String actionUri) {
            this.controllerName = controllerName;
            this.actionUri = actionUri;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LayoutCacheKey)) return false;
            LayoutCacheKey that = (LayoutCacheKey) o;
            return controllerName.equals(that.controllerName) && actionUri.equals(that.actionUri);
        }

        @Override
        public int hashCode() {
            return 31 * controllerName.hashCode() + actionUri.hashCode();
        }
    }

    private static final class LayoutCacheValue {
        final String path;
        final long createdAt = System.currentTimeMillis();

        LayoutCacheValue(String path) {
            this.path = path;
        }

        boolean isExpired(long expirationMillis) {
            return System.currentTimeMillis() - createdAt > expirationMillis;
        }
    }
}
