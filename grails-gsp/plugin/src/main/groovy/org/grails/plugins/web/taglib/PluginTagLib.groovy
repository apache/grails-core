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
package org.grails.plugins.web.taglib

import grails.artefact.TagLibrary
import grails.gsp.TagLib
import grails.plugins.GrailsPluginManager

/**
 * Tags to inspect available plugins.
 *
 * @author Graeme Rocher
 * @since 1.1
 */
@TagLib
class PluginTagLib implements TagLibrary {

    static String namespace = "plugin"

    GrailsPluginManager pluginManager

    /**
     * Gets the path to a particular plugin.<br/>
     *
     * eg. &lt;plugin:path name="myPlugin" /&gt;
     *
     * @attr name REQUIRED the plugin name
     */
    Closure path = { attrs, body ->
        out << pluginManager.getPluginPath(attrs.name)
    }

    /**
     * Checks whether a particular plugin exists and executes the body if it does.<br/>
     *
     * eg. &lt;plugin:isAvailable name="hibernate"&gt;print me&lt;/plugin:isAvailable&gt;
     *
     * @attr name REQUIRED the plugin name
     * @attr version the plugin version
     */
    Closure isAvailable = { attrs, body ->
        if (checkPluginExists(attrs.version, attrs.name)) {
            out << body()
        }
    }

    /**
     * Checks whether a particular plugin does not exist and executes the body if it does.<br/>
     *
     * eg. &lt;plugin:isNotAvailable name="hibernate"&gt;print me&lt;/plugin:isNotAvailable&gt;
     *
     * @attr name REQUIRED the plugin name
     * @attr version the plugin version
     */
    Closure isNotAvailable = { attrs, body ->
        if (!checkPluginExists(attrs.version, attrs.name)) {
            out << body()
        }
    }

    private boolean checkPluginExists(version, name) {
        if (name) {
            if (version && pluginManager.getGrailsPlugin(name, version)) {
                return true
            }
            if (pluginManager.hasGrailsPlugin(name)) {
                return true
            }
        }
        return false
    }
}
