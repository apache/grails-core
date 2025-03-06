package grails.views

import groovy.transform.CompileStatic

/**
 * Settings and constants for the Groovy view infrastructure
 *
 * @author Graeme Rocher
 */
@CompileStatic
interface Views {

    /**
     * The artefact type identifier for Grails
     */
    String TYPE = "views"

    /**
     * The identifier used for model types
     */
    String MODEL = "model"
    /**
     * The identifier used for model types
     */
    String MODEL_TYPES = "modelTypes"

    /**
     * Field used to hold the model types
     */
    String MODEL_TYPES_FIELD = "MODEL_TYPES"
}
