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

import org.grails.taglib.TemplateVariableBinding;
import org.springframework.core.io.ResourceLoader;

/**
 * Used to locate GSPs whether in development or WAR deployed mode.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public interface GroovyPageLocator {

    /**
     * Adds a new resource loader to search
     *
     * @param resourceLoader The resource loader to search
     */
    void addResourceLoader(ResourceLoader resourceLoader);

    /**
     * Finds a page for the given URI
     *
     * @param uri The URI
     * @return A script source
     */
    GroovyPageScriptSource findPage(String uri);

    /**
     * Finds a page for the given URI
     *
     * @param pluginName The plugin name
     * @param uri The URI
     * @param binding The biding to use
     * @return A script source
     */
    GroovyPageScriptSource findPageInBinding(String pluginName, String uri, TemplateVariableBinding binding);

    /**
     * Finds a page for the URI and binding
     *
     * @param uri The URI
     * @param binding The binding
     * @return The page source
     */
    GroovyPageScriptSource findPageInBinding(String uri, TemplateVariableBinding binding);

    /**
     * Removes any precompiled pages for the given URI so that they can be replaced by dynamic pages
     *
     * @param compiledScriptSource The compiled script source
     */
    void removePrecompiledPage(GroovyPageCompiledScriptSource compiledScriptSource);
}
