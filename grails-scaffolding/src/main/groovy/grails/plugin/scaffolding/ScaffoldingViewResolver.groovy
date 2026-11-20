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

import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.aot.AotDetector
import org.springframework.context.ResourceLoaderAware
import org.springframework.core.NativeDetector
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
import org.apache.grails.scaffolding.ScaffoldedPages
import org.grails.gsp.io.GroovyPageScriptSource
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

    private static final Logger LOG = LoggerFactory.getLogger(ScaffoldingViewResolver)

    private static final Object NULL_SCAFFOLD_VALUE = new Object()

    ResourceLoader resourceLoader
    protected Map<String, View> generatedViewCache = new ConcurrentHashMap<>()
    protected Map<Class, Object> scaffoldValueCache = new ConcurrentHashMap<>()
    protected boolean enableReload = false
    /** The pages already reported as having no compiled page, so each is reported once. */
    protected final Set<String> reportedPages = ConcurrentHashMap.newKeySet()
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
        if (readsTemplatesFromProject()) {
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

    /**
     * Whether templates are read from the project's {@code src/main/templates}, so that an edit to
     * one shows without a rebuild. That is so during development, unless the application runs from
     * ahead-of-time artifacts: there the pages compiled at build time are used whatever the
     * surroundings suggest, and a page is found by the template it was compiled from, which is the
     * packaged one.
     */
    protected boolean readsTemplatesFromProject() {
        return isDevelopmentMode() && !AotDetector.useGeneratedArtifacts()
    }

    /**
     * Whether the application is being developed. Overridable because that is derived from the
     * working directory when the class is loaded, and so cannot be varied any other way.
     */
    protected boolean isDevelopmentMode() {
        return Environment.isDevelopmentMode()
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
                        ["${controllerClass.namespace}/${shortViewName}".toString()]
                    } ?: view
                }
            }
            return view
        }

        return tryGenerateScaffoldedView(viewName, GrailsWebRequest.lookup()?.controllerClass)
    }

    /**
     * Attempts to generate a scaffolded view for the given controller from the template it uses for
     * a view it has none of: its namespace's, when it has a namespace and there is one, otherwise
     * the general one.
     * @param viewName The view name
     * @param controllerClass The controller class
     * @return The generated scaffolded view, or null if not applicable
     */
    protected View tryGenerateScaffoldedView(String viewName, GrailsControllerClass controllerClass) {
        return tryGenerateScaffoldedView(viewName, controllerClass) { String shortViewName ->
            controllerClass?.namespace ?
                    ["${controllerClass.namespace}/${shortViewName}".toString(), shortViewName] :
                    [shortViewName]
        }
    }

    /**
     * Attempts to generate a scaffolded view for the given controller
     * @param viewName The view name
     * @param controllerClass The controller class
     * @param templatePaths Closure giving, for a short view name, the template paths to try in order
     * @return The generated scaffolded view, or null if not applicable
     */
    protected View tryGenerateScaffoldedView(String viewName, GrailsControllerClass controllerClass, Closure<List<String>> templatePaths) {
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
        for (String templatePath : templatePaths.call(shortViewName)) {
            Resource template = resolveResource(controllerClass.clazz, templatePath)
            if (template?.exists()) {
                return generateScaffoldedView((Class) scaffoldValue, templatePath, template, cacheKey)
            }
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

    private View generateScaffoldedView(Class scaffoldValue, String templatePath, Resource res, String cacheKey) {
        Map<String, Object> model = model(scaffoldValue).asMap()
        byte[] template = read(res)
        View view = enableReload ? null : findPrecompiledView(templatePath, model, template)
        if (view == null) {
            view = expandTemplate(model, template, cacheKey)
        }
        generatedViewCache.put(cacheKey, view)
        return view
    }

    /**
     * The page the build compiled from this template and model, if it compiled one.
     *
     * <p>Every decision about which template to use has been made by the time this is asked, the
     * same way whether or not anything was compiled, so this only replaces the expansion. The page
     * is found by the template and the model together: a template the build did not see finds
     * nothing and is expanded as it would be otherwise. For each scaffolded controller the build
     * compiles every copy of each template it can choose, so whichever copy was chosen has its
     * page.</p>
     */
    private View findPrecompiledView(String templatePath, Map<String, Object> model, byte[] template) {
        View view = findPage(ScaffoldedPages.uri(templatePath, model, template))
        if (view != null) {
            return view
        }
        reportMissingPage(templatePath, model)
        return null
    }

    private View findPage(String uri) {
        GroovyPageScriptSource page = groovyPageLocator.findPage(uri)
        return page == null ? null : createGroovyPageView(uri, page)
    }

    /**
     * Reports, once per page, a scaffolded view that has no compiled page where compiled pages are
     * in use. During development, and in an application's tests, which render the views as they
     * are, they are not, and nothing is reported.
     *
     * <p>On the JVM the template is expanded instead, as it always was, so that is worth no more
     * than a note. A native image cannot define the page's class at runtime, so there the
     * expansion that follows fails, and the warning says why.</p>
     */
    private void reportMissingPage(String templatePath, Map<String, Object> model) {
        if (!precompiledPagesInUse()) {
            LOG.debug('Expanding the scaffolding template {} for {}', templatePath, model.fullName)
            return
        }
        if (!reportedPages.add("${model.fullName}:${templatePath}".toString())) {
            return
        }
        if (inNativeImage()) {
            LOG.warn('Scaffolding template {} for {} was not compiled by the build, and a native image cannot expand it ' +
                    'at runtime. The scaffolding guide lists the pages the build compiles; otherwise give the controller ' +
                    'a view of its own.', templatePath, model.fullName)
        }
        else {
            LOG.info('Scaffolding template {} for {} was not compiled by the build, so it is expanded on first use',
                    templatePath, model.fullName)
        }
    }

    /** Whether pages compiled by the build are used, which is whether the page locator uses them. */
    protected boolean precompiledPagesInUse() {
        return groovyPageLocator.precompiledAvailable
    }

    /** Whether this runs in a native image, which cannot expand a template into a page at runtime. */
    protected boolean inNativeImage() {
        return NativeDetector.inNativeImage()
    }

    private View expandTemplate(Map<String, Object> model, byte[] template, String cacheKey) {
        String page = ScaffoldedPages.expand(template, model)
        def compiled = templateEngine.createTemplate(new ByteArrayResource(page.getBytes(templateEngine.gspEncoding), "view:$cacheKey"), !enableReload)
        def view = new GroovyPageView()
        view.setServletContext(getServletContext())
        view.setTemplate(compiled)
        view.setApplicationContext(getApplicationContext())
        view.setTemplateEngine(templateEngine)
        view.afterPropertiesSet()
        return view
    }

    private static byte[] read(Resource resource) {
        resource.inputStream.withCloseable { InputStream input -> input.bytes }
    }

    @Override
    void clearCache() {
        super.clearCache()
        generatedViewCache.clear()
        scaffoldValueCache.clear()
    }
}
