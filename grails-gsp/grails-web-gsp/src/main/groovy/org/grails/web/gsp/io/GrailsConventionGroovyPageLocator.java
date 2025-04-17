/*
 * Copyright 2011-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.gsp.io;

import grails.core.GrailsApplication;
import grails.core.GrailsControllerClass;
import grails.core.support.GrailsApplicationAware;
import grails.util.GrailsNameUtils;
import grails.web.mime.MimeType;
import grails.web.mime.MimeTypeResolver;
import grails.web.pages.GroovyPagesUriService;
import jakarta.servlet.http.HttpServletRequest;
import org.grails.core.artefact.ControllerArtefactHandler;
import org.grails.gsp.io.DefaultGroovyPageLocator;
import org.grails.gsp.io.GroovyPageScriptSource;
import org.grails.io.support.GrailsResourceUtils;
import org.grails.taglib.TemplateVariableBinding;
import org.grails.web.pages.DefaultGroovyPagesUriService;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.util.GrailsApplicationAttributes;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Extended GroovyPageLocator that deals with the details of Grails' conventions
 * for controllers names, view names and template names
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class GrailsConventionGroovyPageLocator extends DefaultGroovyPageLocator implements GrailsApplicationAware {

    private static final char DOT = '.';

    private GroovyPagesUriService uriService = new DefaultGroovyPagesUriService();

    private MimeTypeResolver mimeTypeResolver;
    
    private GrailsApplication grailsApplication;

    @Autowired(required = false)
    public void setMimeTypeResolver(MimeTypeResolver mimeTypeResolver) {
        this.mimeTypeResolver = mimeTypeResolver;
    }

    /**
     * Find a view for a path. For example /foo/bar will search for /WEB-INF/grails-app/views/foo/bar.gsp in production
     * and grails-app/views/foo/bar.gsp at development time
     *
     * @param uri The uri to search
     * @return The script source
     */
    public GroovyPageScriptSource findViewByPath(String uri) {
        return uri == null ? null : findPage(uriService.getAbsoluteViewURI(uri));
    }

    /**
     * <p>Finds a view for the given controller name and view name. For example specifying a controller name of "home" and a view name of "index" will search for
     * /WEB-INF/grails-app/views/home/index.gsp in production and grails-app/views/home/index.gsp in development</p>
     *
     * <p>This method will also detect the presence of the requested response format and try to resolve a more appropriate view. For example in the response format
     * is 'xml' then /WEB-INF/grails-app/views/home/index.xml.gsp will be tried first</p>
     *
     * <p>If the view is not found in the application then a scan is executed that searches through binary and source plugins looking for the first matching view name</p>
     *
     * @param controllerName The controller name
     * @param viewName The view name
     * @return The GroovyPageScriptSource
     */
    public GroovyPageScriptSource findView(String controllerName, String viewName) {
        return findView(controllerName, viewName, lookupRequestFormat());
    }

    public GroovyPageScriptSource findView(String controllerName, String viewName, String format) {
        GroovyPageScriptSource scriptSource = findViewForFormat(controllerName, viewName, format);
        if (scriptSource == null) {
            scriptSource  = findPage(uriService.getViewURI(controllerName, viewName));
        }

        return scriptSource;
    }

    /**
     * Finds a view for the given view name and format, otherwise returns null if it doesn't exist
     * @param controllerName The controller name
     * @param viewName The view name
     * @param format The format
     * @return The script source or null
     */
    public GroovyPageScriptSource findViewForFormat(String controllerName, String viewName, String format) {
        String viewNameWithFormat = getViewNameWithFormat(viewName, format);
        return findPage(uriService.getViewURI(controllerName, viewNameWithFormat));
    }

    public String resolveViewFormat(String viewName) {
        String format = lookupRequestFormat();
        return getViewNameWithFormat(viewName, format);
    }

    private String getViewNameWithFormat(String viewName, String format) {
        if (format == null || format.equals("all")) {
            return viewName;
        }
        return viewName + DOT + format;
    }

    /**
     * <p>Finds a view for the given controller and view name. For example specifying a controller with a class name of HomeController and a view name of "index" will search for
     * /WEB-INF/grails-app/views/home/index.gsp in production and grails-app/views/home/index.gsp in development</p>
     *
     * <p>If the view is not found in the application then a scan is executed that searches through binary and source plugins looking for the first matching view name</p>
     *
     * @param controller The controller
     * @param viewName The view name
     * @return The GroovyPageScriptSource
     */
    public GroovyPageScriptSource findView(Object controller, String viewName) {
        if (controller == null || viewName == null) {
            return null;
        }

        String controllerName = getNameForController(controller);
        String viewNameWithFormat = resolveViewFormat(viewName);

        GroovyPageScriptSource scriptSource = null;
        final String controllerClassName = GrailsNameUtils.getFullClassName( controller.getClass() );
        Object controllerArtefact = grailsApplication != null ? grailsApplication.getArtefact(ControllerArtefactHandler.TYPE, controllerClassName) : null;
        if(controllerArtefact instanceof GrailsControllerClass) {
            GrailsControllerClass gcc = (GrailsControllerClass)controllerArtefact;
            String namespace = gcc.getNamespace();
            if(namespace != null) {
                scriptSource = findPage("/" + namespace + uriService.getViewURI(controllerName, viewNameWithFormat));
                if(scriptSource == null) {
                    scriptSource = findPage("/" + namespace + uriService.getViewURI(controllerName, viewName));
                }
            }
        }
        if(scriptSource == null) {
			scriptSource = findPage(uriService.getViewURI(controllerName, viewNameWithFormat));
        }
        if (scriptSource == null) {
            scriptSource = findPage(uriService.getViewURI(controllerName, viewName));
        }
        if (scriptSource == null && pluginManager != null) {
            String pathToView = pluginManager.getPluginViewsPathForInstance(controller);
            if (pathToView != null) {
                scriptSource = findViewByPath(GrailsResourceUtils.appendPiecesForUri(pathToView,viewName));
            }
        }
        // if all else fails do a full search
        if (scriptSource == null) {
            scriptSource = findView(controllerName, viewName);
        }
        return scriptSource;
    }

    /**
     * Finds a template for the given controller name and template name
     *
     * @param controllerName The controller name
     * @param templateName The view name
     * @return The GroovyPageScriptSource
     */
    public GroovyPageScriptSource findTemplate(String controllerName, String templateName) {
        return findPage(uriService.getTemplateURI(controllerName, templateName));
    }

    /**
     * Finds a template for the given controller name and template name
     *
     * @param controller The controller n
     * @param templateName The view name
     * @return The GroovyPageScriptSource
     */
    public GroovyPageScriptSource findTemplate(Object controller, String templateName) {
        String controllerName = getNameForController(controller);
        GroovyPageScriptSource scriptSource = null;
        final String templateURI = uriService.getTemplateURI(controllerName, templateName);
        final String fullClassName = GrailsNameUtils.getFullClassName(controller.getClass());
        Object controllerArtefact = grailsApplication != null ? grailsApplication.getArtefact(ControllerArtefactHandler.TYPE, fullClassName) : null;
        if(controllerArtefact instanceof GrailsControllerClass) {
            GrailsControllerClass gcc = (GrailsControllerClass)controllerArtefact;
            String namespace = gcc.getNamespace();
            if(namespace != null) {
                scriptSource = findPage("/" + namespace + templateURI);
            }
        }
        if(scriptSource == null) {
            scriptSource = findPage(templateURI);
        }
        return scriptSource;
    }

    /**
     * Finds a view for the given given view name, looking up the controller from the request as necessary
     *
     * @param viewName The view name
     * @return The GroovyPageScriptSource
     */
    public GroovyPageScriptSource findView(String viewName) {
        GrailsWebRequest webRequest = GrailsWebRequest.lookup();
        if (webRequest == null) {
            return findViewByPath(viewName);
        }
        return findView(webRequest.getControllerName(), viewName);
    }
    /**
     * Finds a template for the given given template name, looking up the controller from the request as necessary
     *
     * @param templateName The template name
     * @return The GroovyPageScriptSource
     */
    public GroovyPageScriptSource findTemplate(String templateName) {
        GrailsWebRequest webRequest = GrailsWebRequest.lookup();
        if (webRequest == null) {
            return findTemplateByPath(templateName);
        }
        return findTemplate(webRequest.getControllerName(), templateName);
    }

    /**
     * Finds a template for the given given template name, looking up the controller from the request as necessary
     *
     * @param templateName The template name
     * @return The GroovyPageScriptSource
     */
    public GroovyPageScriptSource findTemplateInBinding(Object controller, String templateName, TemplateVariableBinding binding) {
        return findTemplateInBinding(controller, null, templateName, binding);
    }

    /**
     * Finds a template for the given given template name, looking up the controller from the request as necessary
     *
     * @param pluginName The plugin
     * @param templateName The template name
     * @param binding The binding
     * @return The GroovyPageScriptSource
     */
    public GroovyPageScriptSource findTemplateInBinding(Object controller, String pluginName, String templateName, TemplateVariableBinding binding) {
        if (controller == null) {
            GrailsWebRequest webRequest = GrailsWebRequest.lookup();
            if (webRequest == null) {
                return findPageInBinding(pluginName, uriService.getAbsoluteTemplateURI(templateName, true), binding);
            }
            return findPageInBinding(pluginName, uriService.getTemplateURI(webRequest.getControllerName(), templateName), binding);
        }
        final GrailsControllerClass controllerClass = (GrailsControllerClass)grailsApplication.getArtefact(ControllerArtefactHandler.TYPE, GrailsNameUtils.getFullClassName(controller.getClass()) );

        String templateURI;
        final String ns = controllerClass.getNamespace();
        GroovyPageScriptSource scriptSource = null;
        final String controllerName = controllerClass.getLogicalPropertyName();
        if(ns != null) {
            templateURI = '/' + ns + uriService.getTemplateURI(controllerName, templateName);
            scriptSource = findPageInBinding(pluginName, templateURI, binding);
        }

        if(scriptSource == null) {
            templateURI = uriService.getTemplateURI(controllerName, templateName);
            scriptSource = findPageInBinding(pluginName, templateURI, binding);
        }
        return scriptSource;
    }

    /**
     * Finds a template for the given given template name, looking up the controller from the request as necessary
     *
     * @param templateName The template name
     * @return The GroovyPageScriptSource
     */
    public GroovyPageScriptSource findTemplateInBinding(String templateName, TemplateVariableBinding binding) {
        GrailsWebRequest webRequest = GrailsWebRequest.lookup();
        if (webRequest == null) {
            return findPageInBinding(uriService.getAbsoluteTemplateURI(templateName, true), binding);
        }
        return findPageInBinding(uriService.getTemplateURI(webRequest.getControllerName(), templateName), binding);
    }

    /**
     * Finds a template for the given given template name, looking up the controller from the request as necessary
     *
     * @param pluginName The plugin
     * @param templateName The template name
     * @param binding The binding
     * @return The GroovyPageScriptSource
     */
    public GroovyPageScriptSource findTemplateInBinding(String pluginName, String templateName, TemplateVariableBinding binding) {
        GrailsWebRequest webRequest = GrailsWebRequest.lookup();
        if (webRequest == null) {
            return findPageInBinding(pluginName, uriService.getAbsoluteTemplateURI(templateName, true), binding);
        }
        return findPageInBinding(pluginName, uriService.getTemplateURI(webRequest.getControllerName(), templateName), binding);
    }

    /**
     * Find a template for a path. For example /foo/bar will search for /WEB-INF/grails-app/views/foo/_bar.gsp in production
     * and grails-app/views/foo/_bar.gsp at development time
     *
     * @param uri The uri to search
     * @return The script source
     */
    public GroovyPageScriptSource findTemplateByPath(String uri) {
        return findPage(uriService.getAbsoluteTemplateURI(uri, true));
    }

    protected String lookupRequestFormat() {
        if(mimeTypeResolver != null) {
            MimeType mimeType = mimeTypeResolver.resolveResponseMimeType();

            if(mimeType != null) {
                return mimeType.getExtension();
            }
        }
        else {
            GrailsWebRequest webRequest  =
                GrailsWebRequest.lookup();
            if(webRequest != null) {

                HttpServletRequest request = webRequest.getCurrentRequest();
                Object format = request.getAttribute(GrailsApplicationAttributes.RESPONSE_FORMAT);
                return format == null ? null : format.toString();
            }
        }
        return null;
    }

    protected String getNameForController(Object controller) {
        final Class<?> cls = controller.getClass();
        return GrailsNameUtils.getLogicalPropertyName(GrailsNameUtils.getFullClassName(cls), ControllerArtefactHandler.TYPE);
    }

	@Override
	public void setGrailsApplication(GrailsApplication grailsApplication) {
		this.grailsApplication = grailsApplication;
	}
}
