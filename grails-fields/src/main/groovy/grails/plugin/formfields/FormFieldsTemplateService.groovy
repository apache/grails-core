/*
 * Copyright 2012 Rob Fletcher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugin.formfields

import grails.core.GrailsApplication
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import grails.util.GrailsNameUtils
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.ManyToOne
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.OneToOne
import org.grails.scaffolding.model.property.Constrained
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.request.RequestContextHolder

import static org.grails.io.support.GrailsResourceUtils.appendPiecesForUri

@Slf4j
@CompileStatic
class FormFieldsTemplateService {

    public static final String SETTING_WIDGET_PREFIX = 'grails.plugin.fields.widgetPrefix'
    public static final String DISABLE_LOOKUP_CACHE = 'grails.plugin.fields.disableLookupCache'
    private static final String THEMES_FOLDER = "_themes"

    @Autowired
    GrailsApplication grailsApplication

    @Autowired
    GrailsConventionGroovyPageLocator groovyPageLocator

    @Autowired
    GrailsPluginManager pluginManager

    String getWidgetPrefix() {
        return shouldCache ? widgetPrefixCached : widgetPrefixNotCached
    }

    String getTemplateFor(String property) {
        shouldCache ? getTemplateForCached(property) : getTemplateForNotCached(property)
    }

    Map findTemplate(BeanPropertyAccessor propertyAccessor, String templateName, String templatesFolder, String theme = null) {
        shouldCache ?
                findTemplateCached(propertyAccessor, controllerNamespace, controllerName, actionName, templateName, templatesFolder, theme) :
                findTemplateNotCached(propertyAccessor, controllerNamespace, controllerName, actionName, templateName, templatesFolder, theme)
    }

    static String toPropertyNameFormat(Class type) {
        def propertyNameFormat = GrailsNameUtils.getLogicalPropertyName(type.canonicalName, '')
        if (propertyNameFormat.endsWith('[]')) {
            propertyNameFormat = propertyNameFormat - '[]' + 'Array'
        }
        return propertyNameFormat
    }

    private List<String> candidateTemplatePaths(BeanPropertyAccessor propertyAccessor, String controllerNamespace, String controllerName, String actionName, String templateName, String templatesFolder, String themeFolder) {
        List<String> templateResolveOrder = []

        // if we have a widget look in `grails-app/views/_fields/<templateFolder>/_field.gsp`
        if (templatesFolder) {
            templateResolveOrder << appendPiecesForUri("/_fields", themeFolder, templatesFolder, templateName)
        }

        // if there is a controller namespace for the current request any template in its views directory takes priority
        if (controllerNamespace) {
            // first try action-specific templates
            templateResolveOrder << appendPiecesForUri("/", controllerNamespace, controllerName, actionName, propertyAccessor.propertyName, themeFolder, templateName)
            if (propertyAccessor.propertyType) templateResolveOrder << appendPiecesForUri("/", controllerNamespace, controllerName, actionName, themeFolder, toPropertyNameFormat(propertyAccessor.propertyType), templateName)
            templateResolveOrder << appendPiecesForUri("/", controllerNamespace, controllerName, actionName, themeFolder, templateName)

            // then general templates for the controller
            templateResolveOrder << appendPiecesForUri("/", controllerNamespace, controllerName, propertyAccessor.propertyName, themeFolder, templateName)
            if (propertyAccessor.propertyType) templateResolveOrder << appendPiecesForUri("/", controllerNamespace, controllerName, themeFolder, toPropertyNameFormat(propertyAccessor.propertyType), templateName)
            templateResolveOrder << appendPiecesForUri("/", controllerNamespace, controllerName, themeFolder, templateName)
        }

        // if there is a controller for the current request any template in its views directory takes priority
        if (controllerName) {
            // first try action-specific templates
            templateResolveOrder << appendPiecesForUri("/", controllerName, actionName, propertyAccessor.propertyName, themeFolder, templateName)
            if (propertyAccessor.propertyType) templateResolveOrder << appendPiecesForUri("/", controllerName, actionName, themeFolder, toPropertyNameFormat(propertyAccessor.propertyType), templateName)
            templateResolveOrder << appendPiecesForUri("/", controllerName, actionName, themeFolder, templateName)

            // then general templates for the controller
            templateResolveOrder << appendPiecesForUri("/", controllerName, propertyAccessor.propertyName, themeFolder, templateName)
            if (propertyAccessor.propertyType) templateResolveOrder << appendPiecesForUri("/", controllerName, themeFolder, toPropertyNameFormat(propertyAccessor.propertyType), templateName)
            templateResolveOrder << appendPiecesForUri("/", controllerName, themeFolder, templateName)
        }

        // if we have a bean type look in `grails-app/views/_fields/<beanType>/<propertyName>/_field.gsp` and equivalent for superclasses
        if (propertyAccessor.beanType) {
            templateResolveOrder << appendPiecesForUri("/_fields", themeFolder, toPropertyNameFormat(propertyAccessor.beanType), propertyAccessor.propertyName, templateName)
            for (superclass in propertyAccessor.beanSuperclasses) {
                templateResolveOrder << appendPiecesForUri("/_fields", themeFolder, toPropertyNameFormat(superclass), propertyAccessor.propertyName, templateName)
            }
        }

        // if this is an association property look in `grails-app/views/_fields/<associationType>/_field.gsp`
        String associationPath = getAssociationPath(propertyAccessor)
        if (associationPath) {
            templateResolveOrder << appendPiecesForUri('/_fields', themeFolder, associationPath, templateName)
        }

        // if we have a domain constraint widget look in `grails-app/views/_fields/<widget>/_field.gsp`
        String widget = getWidget(propertyAccessor.constraints, propertyAccessor.propertyType)
        if (widget) {
            templateResolveOrder << appendPiecesForUri("/_fields", themeFolder, widget, templateName)
        }

        // if we have a property type look in `grails-app/views/_fields/<propertyType>/_field.gsp` and equivalent for superclasses
        if (propertyAccessor.propertyType) {
            templateResolveOrder << appendPiecesForUri("/_fields", themeFolder, toPropertyNameFormat(propertyAccessor.propertyType), templateName)
            for (propertySuperClass in propertyAccessor.propertyTypeSuperclasses) {
                templateResolveOrder << appendPiecesForUri("/_fields", themeFolder, toPropertyNameFormat(propertySuperClass), templateName)
            }
        }

        // if nothing else is found fall back to a default (even this may not exist for f:input)
        templateResolveOrder << appendPiecesForUri("/_fields", themeFolder, "default", templateName)

        templateResolveOrder
    }

    protected String getWidget(Constrained cp, Class propertyType) {
        if (null == cp) {
            return null
        }
        String widget = null
        if (cp.widget) {
            widget = cp.widget
        } else if (cp.password) {
            widget = 'password'
        } else if (CharSequence.isAssignableFrom(propertyType)) {
            if (cp.url) {
                widget = 'url'
            } else if (cp.creditCard) {
                widget = 'creditCard'
            } else if (cp.email) {
                widget = 'email'
            }
        }
        return widget
    }

    @Memoized
    private String getWidgetPrefixCached() {
        widgetPrefixNotCached
    }

    private String getWidgetPrefixNotCached() {
        return grailsApplication?.config?.getProperty(SETTING_WIDGET_PREFIX, 'widget-')
    }

    @Memoized
    private String getTemplateForCached(String templateProperty) {
        getTemplateForNotCached(templateProperty)
    }

    private String getTemplateForNotCached(String templateProperty) {
        grailsApplication?.config?.getProperty("grails.plugin.fields.$templateProperty", templateProperty) ?: templateProperty
    }

    @Memoized
    private Map<String, String> findTemplateCached(BeanPropertyAccessor propertyAccessor, String controllerNamespace, String controllerName, String actionName, String templateName, String templatesFolder, String themeName) {
        findTemplateNotCached(propertyAccessor, controllerNamespace, controllerName, actionName, templateName, templatesFolder, themeName)
    }

    private Map<String, String> findTemplateNotCached(BeanPropertyAccessor propertyAccessor, String controllerNamespace, String controllerName, String actionName, String templateName, String templatesFolder, String themeName) {
        List<String> candidatePaths
        if (themeName) {
            //if theme is specified, first resolve all theme paths and then all the default paths
            String themeFolder = THEMES_FOLDER + "/" + themeName
            candidatePaths = candidateTemplatePaths(propertyAccessor, controllerNamespace, controllerName, actionName, templateName, templatesFolder, themeFolder)
            candidatePaths = candidatePaths + candidateTemplatePaths(propertyAccessor, controllerNamespace, controllerName, actionName, templateName, templatesFolder, null)
        } else {
            candidatePaths = candidateTemplatePaths(propertyAccessor, controllerNamespace, controllerName, actionName, templateName, templatesFolder, null)
        }

        candidatePaths.findResult { String path ->
            log.debug "looking for template with path $path"
            def source = groovyPageLocator.findTemplateByPath(path)
            if (source) {
                Map template = [path: path]
                GrailsPlugin plugin = pluginManager.allPlugins.find {
                    source.URI.startsWith(it.pluginPath)
                }
                template.plugin = plugin?.name
                log.debug "found template $template.path ${plugin ? "in $template.plugin plugin" : ''}"
                return template
            }
            return null
        }
    }

    private boolean getShouldCache() {
        // If not explicitly specified, templates will be cached
        Boolean cacheDisabled = grailsApplication?.config?.getProperty(DISABLE_LOOKUP_CACHE, Boolean, Boolean.FALSE)
        return !cacheDisabled
    }

    private static String getAssociationPath(BeanPropertyAccessor propertyAccessor) {
        switch (propertyAccessor.domainProperty) {
            case OneToOne: return 'oneToOne'
            case OneToMany: return 'oneToMany'
            case ManyToMany: return 'manyToMany'
            case ManyToOne: return 'manyToOne'
            default: return null
        }
    }

    private static String getControllerNamespace() {
        return grailsWebRequest?.getControllerNamespace()
    }

    private static String getControllerName() {
        grailsWebRequest?.controllerName
    }

    private static String getActionName() {
        grailsWebRequest?.actionName
    }

    private static GrailsWebRequest getGrailsWebRequest() {
        RequestContextHolder.requestAttributes as GrailsWebRequest
    }

}
