package grails.views

import groovy.transform.CompileStatic

/**
 * A ViewUriResolver is response for response template and view URIs using Grails' conventions.
 *
 *
 * @author Graeme Rocher
 */
@CompileStatic
interface ViewUriResolver {
    /**
     * Resolves a template URI for the given path
     *
     * Example: assert resolveTemplateUri('foo', 'bar') == /foo/_bar.gson
     *
     * @param controllerName The controller name
     * @param path The path to the template
     * @return The template URI
     */
    String resolveTemplateUri(String controllerName, String templateName)

    /**
     * Resolves a template URI for the given path
     *
     * Example: assert resolveTemplateUri('api', 'foo', 'bar') == /api/foo/_bar.gson
     *
     * @param controllerNamespace The controller controllerNamespace
     * @param controllerName The controller name
     * @param path The path to the template
     * @return The template URI
     */
    String resolveTemplateUri(String controllerNamespace, String controllerName, String templateName)
}