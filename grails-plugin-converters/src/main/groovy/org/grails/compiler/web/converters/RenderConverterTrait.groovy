package org.grails.compiler.web.converters

import grails.artefact.Enhances
import groovy.transform.Generated
import org.grails.web.converters.Converter
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.web.context.request.RequestContextHolder

import jakarta.servlet.http.HttpServletResponse

@Enhances(["Controller", "Interceptor"])
trait RenderConverterTrait {

    /**
     * Render the given converter to the response
     *
     * @param converter The converter to render
     */
    @Generated
    void render(Converter<?> converter) {
        GrailsWebRequest webRequest = (GrailsWebRequest)RequestContextHolder.currentRequestAttributes()
        HttpServletResponse response = webRequest.currentResponse
        webRequest.renderView = false
        converter.render response
    }
}
