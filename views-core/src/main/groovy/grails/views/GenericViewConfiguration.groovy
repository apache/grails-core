package grails.views

import grails.config.ConfigMap
import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.core.support.GrailsApplicationAware
import grails.util.BuildSettings
import grails.util.Environment
import grails.util.Metadata
import groovy.transform.CompileStatic
import org.grails.config.CodeGenConfig
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.io.support.GrailsResourceUtils
import org.springframework.beans.BeanUtils

import java.beans.PropertyDescriptor

/**
 * Default configuration
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
trait GenericViewConfiguration implements ViewConfiguration, GrailsApplicationAware {

    /**
     * The encoding to use
     */
    String encoding = "UTF-8"
    /**
     * Whether to pretty print
     */
    boolean prettyPrint = false
    /**
     * Whether to use absolute links
     */
    boolean useAbsoluteLinks = false
    /**
     * Whether to enable reloading
     */
    boolean enableReloading = ViewsEnvironment.isDevelopmentMode()
    /**
     * The package name to use
     */
    String packageName = Metadata.getCurrent().getApplicationName() ?: ""
    /**
     * Whether to compile templates statically
     */
    boolean compileStatic = true
    /**
     * The file extension of the templates
     */
    String extension
    /**
     * The template base class
     */
    Class baseTemplateClass
    /**
     * Whether the cache templates
     */
    boolean cache = !Environment.isDevelopmentMode()
    /**
     * Whether resource expansion is allowed
     */
    boolean allowResourceExpansion = true
    /**
     * The path to the templates
     */
    String templatePath = {
        def current = Environment.current
        def pathToTemplates = current.hasReloadLocation() ? current.reloadLocation : BuildSettings.BASE_DIR?.path
        pathToTemplates ? new File(pathToTemplates, GrailsResourceUtils.VIEWS_DIR_PATH).path : "./grails-app/views"
    }()
    /**
     * The default package imports
     */
    String[] packageImports = ['groovy.transform'] as String[]
    /**
     * The default static imports
     */
    String[] staticImports = ["org.springframework.http.HttpStatus", "org.springframework.http.HttpMethod", "grails.web.http.HttpHeaders"] as String[]

    @Override
    void setGrailsApplication(GrailsApplication grailsApplication) {
        if(grailsApplication != null) {
            def domainArtefacts = grailsApplication.getArtefacts(DomainClassArtefactHandler.TYPE)
            setPackageImports(
                    findUniquePackages(domainArtefacts)
            )
        }
    }

    void readConfiguration(File configFile) {
        if(configFile?.exists()) {
            def config = new CodeGenConfig()
            config.loadYml(configFile)
            readConfiguration(config)
        }
    }

    void readConfiguration(ConfigMap config) {
        String moduleName = viewModuleName
        GroovyObject configObject = (GroovyObject)this
        if (config != null) {
            PropertyDescriptor[] descriptors =  BeanUtils.getPropertyDescriptors(GenericViewConfiguration)
            for (PropertyDescriptor desc in descriptors) {
                if (desc.writeMethod != null) {
                    String propertyName = desc.name
                    Object value = null
                    if (desc.propertyType == Class) {
                        String className = config.getProperty("grails.views.${moduleName}.$propertyName".toString(), String)
                        if (className) {
                            value = getClass().classLoader.loadClass(className)
                        }
                    } else {
                        value = config.getProperty("grails.views.${moduleName}.$propertyName", (Class) desc.propertyType)
                    }
                    if(value != null) {
                        configObject.setProperty(propertyName, value)
                    }
                }
            }
        }
    }

    String[] findUniquePackages(GrailsClass[] grailsClasses) {
        Set packages = []
        for (GrailsClass cls : grailsClasses) {
            packages << cls.packageName
        }
        packages as String[]
    }
}
