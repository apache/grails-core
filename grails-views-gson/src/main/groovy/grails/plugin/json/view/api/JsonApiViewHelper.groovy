package grails.plugin.json.view.api

import grails.plugin.json.builder.JsonOutput
import groovy.transform.CompileStatic

/**
 * @author Colin Harrington
 */
@CompileStatic
interface JsonApiViewHelper {

    JsonOutput.JsonWritable render(Object object)

    JsonOutput.JsonWritable render(Object object, Map arguments)
}
