package grails.plugin.scaffolding

import grails.codegen.model.ModelBuilder
import grails.io.IOUtils
import grails.plugin.scaffolding.annotation.Scaffold
import grails.util.BuildSettings
import grails.util.Environment
import groovy.text.GStringTemplateEngine
import groovy.text.Template
import groovy.transform.CompileStatic
import org.grails.buffer.FastStringWriter
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.servlet.view.GroovyPageView
import org.grails.web.servlet.view.GroovyPageViewResolver
import org.springframework.context.ResourceLoaderAware
import org.springframework.core.io.*
import org.springframework.web.servlet.View

import java.util.concurrent.ConcurrentHashMap

/**
 * @author Graeme Rocher
 * @since 3.1
 */
@CompileStatic
class ScaffoldingViewResolver extends GroovyPageViewResolver implements ResourceLoaderAware, ModelBuilder {
    final Class templateOverridePluginDescriptor

    public ScaffoldingViewResolver() {
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
    public ScaffoldingViewResolver(Class templateOverridePluginDescriptor) {
        this.templateOverridePluginDescriptor = templateOverridePluginDescriptor
    }

    ResourceLoader resourceLoader
    protected Map<String, View> generatedViewCache = new ConcurrentHashMap<>()
    protected boolean enableReload = false
    void setEnableReload(boolean enableReload) {
        this.enableReload = enableReload
    }

    protected String buildCacheKey(String viewName) {
        String viewCacheKey = groovyPageLocator.resolveViewFormat(viewName)
        String currentControllerKeyPrefix = resolveCurrentControllerKeyPrefixes(viewName.startsWith("/"))
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
        resource = url? new UrlResource(url) : null
        if (resource?.exists()) {
            return resource
        }

        if (templateOverridePluginDescriptor) {
            url = IOUtils.findResourceRelativeToClass(templateOverridePluginDescriptor, "/META-INF/templates/scaffolding/${shortViewName}.gsp")
            resource = url? new UrlResource(url) : null
            if (resource?.exists()) {
                return resource
            }
        }
        resourceLoader.getResource("classpath:META-INF/templates/scaffolding/${shortViewName}.gsp")
    }

    @Override
    protected View loadView(String viewName, Locale locale) throws Exception {
        def view = super.loadView(viewName, locale)
        if (view == null) {
            String cacheKey = buildCacheKey(viewName)
            view = enableReload? null : generatedViewCache.get(cacheKey)
            if (view != null) {
                return view
            } else {
                def webR = GrailsWebRequest.lookup()
                def controllerClass = webR.controllerClass

                def scaffoldValue = controllerClass?.getPropertyValue("scaffold")
                if (!scaffoldValue) {
                    Scaffold scaffoldAnnotation = controllerClass?.clazz?.getAnnotation(Scaffold)
                    scaffoldValue = scaffoldAnnotation?.domain()
                    if (scaffoldValue == Void) {
                        scaffoldValue = null
                    }
                }

                if (scaffoldValue instanceof Class) {
                    def shortViewName = viewName.substring(viewName.lastIndexOf('/') + 1)
                    Resource res = controllerClass.namespace? resolveResource(controllerClass.clazz, "${controllerClass.namespace}/${shortViewName}") : null
                    if (!res?.exists()) {
                        res = resolveResource(controllerClass.clazz, shortViewName)
                    }
                    if (res.exists()) {
                        def model = model((Class) scaffoldValue)
                        def viewGenerator = new GStringTemplateEngine()
                        Template t = viewGenerator.createTemplate(res.URL)

                        def contents = new FastStringWriter()
                        t.make(model.asMap()).writeTo(contents)

                        def template = templateEngine.createTemplate(new ByteArrayResource(contents.toString().getBytes(templateEngine.gspEncoding), "view:$cacheKey"), !enableReload)
                        view = new GroovyPageView()
                        view.setServletContext(getServletContext())
                        view.setTemplate(template)
                        view.setApplicationContext(getApplicationContext())
                        view.setTemplateEngine(templateEngine)
                        view.afterPropertiesSet()
                        generatedViewCache.put(cacheKey, view)
                        return view
                    } else {
                        return view
                    }
                }
            }
        }
        return view
    }
}