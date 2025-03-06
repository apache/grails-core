package grails.views

import grails.util.BuildSettings
import grails.util.Environment
import groovy.transform.CompileStatic

/**
 * Environment helper methods
 *
 * @author Graeme Rocher
 */
@CompileStatic
class ViewsEnvironment {
    private static final boolean DEVELOPMENT_MODE = Environment.getCurrent() == Environment.DEVELOPMENT && BuildSettings.GRAILS_APP_DIR_PRESENT

    /**
     * @return Whether development mode is enabled
     */
    static boolean isDevelopmentMode() {
        DEVELOPMENT_MODE
    }
}
