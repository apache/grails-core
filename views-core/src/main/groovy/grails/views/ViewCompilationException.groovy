package grails.views

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.grails.exceptions.reporting.SourceCodeAware

/**
 * Exception when views fail to compile
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class ViewCompilationException extends ViewException implements SourceCodeAware {

    final String fileName

    ViewCompilationException(CompilationFailedException cause, String fileName) {
        super(cause)
        this.fileName = fileName
    }

    @Override
    int getLineNumber() {
        def cause = getCause()
        ASTNode node = ((CompilationFailedException) cause).getNode()

        if(node != null) {
            return node.getLineNumber()
        }
        else if(cause instanceof MultipleCompilationErrorsException) {
            MultipleCompilationErrorsException mce = (MultipleCompilationErrorsException)cause
            def message = mce.errorCollector.errors[0]
            if(message instanceof SyntaxErrorMessage) {
                return ((SyntaxErrorMessage)message).getCause().line
            }
        }
        return -1
    }
}
