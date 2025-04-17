package grails.views

import groovy.text.Template
import groovy.transform.CompileStatic

/**
 * Interface for resolving templates
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
interface TemplateResolver {

    /**
     * Resolves the URL to a template using the given path
     *
     * @param path The path
     * @return The URL or null if it cannot be found
     */
    URL resolveTemplate(String path)

    /**
     * Resolves a template class for the path
     *
     * @param path The path
     * @return The Class or null if it cannot be found
     */
    Class<? extends Template> resolveTemplateClass(String path)

    /**
     * Resolves a template class for the path
     *
     * @param packageName the scope to search in (application or plugin name for example)
     * @param path The path
     * @return The Class or null if it cannot be found
     */
    Class<? extends Template> resolveTemplateClass(String packageName, String path)
}