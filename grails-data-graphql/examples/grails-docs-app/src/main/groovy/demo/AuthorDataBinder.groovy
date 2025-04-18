package demo
// tag::wholeFile[]
import groovy.transform.CompileStatic
import org.grails.gorm.graphql.plugin.binding.GrailsGraphQLDataBinder

@CompileStatic
class AuthorDataBinder extends GrailsGraphQLDataBinder {

    @Override
    void bind(Object object, Map data) {
        List<Map> books = (List)data.remove('books')
        for (Map entry: books) {
            data.put("books[${entry.key}]".toString(), entry.value)
        }
        super.bind(object, data)
    }
}
// end::wholeFile[]