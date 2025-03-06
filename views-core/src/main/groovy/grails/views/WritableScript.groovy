package grails.views

import groovy.transform.CompileStatic

/**
 * Interface for scripts that are writable
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
interface WritableScript extends Writable, WriterProvider {

    /**
     * Obtains the source file
     */
    File getSourceFile()
    /**
     * @param file Sets the source file
     */
    void setSourceFile(File file)

    /**
     * Sets the binding
     *
     * @param binding The binding
     */
    void setBinding(Binding binding)

    /**
     * @return Obtains the binding
     */
    Binding getBinding()

    /**
     * Runs the script and returns the result
     *
     * @return The result
     */
    Object run()
}