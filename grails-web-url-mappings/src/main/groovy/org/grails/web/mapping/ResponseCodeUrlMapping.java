/*
 * Copyright 2004-2024 the original author or authors.
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

import grails.core.GrailsApplication;
import grails.gorm.validation.ConstrainedProperty;
import grails.web.mapping.UrlMappingData;
import grails.web.mapping.UrlMappingInfo;
import java.net.URI;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.Map;

/**
 * A Url mapping for http response codes.
 *
 * @author mike
 * @since 1.0-RC1
 */
@SuppressWarnings("rawtypes")
public class ResponseCodeUrlMapping extends AbstractUrlMapping {

    private final ResponseCodeMappingData urlData;
    private final ConstrainedProperty[] constraints = new ConstrainedProperty[0];
    private Class<?> exceptionType;

    public ResponseCodeUrlMapping(UrlMappingData urlData, Object controllerName, Object actionName, Object namespace, Object pluginName, Object viewName, ConstrainedProperty[] constraints, GrailsApplication grailsApplication) {
        super(null, controllerName, actionName, namespace, pluginName, viewName, constraints, grailsApplication);
        this.urlData = (ResponseCodeMappingData) urlData;

        Assert.isTrue(constraints == null || constraints.length == 0,
                "Constraints can't be used for response code url mapping");
    }

    public ResponseCodeUrlMapping(UrlMappingData urlData, URI uri, ConstrainedProperty[] constraints, GrailsApplication grailsApplication) {
        super(uri, constraints, grailsApplication);
        this.urlData = (ResponseCodeMappingData) urlData;

        Assert.isTrue(constraints == null || constraints.length == 0,
            "Constraints can't be used for response code url mapping");
    }

    public UrlMappingInfo match(String uri) {
        return null;
    }

    public UrlMappingData getUrlData() {
        return urlData;
    }

    @Override
    public ConstrainedProperty[] getConstraints() {
        return constraints;
    }

    public int compareTo(Object o) {
        return 0;
    }

    public String createURL(Map values, String encoding) {
        throw new UnsupportedOperationException("Method createURL not implemented in " + getClass());
    }

    public String createURL(Map values, String encoding, String fragment) {
        throw new UnsupportedOperationException("Method createURL not implemented in " + getClass());
    }

    public String createURL(String controller, String action, Map values, String encoding) {
        throw new UnsupportedOperationException("Method createURL not implemented in " + getClass());
    }

    public String createURL(String controller, String action, String pluginName, Map values, String encoding) {
        throw new UnsupportedOperationException("Method createURL not implemented in " + getClass());
    }

    public String createURL(String controller, String action, String namespace, String pluginName, Map values, String encoding) {
        throw new UnsupportedOperationException("Method createURL not implemented in " + getClass());
    }

    public String createRelativeURL(String controller, String action, Map values, String encoding) {
        throw new UnsupportedOperationException("Method createRelativeURL not implemented in " + getClass());
    }

    public String createRelativeURL(String controller, String action, String pluginName, Map values, String encoding) {
        throw new UnsupportedOperationException("Method createRelativeURL not implemented in " + getClass());
    }

    public String createRelativeURL(String controller, String action, String namespace, String pluginName, Map values, String encoding) {
        throw new UnsupportedOperationException("Method createRelativeURL not implemented in " + getClass());
    }

    public String createRelativeURL(String controller, String action, Map values, String encoding, String fragment) {
        throw new UnsupportedOperationException("Method createRelativeURL not implemented in " + getClass());
    }

    public String createRelativeURL(String controller, String action, String namespace, String pluginName, Map values, String encoding, String fragment) {
        throw new UnsupportedOperationException("Method createRelativeURL not implemented in " + getClass());
    }

    public String createURL(String controller, String action, Map values, String encoding, String fragment) {
        throw new UnsupportedOperationException("Method createURL not implemented in " + getClass());
    }

    public String createURL(String controller, String action, String namespace, String pluginName, Map values, String encoding, String fragment) {
        throw new UnsupportedOperationException("Method createURL not implemented in " + getClass());
    }

    public UrlMappingInfo match(int responseCode) {
        if (responseCode == urlData.getResponseCode()) {
            return new DefaultUrlMappingInfo(null, controllerName, actionName, namespace, pluginName, viewName,
                    parameterValues, urlData, grailsApplication);
        }
        return null;
    }

    public void setExceptionType(Class<?> exClass) {
        this.exceptionType = exClass;
    }

    public Class<?> getExceptionType() {
        return exceptionType;
    }
}
