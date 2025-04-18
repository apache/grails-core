package org.grails.gorm.graphql.binding.manager

import org.grails.gorm.graphql.binding.GraphQLDataBinder
import spock.lang.Specification

class GraphQLDataBinderManagerSpec extends Specification {

    DefaultGraphQLDataBinderManager manager

    void setup() {
        manager = new DefaultGraphQLDataBinderManager()
    }

    GraphQLDataBinder newBinder() {
        new GraphQLDataBinder() {
            @Override
            void bind(Object object, Map data) { }
        }
    }

    void "test Object binder exists"() {
        expect:
        manager.getDataBinder(String) != null
        manager.getDataBinder(Object) != null
    }

    void "test binders add order takes precedence"() {
        given:
        GraphQLDataBinder binder = newBinder()
        manager.registerDataBinder(String, binder)

        expect:
        manager.getDataBinder(Object) != binder
        manager.getDataBinder(String) == binder
    }

    void "test binder for exact class takes precedence over parent classes"() {
        given:
        GraphQLDataBinder serializable = newBinder()
        GraphQLDataBinder string = newBinder()
        manager.registerDataBinder(String, string)
        manager.registerDataBinder(Serializable, serializable)

        expect:
        manager.getDataBinder(String) == string
        manager.getDataBinder(Number) == serializable
    }

    void "test binder for exact class takes precedence over parent classes reverse order"() {
        given:
        GraphQLDataBinder serializable = newBinder()
        GraphQLDataBinder string = newBinder()
        manager.registerDataBinder(Serializable, serializable)
        manager.registerDataBinder(String, string)

        expect:
        manager.getDataBinder(String) == string
        manager.getDataBinder(Number) == serializable
    }

    void "test the first parent class is chosen"() {
        given:
        GraphQLDataBinder serializable = newBinder()
        GraphQLDataBinder comparable = newBinder()
        manager.registerDataBinder(Serializable, serializable)
        manager.registerDataBinder(Comparable, comparable)

        expect:
        manager.getDataBinder(Long) == comparable
    }

    void "test the first parent class is chosen reverse order"() {
        given:
        GraphQLDataBinder serializable = newBinder()
        GraphQLDataBinder comparable = newBinder()
        manager.registerDataBinder(Comparable, comparable)
        manager.registerDataBinder(Serializable, serializable)

        expect:
        manager.getDataBinder(Long) == serializable
    }
}
