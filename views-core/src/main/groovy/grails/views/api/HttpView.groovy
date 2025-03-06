package grails.views.api

import grails.views.api.http.Parameters
import grails.views.api.http.Request
import grails.views.api.http.Response
import grails.views.api.internal.EmptyParameters
import groovy.transform.CompileStatic

/**
 * A view that is rendered in the context of an HTTP request
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
trait HttpView extends View {

    /**
     * @return The page object
     */
    Response response

    /**
     * @return The request object
     */
    Request request

    /**
     * @return The same as {@link #getResponse()}
     */
    Request getPage() {
        request
    }

    /**
     * The parameters
     */
    Parameters params = new EmptyParameters()
}