package grails.plugin.json.view.api

import grails.plugin.json.builder.JsonOutput
import grails.plugin.json.builder.StreamingJsonBuilder
import groovy.transform.CompileStatic

/**
 * @author Graeme Rocher
 * @since 1.1.0
 */
@CompileStatic
interface HalViewHelper {

    /**
     * Same as {@link GrailsJsonViewHelper#render(java.lang.Object, java.util.Map, groovy.lang.Closure)} but renders HAL links too
     */
    JsonOutput.JsonWritable render(Object object, Map arguments, @DelegatesTo(StreamingJsonBuilder.StreamingJsonDelegate) Closure customizer)

    /**
     * Same as {@link GrailsJsonViewHelper#render(java.lang.Object, java.util.Map)} but renders HAL links too
     */
    JsonOutput.JsonWritable render(Object object, Map arguments)
    /**
     * Same as {@link GrailsJsonViewHelper#render(java.lang.Object, java.util.Map, groovy.lang.Closure)} but renders HAL links too
     */
    JsonOutput.JsonWritable render(Object object, @DelegatesTo(StreamingJsonBuilder.StreamingJsonDelegate) Closure customizer )

    /**
     * Same as {@link GrailsJsonViewHelper#render(java.lang.Object)} but renders HAL links too
     */
    JsonOutput.JsonWritable render(Object object)

    /**
     * Same as {@link GrailsJsonViewHelper#inline(java.lang.Object, java.util.Map, groovy.lang.Closure)} but renders HAL links too
     */
    void inline(Object object, Map arguments, @DelegatesTo(StreamingJsonBuilder.StreamingJsonDelegate) Closure customizer)

    /**
     * Same as {@link GrailsJsonViewHelper#inline(java.lang.Object, java.util.Map)} but renders HAL links too
     */
    void inline(Object object, Map arguments)
    /**
     * Same as {@link GrailsJsonViewHelper#inline(java.lang.Object, java.util.Map, groovy.lang.Closure)} but renders HAL links too
     */
    void inline(Object object, @DelegatesTo(StreamingJsonBuilder.StreamingJsonDelegate) Closure customizer )

    /**
     * Same as {@link GrailsJsonViewHelper#inline(java.lang.Object)} but renders HAL links too
     */
    void inline(Object object)
    /**
     * @param name Sets the HAL response type
     */
    void type(String name)
    /**
     * Define the hal links
     *
     * @param callable The closure
     */
    void links(Closure callable)

    /**
     * Creates HAL links for the given object
     *
     * @param object The object to create links for
     */
    void links(Object object, String contentType)

    /**
     * Creates HAL links for the given object
     *
     * @param object The object to create links for
     */
    void links(Object object)


    /**
     * Creates HAL links for the given model
     *
     * @param object The model to create links for
     */
    void links(Map model)

    /**
     * Creates HAL links for the given model and pagination object
     *
     * @param object The model to create links for
     */
    void links(Map model, Object paginationObject, Number total)

    /**
     * Pagination support which outputs hal links to the resulting pages
     *
     * @param object The object to create links for
     * @param total The total number of objects to be paginated
     */
    void paginate(Object object, Integer total)

    /**
     * Pagination support which outputs hal links to the resulting pages
     *
     * @param object The object to create links for
     * @param total The total number of objects to be paginated
     * @param offset The numerical offset where the page starts (defaults to 0)
     */
    void paginate(Object object, Integer total, Integer offset)

    /**
     * Pagination support which outputs hal links to the resulting pages
     *
     * @param object The object to create links for
     * @param total The total number of objects to be paginated
     * @param offset The numerical offset where the page starts (defaults to 0)
     * @param max The maximum number of objects to be shown (defaults to 10)
     */
    void paginate(Object object, Integer total, Integer offset, Integer max)

    /**
     * Pagination support which outputs hal links to the resulting pages
     *
     * @param object The object to create links for
     * @param total The total number of objects to be paginated
     * @param offset The numerical offset where the page starts (defaults to 0)
     * @param max The maximum number of objects to be shown (defaults to 10)
     * @param sort The field to sort on (defaults to null)
     */
    void paginate(Object object, Integer total, Integer offset, Integer max,  String sort)

    /**
     * Pagination support which outputs hal links to the resulting pages
     *
     * @param object The object to create links for
     * @param total The total number of objects to be paginated
     * @param offset The numerical offset where the page starts (defaults to 0)
     * @param max The maximum number of objects to be shown (defaults to 10)
     * @param sort The field to sort on (defaults to null)
     * @param order The order in which the results are to be sorted eg: DESC or ASC
     */
    void paginate(Object object, Integer total, Integer offset, Integer max,  String sort, String order)


    /**
     * Render embedded links for the associations of the given object (if any)
     *
     * @param object The domain object
     */
    void embedded(Object object)

    /**
     * Render embedded links for the given model
     *
     * @param model The embedded model
     */
    void embedded(Map model)
    /**
     * Render embedded links for the associations of the given object (if any)
     *
     * @param object The domain object
     * @param arguments Arguments to the embedded method
     */
    void embedded(Object object, Map arguments)

    /**
     * Outputs a HAL embedded entry for the given closure
     *
     * @param callable The callable
     */
    void embedded(@DelegatesTo(StreamingJsonBuilder.StreamingJsonDelegate) Closure callable)


    /**
     * Outputs a HAL embedded entry for the content type and closure
     *
     * @param contentType The content type
     * @param callable The callable
     */
    void embedded(String contentType, @DelegatesTo(StreamingJsonBuilder.StreamingJsonDelegate) Closure callable)

    void setDelegate(StreamingJsonBuilder.StreamingJsonDelegate jsonDelegate)
}