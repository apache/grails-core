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

import groovy.transform.CompileStatic
import org.springframework.util.Assert

import grails.core.GrailsApplication
import grails.gorm.validation.ConstrainedProperty
import grails.web.mapping.UrlMappingData
import grails.web.mapping.UrlMappingInfo

/**
 * A Url mapping for http response codes.
 *
 * @author mike
 * @since 1.0-RC1
 */
@SuppressWarnings('rawtypes')
@CompileStatic
class ResponseCodeUrlMapping extends AbstractUrlMapping {

    private final ResponseCodeMappingData urlData
    private final ConstrainedProperty[] constraints = new ConstrainedProperty[0]
    private Class<?> exceptionType

    ResponseCodeUrlMapping(UrlMappingData urlData, Object controllerName, Object actionName, Object namespace, Object pluginName, Object viewName, ConstrainedProperty[] constraints, GrailsApplication grailsApplication) {
        super(null, controllerName, actionName, namespace, pluginName, viewName, constraints, grailsApplication)
        this.urlData = (ResponseCodeMappingData) urlData

        Assert.isTrue(constraints == null || constraints.length == 0,
                "Constraints can't be used for response code url mapping")
    }

    ResponseCodeUrlMapping(UrlMappingData urlData, URI uri, ConstrainedProperty[] constraints, GrailsApplication grailsApplication) {
        super(uri, constraints, grailsApplication)
        this.urlData = (ResponseCodeMappingData) urlData

        Assert.isTrue(constraints == null || constraints.length == 0,
            "Constraints can't be used for response code url mapping")
    }

    UrlMappingInfo match(String uri) {
        return null
    }

    UrlMappingData getUrlData() {
        return urlData
    }

    @Override
    ConstrainedProperty[] getConstraints() {
        return constraints
    }

    int compareTo(Object o) {
        return 0
    }

    String createURL(Map values, String encoding) {
        throw new UnsupportedOperationException('Method createURL not implemented in ' + getClass())
    }

    String createURL(Map values, String encoding, String fragment) {
        throw new UnsupportedOperationException('Method createURL not implemented in ' + getClass())
    }

    String createURL(String controller, String action, Map values, String encoding) {
        throw new UnsupportedOperationException('Method createURL not implemented in ' + getClass())
    }

    String createURL(String controller, String action, String pluginName, Map values, String encoding) {
        throw new UnsupportedOperationException('Method createURL not implemented in ' + getClass())
    }

    String createURL(String controller, String action, String namespace, String pluginName, Map values, String encoding) {
        throw new UnsupportedOperationException('Method createURL not implemented in ' + getClass())
    }

    String createRelativeURL(String controller, String action, Map values, String encoding) {
        throw new UnsupportedOperationException('Method createRelativeURL not implemented in ' + getClass())
    }

    String createRelativeURL(String controller, String action, String pluginName, Map values, String encoding) {
        throw new UnsupportedOperationException('Method createRelativeURL not implemented in ' + getClass())
    }

    String createRelativeURL(String controller, String action, String namespace, String pluginName, Map values, String encoding) {
        throw new UnsupportedOperationException('Method createRelativeURL not implemented in ' + getClass())
    }

    String createRelativeURL(String controller, String action, Map values, String encoding, String fragment) {
        throw new UnsupportedOperationException('Method createRelativeURL not implemented in ' + getClass())
    }

    String createRelativeURL(String controller, String action, String namespace, String pluginName, Map values, String encoding, String fragment) {
        throw new UnsupportedOperationException('Method createRelativeURL not implemented in ' + getClass())
    }

    String createURL(String controller, String action, Map values, String encoding, String fragment) {
        throw new UnsupportedOperationException('Method createURL not implemented in ' + getClass())
    }

    String createURL(String controller, String action, String namespace, String pluginName, Map values, String encoding, String fragment) {
        throw new UnsupportedOperationException('Method createURL not implemented in ' + getClass())
    }

    UrlMappingInfo match(int responseCode) {
        if (responseCode == urlData.getResponseCode()) {
            return new DefaultUrlMappingInfo(null, controllerName, actionName, namespace, pluginName, viewName,
                    parameterValues, urlData, grailsApplication)
        }
        return null
    }

    void setExceptionType(Class<?> exClass) {
        this.exceptionType = exClass
    }

    Class<?> getExceptionType() {
        return exceptionType
    }

}
