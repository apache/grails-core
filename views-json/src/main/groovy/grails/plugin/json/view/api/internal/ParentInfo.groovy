package grails.plugin.json.view.api.internal

import grails.views.GrailsViewTemplate
import groovy.transform.CompileStatic

@CompileStatic
class ParentInfo {
    /**
     * The parent template if any
     */
    GrailsViewTemplate parentTemplate

    /**
     * The parent model, if any
     */
    Map parentModel
}
