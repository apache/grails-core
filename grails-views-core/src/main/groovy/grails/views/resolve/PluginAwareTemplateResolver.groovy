/*
 * Copyright 2015 original authors
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
package grails.views.resolve

import grails.plugins.GrailsPluginManager
import grails.plugins.PluginManagerAware
import grails.views.ViewConfiguration
import groovy.text.Template
import groovy.transform.CompileStatic
import org.grails.io.support.GrailsResourceUtils
import org.grails.plugins.BinaryGrailsPlugin


/**
 * A template resolver capable of looking through the installed Grails plugins and finding a template within the scope of the plugin
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class PluginAwareTemplateResolver extends GenericGroovyTemplateResolver implements PluginManagerAware {
    GrailsPluginManager pluginManager

    PluginAwareTemplateResolver(ViewConfiguration viewConfiguration) {
        this.packageName = viewConfiguration.packageName
        this.baseDir = new File(viewConfiguration.templatePath)
        this.classLoader = Thread.currentThread().contextClassLoader
    }

    void setPluginManager(GrailsPluginManager pluginManager) {
        this.pluginManager = pluginManager
    }

    @Override
    Class<? extends Template> resolveTemplateClass(String path) {
        Class<? extends Template> applicationTemplate = super.resolveTemplateClass(path)
        if(applicationTemplate == null) {
            // try global template
            applicationTemplate = resolveTemplateClass(null, path)
        }
        if(applicationTemplate == null && pluginManager != null) {
            // search plugins for template
            for( plugin in pluginManager.allPlugins ) {
                Class<? extends Template> pluginTemplate = resolveTemplateClass(plugin.fileSystemShortName, path)
                if(pluginTemplate != null) {
                    return pluginTemplate
                }
            }
        }
        return applicationTemplate
    }

    @Override
    URL resolveTemplate(String path) {
        URL applicationTemplate = super.resolveTemplate(path)
        if(applicationTemplate == null && pluginManager != null) {
            for( plugin in pluginManager.allPlugins ) {
                if(plugin instanceof BinaryGrailsPlugin) {
                    BinaryGrailsPlugin binaryGrailsPlugin = (BinaryGrailsPlugin) plugin
                    File projectDirectory = binaryGrailsPlugin.getProjectDirectory()
                    if (projectDirectory != null) {
                        File f = new File(projectDirectory, GrailsResourceUtils.VIEWS_DIR_PATH + path.replaceFirst('/', ''))
                        if (f.exists()) {
                            return f.toURI().toURL()
                        }
                    }
                }
            }
        }
        return applicationTemplate
    }

}
