package grails.test.app

// tag::wholeFile[]
import org.grails.gorm.graphql.plugin.binding.GrailsGraphQLDataBinder

class RoleDataBinder extends GrailsGraphQLDataBinder {

    @Override
    void bind(Object object, Map data) {
        data.put('authority', data.remove('name'))
        super.bind(object, data)
    }
}
// end::wholeFile[]
