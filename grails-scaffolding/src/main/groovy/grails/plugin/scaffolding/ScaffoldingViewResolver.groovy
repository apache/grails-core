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

package grails.plugin.scaffolding

import java.util.concurrent.ConcurrentHashMap

import groovy.text.GStringTemplateEngine
import groovy.text.Template
import groovy.transform.CompileStatic

import org.springframework.context.ResourceLoaderAware
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.UrlResource
import org.springframework.web.servlet.View

import grails.codegen.model.ModelBuilder
import grails.core.GrailsControllerClass
import grails.io.IOUtils
import grails.plugin.scaffolding.annotation.Scaffold
import grails.util.BuildSettings
import grails.util.Environment
import org.grails.buffer.FastStringWriter
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.servlet.view.GroovyPageView
import org.grails.web.servlet.view.GroovyPageViewResolver

/**
 * @author Graeme Rocher
 * @since 3.1
 */
@CompileStatic
class ScaffoldingViewResolver extends GroovyPageViewResolver implements ResourceLoaderAware, ModelBuilder {

    final Class templateOverridePluginDescriptor

    ScaffoldingViewResolver() {
        this.templateOverridePluginDescriptor = null
    }

    /**
     * This constructor allows a plugin to override the default templates provided by the Scaffolding
     * plugin.  The plugin that contains the template override should be configured to load AFTER the
     * scaffolding plugin.  An example implementation follows:
     *
     * <pre>
     * {@code
     * def loadAfter = ['scaffolding']
     * }
     * </pre>
     * ...
     * <pre>
     * {@code
     * @Override
     * Closure doWithSpring() { { ->
     *    jspViewResolver(ScaffoldingViewResolver, this.class) { bean ->
     *        bean.lazyInit = true
     *        bean.parent = "abstractViewResolver"
     *    }
     * }}
     * </pre>
     *
     * @param templateOverridePluginDescriptor
     */
    ScaffoldingViewResolver(Class templateOverridePluginDescriptor) {
        this.templateOverridePluginDescriptor = templateOverridePluginDescriptor
    }

    private static final Object NULL_SCAFFOLD_VALUE = new Object()

    ResourceLoader resourceLoader
    protected Map<String, View> generatedViewCache = new ConcurrentHashMap<>()
    protected Map<Class, Object> scaffoldValueCache = new ConcurrentHashMap<>()
    protected boolean enableReload = false
    protected boolean enableNamespaceViewDefaults = false

    void setEnableReload(boolean enableReload) {
        this.enableReload = enableReload
    }

    void setEnableNamespaceViewDefaults(boolean enableNamespaceViewDefaults) {
        this.enableNamespaceViewDefaults = enableNamespaceViewDefaults
    }

    protected String buildCacheKey(String viewName) {
        String viewCacheKey = groovyPageLocator.resolveViewFormat(viewName)
        String currentControllerKeyPrefix = resolveCurrentControllerKeyPrefixes(viewName.startsWith('/'))
        if (currentControllerKeyPrefix != null) {
            viewCacheKey = currentControllerKeyPrefix + ':' + viewCacheKey
        }
        viewCacheKey
    }

    private Resource resolveResource(Class controllerClass, shortViewName) {
        Resource resource
        if (Environment.isDevelopmentMode()) {
            resource = new FileSystemResource(new File(BuildSettings.BASE_DIR, "src/main/templates/scaffolding/${shortViewName}.gsp"))
            if (resource.exists()) {
                return resource
            }
        }

        def url = IOUtils.findResourceRelativeToClass(controllerClass, "/META-INF/templates/scaffolding/${shortViewName}.gsp")
        resource = url ? new UrlResource(url) : null
        if (resource?.exists()) {
            return resource
        }

        if (templateOverridePluginDescriptor) {
            url = IOUtils.findResourceRelativeToClass(templateOverridePluginDescriptor, "/META-INF/templates/scaffolding/${shortViewName}.gsp")
            resource = url ? new UrlResource(url) : null
            if (resource?.exists()) {
                return resource
            }
        }
        resourceLoader.getResource("classpath:META-INF/templates/scaffolding/${shortViewName}.gsp")
    }

    @Override
    protected View loadView(String viewName, Locale locale) throws Exception {
        def view = super.loadView(viewName, locale)

        if (view != null) {
            if (!enableNamespaceViewDefaults) {
                return view
            }

            def controllerClass = GrailsWebRequest.lookup()?.controllerClass
            if (controllerClass?.namespace) {
                // Check if the view found is already a namespace-specific view
                def isNamespaceSpecificView = view instanceof GroovyPageView &&
                        view.url?.contains("/${controllerClass.namespace}/")

                if (!isNamespaceSpecificView) {
                    // View is a fallback (non-namespaced), check for namespace-specific scaffolded template
                    return tryGenerateScaffoldedView(viewName, controllerClass) { String shortViewName ->
                        // Only check namespace-specific template
                        resolveResource(controllerClass.clazz, "${controllerClass.namespace}/${shortViewName}")
                    } ?: view
                }
            }
            return view
        }

        def controllerClass = GrailsWebRequest.lookup()?.controllerClass

        return tryGenerateScaffoldedView(viewName, controllerClass) { String shortViewName ->
            Resource res = controllerClass?.namespace ? resolveResource(controllerClass.clazz, "${controllerClass.namespace}/${shortViewName}") : null
            if (!res?.exists()) {
                res = resolveResource(controllerClass.clazz, shortViewName)
            }
            return res
        }
    }

    /**
     * Attempts to generate a scaffolded view for the given controller
     * @param viewName The view name
     * @param controllerClass The controller class
     * @param resourceResolver Closure that resolves the scaffold template resource given a short view name
     * @return The generated scaffolded view, or null if not applicable
     */
    private View tryGenerateScaffoldedView(String viewName, GrailsControllerClass controllerClass, Closure<Resource> resourceResolver) {
        def scaffoldValue = getScaffoldValue(controllerClass)
        if (!(scaffoldValue instanceof Class)) {
            return null
        }

        String cacheKey = buildCacheKey(viewName)

        // Check cache first
        def cachedScaffoldedView = enableReload ? null : generatedViewCache.get(cacheKey)
        if (cachedScaffoldedView != null) {
            return cachedScaffoldedView
        }

        def shortViewName = viewName.substring(viewName.lastIndexOf('/') + 1)
        Resource scaffoldResource = resourceResolver.call(shortViewName)

        if (scaffoldResource?.exists()) {
            return generateScaffoldedView(scaffoldValue, scaffoldResource, cacheKey)
        }

        return null
    }

    private Object getScaffoldValue(GrailsControllerClass controllerClass) {
        if (!controllerClass) {
            return null
        }

        // Cache the scaffold value to avoid repeated reflection
        Class controllerClazz = controllerClass.clazz
        if (scaffoldValueCache.containsKey(controllerClazz)) {
            Object cached = scaffoldValueCache.get(controllerClazz)
            return cached == NULL_SCAFFOLD_VALUE ? null : cached
        }

        def scaffoldValue = controllerClass.getPropertyValue('scaffold')
        if (!scaffoldValue) {
            Scaffold scaffoldAnnotation = controllerClazz?.getAnnotation(Scaffold)
            if (scaffoldAnnotation) {
                // Check domain() attribute for view scaffolding - domain class is required for model generation.
                // Note: For @Scaffold(RestfulServiceController<T>), the AST transformation
                // (ScaffoldingControllerInjector) extracts T and sets it as domain() at compile time,
                // so this works for both @Scaffold(domain = User) and @Scaffold(RestfulServiceController<User>).
                scaffoldValue = scaffoldAnnotation.domain()
                if (scaffoldValue == Void) {
                    scaffoldValue = null
                }
            }
        }

        // Cache the result (even if null, to avoid repeated lookups)
        scaffoldValueCache.put(controllerClazz, scaffoldValue == null ? NULL_SCAFFOLD_VALUE : scaffoldValue)
        return scaffoldValue
    }

    private View generateScaffoldedView(Class scaffoldValue, Resource res, String cacheKey) {
        def model = model((Class) scaffoldValue)
        def viewGenerator = new GStringTemplateEngine()
        Template t = viewGenerator.createTemplate(res.URL)

        def contents = new FastStringWriter()
        t.make(model.asMap()).writeTo(contents)

        def template = templateEngine.createTemplate(new ByteArrayResource(contents.toString().getBytes(templateEngine.gspEncoding), "view:$cacheKey"), !enableReload)
        def view = new GroovyPageView()
        view.setServletContext(getServletContext())
        view.setTemplate(template)
        view.setApplicationContext(getApplicationContext())
        view.setTemplateEngine(templateEngine)
        view.afterPropertiesSet()
        generatedViewCache.put(cacheKey, view)
        return view
    }

    @Override
    void clearCache() {
        super.clearCache()
        generatedViewCache.clear()
        scaffoldValueCache.clear()
    }
}
