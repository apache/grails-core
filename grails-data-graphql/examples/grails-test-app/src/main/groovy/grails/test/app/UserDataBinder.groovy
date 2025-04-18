package grails.test.app

import org.grails.gorm.graphql.plugin.binding.GrailsGraphQLDataBinder
import groovy.transform.CompileStatic

@CompileStatic
class UserDataBinder extends GrailsGraphQLDataBinder {

    @Override
    void bind(Object object, Map data) {
        //These properties are guaranteed to be here because they are
        //created with nullable(false)
        Integer first = (Integer)data.remove('firstNumber')
        Integer second = (Integer)data.remove('secondNumber')
        if (first != null && second != null) {
            data.put('addedNumbers', first + second)
        }
        super.bind(object, data)
    }
}
