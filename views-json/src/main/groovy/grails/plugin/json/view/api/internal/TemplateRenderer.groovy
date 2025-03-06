package grails.plugin.json.view.api.internal

import grails.plugin.json.view.api.GrailsJsonViewHelper
import grails.util.GrailsNameUtils
import groovy.transform.CompileStatic

/**
 * Handles the template namespace
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class TemplateRenderer {

    final @Delegate GrailsJsonViewHelper jsonViewHelper

    TemplateRenderer(GrailsJsonViewHelper jsonViewHelper) {
        this.jsonViewHelper = jsonViewHelper
    }

    @Override
    Object invokeMethod(String name, Object args) {
        Object[] argArray = (Object[]) args


        def absolute = name.lastIndexOf('/')
        String modelName = absolute > -1 ? name.substring(absolute+1, name.length()) : name
        int len = argArray.length
        if(len == 1) {
            def val = argArray[0]
            if(val == null) {
                return null
            }
            if(val instanceof Map) {
                return jsonViewHelper.render(template:name, model:val)
            }
            else if(val instanceof Iterable) {
                return jsonViewHelper.render(template:name, var:modelName, collection:val)
            }
            else {
                def model = [(modelName): val]
                model.put(GrailsNameUtils.getPropertyName(val.getClass()), val)
                return jsonViewHelper.render(template:name, model: model)
            }
        }
        else if(len == 2) {
            def var = argArray[0]
            def coll = argArray[1]
            if (var instanceof Iterable) {
                if (coll instanceof Map) {
                    return jsonViewHelper.render(template:name, var:modelName, collection:var, model:coll)
                }
            }
            else if (coll instanceof Iterable) {
                return jsonViewHelper.render(template:name, var:var.toString(), collection:coll)
            }
        }
        else if(len == 3) {
            def var = argArray[0]
            def coll = argArray[1]
            def model = (Map)argArray[2]
            jsonViewHelper.render(template:name, model:model, collection:coll, var:var.toString())
        }

    }
}
