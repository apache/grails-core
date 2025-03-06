package grails.views.resolve

import grails.views.ViewUriResolver
import groovy.transform.CompileStatic
import org.grails.buffer.FastStringWriter

/**
 * Generic implementation for resolving views
 *
 * @author Graeme Rocher
 */
@CompileStatic
class GenericViewUriResolver implements ViewUriResolver {
    private static final String SLASH_STR = "/"
    private static final char SLASH = '/'
    private static final char UNDERSCORE = '_'
    private static final String BLANK = ""
    private static final String SLASH_UNDR = "/_";

    final String extension

    GenericViewUriResolver(String extension) {
        this.extension = extension
    }

    String resolveTemplateUri(String controllerName, String templateName, boolean includeExtension = true) {
        return resolveTemplateUri(null, controllerName, templateName, includeExtension)
    }

    String resolveTemplateUri(String controllerNamespace, String controllerName, String templateName, boolean includeExtension = true) {
        if (templateName.startsWith(SLASH_STR)) {
            return getAbsoluteTemplateURI(templateName, includeExtension)
        }

        def buf = new FastStringWriter()
        String pathToTemplate = BLANK

        int lastSlash = templateName.lastIndexOf(SLASH_STR)
        if (lastSlash > -1) {
            pathToTemplate = templateName.substring(0, lastSlash + 1)
            templateName = templateName.substring(lastSlash + 1)
        }
        if(controllerNamespace != null) {
            buf << SLASH << controllerNamespace
        }
        if(controllerName != null) {
            if(controllerName.startsWith(SLASH_STR)) {
                buf << controllerName
            }
            else {
                buf << SLASH << controllerName
            }
        }
        buf << SLASH << pathToTemplate << UNDERSCORE << templateName
        if(includeExtension) {
            return buf.append(extension).toString()
        }
        else {
            return buf.toString()
        }
    }


    /**
     * Used to resolve template names that are not relative to a controller.
     *
     * @param templateName The template name normally beginning with /
     * @return The template URI
     */
    protected String getAbsoluteTemplateURI(String templateName, boolean includeExtension = true) {
        def buf = new FastStringWriter()
        String tmp = templateName.substring(1,templateName.length())
        if (tmp.indexOf(SLASH_STR) > -1) {
            buf << SLASH
            int i = tmp.lastIndexOf(SLASH_STR)
            buf << tmp.substring(0, i) << SLASH_UNDR
            buf << tmp.substring(i + 1,tmp.length())
        }
        else {
            buf << SLASH_UNDR << templateName.substring(1,templateName.length())
        }
        if(includeExtension) {
            String uri = buf.append(extension).toString()
            buf.close()
            return uri
        } else {
            return buf.toString()
        }

    }
}
