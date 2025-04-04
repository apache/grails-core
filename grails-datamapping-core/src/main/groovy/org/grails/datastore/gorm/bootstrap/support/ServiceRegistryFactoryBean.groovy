package org.grails.datastore.gorm.bootstrap.support

import grails.gorm.services.Service
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.reflect.NameUtils
import org.grails.datastore.mapping.services.ServiceRegistry
import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ConfigurableApplicationContext

import java.beans.Introspector

/**
 * @author Graeme Rocher
 * @since
 */
@CompileStatic
class ServiceRegistryFactoryBean implements FactoryBean<ServiceRegistry>, BeanFactoryAware {
    final Datastore datastore

    ServiceRegistryFactoryBean(Datastore datastore) {
        this.datastore = datastore
    }

    @Override
    ServiceRegistry getObject() throws Exception {
        return datastore
    }

    @Override
    Class<?> getObjectType() {
        return ServiceRegistry
    }

    @Override
    boolean isSingleton() {
        return true
    }

    @Override
    void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if(beanFactory instanceof ConfigurableListableBeanFactory) {
            AutowireCapableBeanFactory autowireCapableBeanFactory = beanFactory instanceof AutowireCapableBeanFactory ? (AutowireCapableBeanFactory)beanFactory : null
            ConfigurableListableBeanFactory configurableListableBeanFactory = (ConfigurableListableBeanFactory)beanFactory

            for(org.grails.datastore.mapping.services.Service service in datastore.services) {
                def serviceClass = service.getClass()
                Service ann = serviceClass.getAnnotation(Service)
                String serviceName = ann?.name()
                if(serviceName == null) {
                    serviceName = Introspector.decapitalize(serviceClass.simpleName)
                }

                if(!configurableListableBeanFactory.containsBean(serviceName)) {
                    autowireCapableBeanFactory?.autowireBean(service)
                    service.setDatastore(datastore)
                    configurableListableBeanFactory.registerSingleton(serviceName, service)
                }
                else {
                    String root = Introspector.decapitalize( datastore.getClass().simpleName - 'Datastore' )
                    serviceName = "${root}${NameUtils.capitalize(serviceName)}"
                    if(!configurableListableBeanFactory.containsBean(serviceName)) {
                        autowireCapableBeanFactory?.autowireBean(service)
                        service.setDatastore(datastore)
                        configurableListableBeanFactory.registerSingleton(serviceName, service)
                    }
                }
            }
        }
    }

}
