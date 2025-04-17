package grails.plugin.json.view.api.jsonapi

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.PersistentProperty

@CompileStatic
class DefaultJsonApiIdRenderer implements JsonApiIdRenderStrategy {

    @Override
    Object render(Object object, PersistentProperty property) {
        final String propertyName = property.name
        if (object && object.hasProperty(propertyName)) {
            return object.getAt(propertyName).toString()
        }
    }
}
