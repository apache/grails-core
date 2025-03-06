package grails.views

import groovy.transform.CompileStatic
import org.grails.exceptions.reporting.SourceCodeAware

/**
 * Thrown when a view rendering exception occurs
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class ViewRenderException extends ViewException implements SourceCodeAware {
    final File sourceFile
    final WritableScript view
    final int lineNumber

    ViewRenderException(String message, Throwable cause, WritableScript view) {
        super(message, cause)
        this.sourceFile = view.sourceFile
        this.view = view
        this.lineNumber = findFirstElementCausedByScript()?.lineNumber ?: -1
    }

    @Override
    String getFileName() {
        sourceFile.canonicalPath
    }

    StackTraceElement findFirstElementCausedByScript() {
        def cause = getCause()
        while(cause != null) {
            for(StackTraceElement e in cause.stackTrace) {
                def cls = e.className
                if(cls.contains('$')) {
                    cls = cls.substring(0, cls.indexOf('$'))
                }
                if(cls == view.getClass().name) {
                    return e
                }
            }
            cause = cause.getCause()
        }
        return null
    }
}
