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

import grails.core.GrailsApplication;
import grails.gorm.validation.Constrained;
import grails.gorm.validation.ConstrainedProperty;
import grails.web.mapping.UrlMapping;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import jakarta.servlet.ServletContext;

/**
 * Abstract UrlMapping implementation that provides common basic functionality.
 *
 * @author Graeme Rocher
 * @since 0.5.5
 */
public abstract class AbstractUrlMapping implements UrlMapping {

    protected final ConstrainedProperty[] constraints;
    protected Object controllerName;
    protected Object actionName;
    protected Object namespace;
    protected Object pluginName;
    protected Object viewName;
    protected Object forwardURI;
    protected Object redirectInfo;
    protected ServletContext servletContext;
    protected GrailsApplication grailsApplication;
    protected Map<String,Object> parameterValues = Collections.emptyMap();
    protected boolean parseRequest;
    protected String mappingName;
    protected String httpMethod = ANY_HTTP_METHOD;
    protected String version = ANY_VERSION;
    protected Integer pluginIndex;

    /**
     * Base constructor required to construct a UrlMapping instance
     *
     * @param controllerName The name of the controller
     * @param actionName The name of the action
     * @param constraints Any constraints that apply to the mapping
     * @param grailsApplication The GrailsApplication instance
     */
    public AbstractUrlMapping(Object redirectInfo, Object controllerName, Object actionName, Object namespace, Object pluginName, Object viewName, ConstrainedProperty[] constraints, GrailsApplication grailsApplication) {
        this.controllerName = controllerName;
        this.actionName = actionName;
        this.namespace = namespace;
        this.pluginName = pluginName;
        this.constraints = constraints;
        this.viewName = viewName;
        setGrailsApplication(grailsApplication);
        this.redirectInfo = redirectInfo;
    }

    private void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
        if(grailsApplication != null) {

            final ApplicationContext applicationContext = grailsApplication.getMainContext();
            if(applicationContext instanceof WebApplicationContext) {
                this.servletContext = ((WebApplicationContext)applicationContext).getServletContext();
            }
        }
    }

    protected AbstractUrlMapping(Object viewName, ConstrainedProperty[] constraints, GrailsApplication grailsApplication) {
        this.viewName = viewName;
        this.constraints = constraints;
        this.grailsApplication = grailsApplication;
        setGrailsApplication(grailsApplication);
    }

    protected AbstractUrlMapping(URI uri, ConstrainedProperty[] constraints, GrailsApplication grailsApplication) {
        this.forwardURI = uri;
        this.constraints = constraints;
        this.grailsApplication = grailsApplication;
        setGrailsApplication(grailsApplication);
    }

    @Override
    public String getHttpMethod() {
        return httpMethod;
    }

    @Override
    public String getVersion() {
        return version;
    }

    /**
     * @see UrlMapping#getConstraints()
     */
    public Constrained[] getConstraints() {
        return constraints;
    }

    /**
     * @see UrlMapping#getControllerName()
     */
    public Object getControllerName() {
        return controllerName;
    }

    /**
     * @see grails.web.mapping.UrlMapping#getActionName()
     */
    public Object getActionName() {
        return actionName;
    }

    public Object getPluginName() {
        return pluginName;
    }

    public Object getNamespace() {
        return namespace;
    }

    /**
     * @see grails.web.mapping.UrlMapping#getViewName()
     */
    public Object getViewName() {
        return viewName;
    }

    public void setParameterValues(Map<String,Object> parameterValues) {
        this.parameterValues = Collections.unmodifiableMap(parameterValues);
    }

    public void setParseRequest(boolean shouldParse) {
        this.parseRequest = shouldParse;
    }

    public String getMappingName() {
        return mappingName;
    }

    public void setMappingName(String name) {
        mappingName = name;
    }

    public boolean hasRuntimeVariable(String name) {
        if (constraints != null) {
            for (int i = 0; i < constraints.length; i++) {
                ConstrainedProperty cp = constraints[i];
                if (cp.getPropertyName().equals(name)) return true;
            }
        }
        return false;
    }

    public Object getRedirectInfo() {
        return redirectInfo;
    }


    public void setPluginIndex(int pluginIndex) {
        this.pluginIndex = pluginIndex;
    }

    public Integer getPluginIndex() {
        return this.pluginIndex;
    }

    public boolean isDefinedInPlugin() {
        return pluginIndex != null;
    }
}
