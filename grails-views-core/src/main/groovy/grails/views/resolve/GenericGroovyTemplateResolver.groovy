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

package grails.views.resolve

import groovy.text.Template
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import grails.util.BuildSettings
import grails.views.TemplateResolver
import org.grails.io.support.GrailsResourceUtils

/**
 * A generic TemplateResolver for resolving Groovy templates that are compiled into classes
 *
 * @author Graeme Rocher
 */
@CompileStatic
@Slf4j
class GenericGroovyTemplateResolver implements TemplateResolver {
    /**
     * The base directory to load templates in development mode
     */
    public static final char SLASH_CHAR = '/' as char
    public static final char DOT_CHAR = '.' as char
    public static final char UNDERSCORE_CHAR = '_' as char


    File baseDir = BuildSettings.BASE_DIR ? new File(BuildSettings.BASE_DIR, GrailsResourceUtils.VIEWS_DIR_PATH) : null

    /**
     * The base package to load templates as classes in production mode
     */
    String packageName = ""

    /**
     * The class loader to use for template loading in production mode
     */
    ClassLoader classLoader

    @Override
    URL resolveTemplate(String path) {
        if(baseDir != null) {
            def f = new File(baseDir, path)
            if(f.exists()) {
                return f.toURI().toURL()
            }
        }
        return null
    }

    @Override
    Class<? extends Template> resolveTemplateClass(String path) {
        resolveTemplateClass(packageName, path)
    }

    @Override
    Class<? extends Template> resolveTemplateClass(String packageName, String path) {
        String className = resolveTemplateName(packageName, path)
        try {
            log.trace("Attempting to load class [$className] for template [$path]")
            def cls = classLoader.loadClass(className)
            return (Class<? extends Template>)cls
        } catch (Throwable e) {
        }
        return null
    }

    static String resolveTemplateName(String scope, String path) {
        if(path.startsWith(File.separator) || path.startsWith('/')) {
            path = path.substring(1) // remove leading path separator
        }
        path = path.replace(File.separatorChar, UNDERSCORE_CHAR)
        path = path.replace(SLASH_CHAR, UNDERSCORE_CHAR)
        path = path.replace(DOT_CHAR, UNDERSCORE_CHAR)
        if(scope) {
            scope = scope.replaceAll(/[\W\s]/, String.valueOf(UNDERSCORE_CHAR))
            path = "${scope}_${path}"
        }
        return path
    }
}
