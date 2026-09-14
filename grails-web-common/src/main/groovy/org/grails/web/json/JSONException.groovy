/*
Public Domain.
*/

package org.grails.web.json

import groovy.transform.CompileStatic

/**
 * The JSONException is thrown by the JSON.org classes then things are amiss.
 * @author JSON.org
 * @version 2
 */
@CompileStatic
class JSONException extends RuntimeException {

    private static final long serialVersionUID = -4009964545824827919L
    private Throwable cause

    /**
     * Constructs a JSONException with an explanatory message.
     * @param message Detail about the reason for the exception.
     */
    JSONException(String message) {
        super(message)
    }

    JSONException(Throwable t) {
        super(t.getMessage())
        this.@cause = t
    }

    @Override
    Throwable getCause() {
        return this.@cause
    }

}
