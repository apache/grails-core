import grails.test.app.GraphQLCustomizer

// Place your Spring DSL code here
beans = {

    graphQLPostProcessor(GraphQLCustomizer)
}
