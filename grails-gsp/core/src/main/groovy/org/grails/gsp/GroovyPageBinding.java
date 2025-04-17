/*
 * Copyright 2004-2005 the original author or authors.
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
package org.grails.gsp;

import grails.plugins.GrailsPlugin;
import groovy.lang.Binding;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.grails.taglib.TemplateVariableBinding;

import java.util.Map;

/**
 * Script Binding that is used in GSP evaluation.
 *
 * @author Lari Hotari
 */
public class GroovyPageBinding extends TemplateVariableBinding {
    private static final Log log = LogFactory.getLog(GroovyPageBinding.class);

    public GroovyPageBinding() {
        super();
    }

    public GroovyPageBinding(Binding parent) {
        super(parent);
    }

    public GroovyPageBinding(String pluginContextPath) {
        setPluginContextPath(pluginContextPath);
    }

    @SuppressWarnings("rawtypes")
    public GroovyPageBinding(Map variables) {
        super(variables);
    }

    public GroovyPageBinding(String[] args) {
        super(args);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object getVariable(String name) {
        Object val = getVariablesMap().get(name);
        if (val == null && !getVariablesMap().containsKey(name)) {
            if (GroovyPage.PAGE_SCOPE.equals(name)) return this;
            return super.getVariable(name);
        }
        return val;
    }

    protected boolean shouldUseChildBinding(TemplateVariableBinding childBinding) {
        return isRoot() || hasSameOwnerClass(childBinding);
    }

    private boolean hasSameOwnerClass(TemplateVariableBinding otherBinding) {
        // owner class can be same in recursive rendering; in that case, the child binding should be used for setting variable values
        return (getOwner() != null && otherBinding.getOwner() != null && getOwner().getClass()==otherBinding.getOwner().getClass());
    }

    public String getPluginContextPath() {
        return (String)getVariable(GroovyPage.PLUGIN_CONTEXT_PATH);
    }

    @SuppressWarnings("unchecked")
    public void setPluginContextPath(String pluginContextPath) {
        getVariablesMap().put(GroovyPage.PLUGIN_CONTEXT_PATH, pluginContextPath);
    }

    @SuppressWarnings("unchecked")
    public void setPagePlugin(GrailsPlugin plugin) {
        getVariablesMap().put("pagePlugin", plugin);
    }

    public GrailsPlugin getPagePlugin() {
        return (GrailsPlugin)getVariable("pagePlugin");
    }

    protected boolean isReservedName(String name) {
        return GroovyPage.isReservedName(name);
    }
}
