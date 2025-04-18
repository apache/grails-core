package demo

import groovy.transform.CompileStatic
import org.grails.gorm.graphql.binding.manager.GraphQLDataBinderManager
import org.grails.gorm.graphql.plugin.GraphQLPostProcessor

@CompileStatic
class GraphQLCustomizer extends GraphQLPostProcessor {

    @Override
    void doWith(GraphQLDataBinderManager dataBinderManager) {
        dataBinderManager.registerDataBinder(Author, new AuthorDataBinder())
    }
}
