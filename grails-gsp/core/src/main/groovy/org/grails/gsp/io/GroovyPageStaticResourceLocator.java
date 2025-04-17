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
package org.grails.gsp.io;

import grails.plugins.GrailsPlugin;
import org.grails.core.io.DefaultResourceLocator;
import org.grails.gsp.GroovyPageBinding;
import org.grails.io.support.GrailsResourceUtils;
import org.grails.taglib.AbstractTemplateVariableBinding;
import org.grails.taglib.encoder.OutputContext;
import org.grails.taglib.encoder.OutputContextLookupHelper;
import org.springframework.core.io.Resource;

/**
 * <p>
 *   Extends the {@link DefaultResourceLocator} class with extra methods to evaluate static resources relative to the currently executing GSP page.
 * </p>
 *
 * <p>This class is used to resolve references to static resources like CSS, Javascript and images files</p>
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class GroovyPageStaticResourceLocator extends DefaultResourceLocator {

    @Override
    public Resource findResourceForURI(String uri) {
        Resource resource = super.findResourceForURI(uri);
        if (resource == null || !resource.exists()) {
            AbstractTemplateVariableBinding binding = findBindingInOutputContext();
            if (binding instanceof GroovyPageBinding) {
                GrailsPlugin pagePlugin = ((GroovyPageBinding)binding).getPagePlugin();
                if (pagePlugin != null && pluginManager != null) {
                    resource = findResourceForPlugin(pagePlugin, uri);
                }
            }
            else if (pluginManager != null) {
                // attempt brute force search of all plugins
                for (GrailsPlugin plugin : pluginManager.getAllPlugins()) {
                    resource = findResourceForPlugin(plugin, uri);
                    if (resource != null) break;
                }
            }
        }
        return resource;

    }

    private Resource findResourceForPlugin(GrailsPlugin plugin, String uri) {
        Resource resource;
        String pluginPath = pluginManager.getPluginPath(plugin.getName());
        String pluginUri = GrailsResourceUtils.appendPiecesForUri(pluginPath, uri);
        resource = super.findResourceForURI(pluginUri);
        return resource;
    }

    protected AbstractTemplateVariableBinding findBindingInOutputContext() {
        OutputContext outputContext = OutputContextLookupHelper.lookupOutputContext();
        if (outputContext != null) {
            return outputContext.getBinding();
        }
        return null;
    }

}
