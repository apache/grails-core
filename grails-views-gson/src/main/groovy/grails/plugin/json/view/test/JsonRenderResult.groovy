package grails.plugin.json.view.test

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import org.springframework.http.HttpStatus

/**
 * A result object returned by {@link JsonViewTest}
 *
 * @author Graeme Rocher
 * @since 1.1
 */
@CompileStatic
@Canonical
class JsonRenderResult {
    /**
     * The status
     */
    HttpStatus status = HttpStatus.OK
    /**
     * The HTTP response message
     */
    String message = HttpStatus.OK.getReasonPhrase()
    /**
     * The content type
     */
    String contentType

    /**
     * The headers
     */
    Map<String, String> headers = [:]
    /**
     * The JSON result
     */
    Object json
    /**
     * The raw JSON text
     */
    String jsonText
}
