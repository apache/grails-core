/*
 * Copyright 2011 the original author or authors.
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
package org.grails.web.pages;

import groovy.lang.GroovyObject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.util.Assert;

/**
 * Provides services for resolving URIs.
 *
 * Caches lookups in an internal ConcurrentMap cache.
 *
 * @author Lari Hotari , Sagire Software Oy
 */
public class DefaultGroovyPagesUriService extends GroovyPagesUriSupport {

    ConcurrentMap<TupleStringKey, String> templateURICache = new ConcurrentHashMap<TupleStringKey, String>();
    ConcurrentMap<TupleStringKey, String> deployedViewURICache = new ConcurrentHashMap<TupleStringKey, String>();
    ConcurrentMap<ControllerObjectKey, String> controllerNameCache = new ConcurrentHashMap<ControllerObjectKey, String>();
    ConcurrentMap<TupleStringKey, String> noSuffixViewURICache = new ConcurrentHashMap<TupleStringKey, String>();

    private static class TupleStringKey {
        String keyPart1;
        String keyPart2;

        TupleStringKey(String keyPart1, String keyPart2) {
            this.keyPart1 = keyPart1;
            this.keyPart2 = keyPart2;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            TupleStringKey that = (TupleStringKey) o;

            if (keyPart1 != null ? !keyPart1.equals(that.keyPart1) : that.keyPart1 != null) {
                return false;
            }
            if (keyPart2 != null ? !keyPart2.equals(that.keyPart2) : that.keyPart2 != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = keyPart1 != null ? keyPart1.hashCode() : 0;
            result = 31 * result + (keyPart2 != null ? keyPart2.hashCode() : 0);
            return result;
        }
    }

    private static class ControllerObjectKey {
        private long controllerHashCode;
        private String controllerClassName;

        ControllerObjectKey(GroovyObject controller) {
            controllerHashCode = controller.getClass().hashCode();
            controllerClassName = controller.getClass().getName();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ControllerObjectKey that = (ControllerObjectKey) o;

            if (controllerHashCode != that.controllerHashCode) return false;
            if (!controllerClassName.equals(that.controllerClassName)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = (int) (controllerHashCode ^ (controllerHashCode >>> 32));
            result = 31 * result + controllerClassName.hashCode();
            return result;
        }
    }

    /* (non-Javadoc)
     * @see grails.web.pages.GroovyPagesUriService#getTemplateURI(java.lang.String, java.lang.String)
     */
    @Override
    public String getTemplateURI(String controllerName, String templateName) {
        TupleStringKey key = new TupleStringKey(controllerName, templateName);
        String uri = templateURICache.get(key);
        if (uri == null) {
            uri = super.getTemplateURI(controllerName, templateName);
            String prevuri=templateURICache.putIfAbsent(key, uri);
            if (prevuri != null) {
                return prevuri;
            }
        }
        return uri;
    }

    /* (non-Javadoc)
     * @see grails.web.pages.GroovyPagesUriService#getDeployedViewURI(java.lang.String, java.lang.String)
     */
    @Override
    public String getDeployedViewURI(String controllerName, String viewName) {
        TupleStringKey key = new TupleStringKey(controllerName, viewName);
        String uri = deployedViewURICache.get(key);
        if (uri == null) {
            uri = super.getDeployedViewURI(controllerName, viewName);
            String prevuri = deployedViewURICache.putIfAbsent(key, uri);
            if (prevuri != null) {
                return prevuri;
            }
        }
        return uri;
    }

    @Override
    public String getLogicalControllerName(GroovyObject controller) {
        ControllerObjectKey key = new ControllerObjectKey(controller);
        String name = controllerNameCache.get(key);
        if (name == null) {
            name = super.getLogicalControllerName(controller);
            String prevname = name != null ? controllerNameCache.putIfAbsent(key, name) : null;
            if (prevname != null) {
                return prevname;
            }
        }
        return name;
    }

    /* (non-Javadoc)
     * @see grails.web.pages.GroovyPagesUriService#getNoSuffixViewURI(groovy.lang.GroovyObject, java.lang.String)
     */
    @Override
    public String getNoSuffixViewURI(GroovyObject controller, String viewName) {
        Assert.notNull(controller, "Argument [controller] cannot be null");
        return getNoSuffixViewURI(getLogicalControllerName(controller),viewName);
    }

    /* (non-Javadoc)
     * @see grails.web.pages.GroovyPagesUriService#getNoSuffixViewURI(java.lang.String, java.lang.String)
     */
    @Override
    public String getNoSuffixViewURI(String controllerName, String viewName) {
        TupleStringKey key = new TupleStringKey(controllerName, viewName);
        String uri = noSuffixViewURICache.get(key);
        if (uri == null) {
            uri = super.getNoSuffixViewURI(controllerName, viewName);
            String prevuri = noSuffixViewURICache.putIfAbsent(key, uri);
            if (prevuri != null) {
                return prevuri;
            }
        }
        return uri;
    }

    /* (non-Javadoc)
     * @see grails.web.pages.GroovyPagesUriService#getTemplateURI(groovy.lang.GroovyObject, java.lang.String)
     */
    @Override
    public String getTemplateURI(GroovyObject controller, String templateName) {
        return getTemplateURI(getLogicalControllerName(controller), templateName);
    }

    /* (non-Javadoc)
     * @see grails.web.pages.GroovyPagesUriService#clear()
     */
    @Override
    public void clear() {
        templateURICache.clear();
        deployedViewURICache.clear();
        controllerNameCache.clear();
        noSuffixViewURICache.clear();
    }
}
