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
package org.apache.grails.web.layout;

import grails.util.Environment;
import grails.util.GrailsNameUtils;
import grails.web.pages.GroovyPagesUriService;
import groovy.lang.GroovyObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;

import grails.util.GrailsClassUtils;
import grails.util.GrailsStringUtils;
import org.grails.core.artefact.ControllerArtefactHandler;
import org.grails.io.support.GrailsResourceUtils;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.util.GrailsApplicationAttributes;
import org.grails.web.servlet.view.AbstractGrailsView;
import org.grails.web.servlet.view.GrailsViewResolver;
import org.grails.web.servlet.view.LayoutViewResolver;
import org.grails.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

import com.opensymphony.module.sitemesh.Decorator;
import com.opensymphony.module.sitemesh.Page;
import com.opensymphony.sitemesh.Content;

/**
 * Provides the logic for GrailsLayoutDecoratorMapper without so many ties to
 * the Sitemesh API.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class GroovyPageLayoutFinder implements ApplicationListener<ContextRefreshedEvent>, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsLayoutDecoratorMapper.class);
    private static final long LAYOUT_CACHE_EXPIRATION_MILLIS = Long.getLong("grails.gsp.reload.interval", 5000);
    private static final String LAYOUTS_PATH = "/layouts";

    private static final int ORDER = Ordered.LOWEST_PRECEDENCE - 1;

    private Map<String, DecoratorCacheValue> decoratorCache = new ConcurrentHashMap<String, DecoratorCacheValue>();
    private Map<LayoutCacheKey, DecoratorCacheValue> layoutDecoratorCache = new ConcurrentHashMap<LayoutCacheKey, DecoratorCacheValue>();

    private String defaultDecoratorName;
    private boolean gspReloadEnabled;
    private boolean cacheEnabled = (Environment.getCurrent() != Environment.DEVELOPMENT);
    private ViewResolver viewResolver;
    private boolean enableNonGspViews = false;

    @Override
    public int getOrder() {
        return ORDER;
    }

    public void setDefaultDecoratorName(String defaultDecoratorName) {
        this.defaultDecoratorName = defaultDecoratorName;
    }

    public void setEnableNonGspViews(boolean enableNonGspViews) {
        this.enableNonGspViews = enableNonGspViews;
    }

    public void setGspReloadEnabled(boolean gspReloadEnabled) {
        this.gspReloadEnabled = gspReloadEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public void setViewResolver(ViewResolver viewResolver) {
        if (viewResolver instanceof LayoutViewResolver) {
            this.viewResolver = ((LayoutViewResolver) viewResolver).getInnerViewResolver();
        } else {
            this.viewResolver = viewResolver;
        }
    }

    public Decorator findLayout(HttpServletRequest request, Content page) {
        return findLayout(request, GSPGrailsLayoutPage.content2htmlPage(page));
    }

    public Decorator findLayout(HttpServletRequest request, Page page) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Evaluating layout for request: {}", request.getRequestURI());
        }
        final Object layoutAttribute = request.getAttribute(WebUtils.LAYOUT_ATTRIBUTE);
        if (request.getAttribute(GroovyPagesUriService.RENDERING_VIEW_ATTRIBUTE) != null || layoutAttribute != null) {
            String layoutName = layoutAttribute == null ? null : layoutAttribute.toString();

            if (layoutName == null) {
                layoutName = page.getProperty("meta.layout");
            }

            Decorator d = null;

            if (GrailsStringUtils.isBlank(layoutName)) {
                GroovyObject controller = (GroovyObject) request.getAttribute(GrailsApplicationAttributes.CONTROLLER);
                if (controller != null) {
                    GrailsWebRequest webRequest = GrailsWebRequest.lookup(request);
                    String controllerName = webRequest.getControllerName();
                    if (controllerName == null) {
                        controllerName = GrailsNameUtils.getLogicalPropertyName(controller.getClass().getName(), ControllerArtefactHandler.TYPE);
                    }
                    String actionUri = webRequest.getAttributes().getControllerActionUri(request);

                    if (controllerName != null && actionUri != null) {

                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Found controller in request, locating layout for controller [{}] and action [{}}]", controllerName, actionUri);
                        }

                        LayoutCacheKey cacheKey = null;
                        boolean cachedIsNull = false;

                        if (cacheEnabled) {
                            cacheKey = new LayoutCacheKey(controllerName, actionUri);
                            DecoratorCacheValue cacheValue = layoutDecoratorCache.get(cacheKey);
                            if (cacheValue != null && (!gspReloadEnabled || !cacheValue.isExpired())) {
                                d = cacheValue.getDecorator();
                                if (d == null) {
                                    cachedIsNull = true;
                                }
                            }
                        }

                        if (d == null && !cachedIsNull) {
                            d = resolveDecorator(request, controller, controllerName, actionUri);
                            if (cacheEnabled) {
                                if (LOG.isDebugEnabled() && d != null) {
                                    LOG.debug("Caching resolved layout {} for controller {} and action {}", d.getPage(), controllerName, actionUri);
                                }
                                layoutDecoratorCache.put(cacheKey, new DecoratorCacheValue(d));
                            }
                        }
                    }
                } else {
                    d = getApplicationDefaultDecorator(request);
                }
            } else {
                d = getNamedDecorator(request, layoutName);
            }

            if (d != null) {
                return d;
            }
        }
        return null;
    }

    protected Decorator getApplicationDefaultDecorator(HttpServletRequest request) {
        return getNamedDecorator(request, defaultDecoratorName == null ? "application" : defaultDecoratorName,
                !enableNonGspViews || defaultDecoratorName == null);
    }

    public Decorator getNamedDecorator(HttpServletRequest request, String name) {
        return getNamedDecorator(request, name, !enableNonGspViews);
    }

    public Decorator getNamedDecorator(HttpServletRequest request, String name, boolean viewMustExist) {
        if (GrailsStringUtils.isBlank(name) || WebUtils.NONE_LAYOUT.equals(name)) {
            return null;
        }

        if (cacheEnabled) {
            DecoratorCacheValue cacheValue = decoratorCache.get(name);
            if (cacheValue != null && (!gspReloadEnabled || !cacheValue.isExpired())) {
                return cacheValue.getDecorator();
            }
        }

        View view;
        try {
            view = viewResolver.resolveViewName(GrailsResourceUtils.cleanPath(GrailsResourceUtils.appendPiecesForUri(LAYOUTS_PATH, name)),
                    request.getLocale());
            // it's only possible to check that GroovyPageView exists
            if (viewMustExist && !(view instanceof AbstractGrailsView)) {
                view = null;
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to resolve view", e);
        }

        Decorator d = null;
        if (view != null) {
            d = createDecorator(name, view);
        }

        if (cacheEnabled) {
            decoratorCache.put(name, new DecoratorCacheValue(d));
        }
        return d;
    }

    private Decorator resolveDecorator(HttpServletRequest request, GroovyObject controller, String controllerName,
                                       String actionUri) {
        Decorator d = null;

        Object layoutProperty = GrailsClassUtils.getStaticPropertyValue(controller.getClass(), "layout");
        if (layoutProperty instanceof CharSequence) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("layout property found in controller, looking for template named {}", layoutProperty);
            }
            d = getNamedDecorator(request, layoutProperty.toString());
        } else {
            if (!GrailsStringUtils.isBlank(actionUri)) {
                d = getNamedDecorator(request, actionUri.substring(1), true);
            }

            if (d == null && !GrailsStringUtils.isBlank(controllerName)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Action layout not found, trying controller");
                }
                d = getNamedDecorator(request, controllerName, true);
            }

            if (d == null) {
                d = getApplicationDefaultDecorator(request);
            }
        }

        return d;
    }

    private Decorator createDecorator(String decoratorName, View view) {
        return new SpringMVCViewDecorator(decoratorName, view);
    }

    private static class LayoutCacheKey {
        private String controllerName;
        private String actionUri;

        public LayoutCacheKey(String controllerName, String actionUri) {
            this.controllerName = controllerName;
            this.actionUri = actionUri;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LayoutCacheKey that = (LayoutCacheKey) o;

            if (!actionUri.equals(that.actionUri)) return false;
            if (!controllerName.equals(that.controllerName)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = controllerName.hashCode();
            result = 31 * result + actionUri.hashCode();
            return result;
        }
    }

    private static class DecoratorCacheValue {
        Decorator decorator;
        long createTimestamp = System.currentTimeMillis();

        public DecoratorCacheValue(Decorator decorator) {
            this.decorator = decorator;
        }

        public Decorator getDecorator() {
            return decorator;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createTimestamp > LAYOUT_CACHE_EXPIRATION_MILLIS;
        }
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!(viewResolver instanceof GrailsViewResolver)) {
            setViewResolver(event.getApplicationContext().getBean(GrailsViewResolver.class));
        }

    }
}