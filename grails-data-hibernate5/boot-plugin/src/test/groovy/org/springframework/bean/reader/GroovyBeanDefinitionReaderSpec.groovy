package org.springframework.bean.reader

import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Specification

/**
 * Created by graemerocher on 06/02/14.
 */
class GroovyBeanDefinitionReaderSpec extends Specification{

    protected AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    void setup() {
        MyBean.blah = 'foo'
    }

    void "Test singletons are pre-instantiated with beans added by GroovyBeanDefinitionReader"() {
        when:"The groovy reader is used"
            def beanReader= new GroovyBeanDefinitionReader(context)
            beanReader.beans {
                myBean(MyBean)
            }

            context.refresh()

        then:"The bean is pre instantiated"
            MyBean.blah == 'created'
    }
}
class MyBean implements InitializingBean{
    static String blah = 'foo'

    @Override
    void afterPropertiesSet() throws Exception {
        blah = 'created'
    }
}
