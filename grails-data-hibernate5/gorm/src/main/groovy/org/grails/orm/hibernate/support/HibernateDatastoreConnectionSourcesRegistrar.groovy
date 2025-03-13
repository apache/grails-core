package org.grails.orm.hibernate.support

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.bootstrap.support.InstanceFactoryBean
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.grailsversion.GrailsVersion
import org.hibernate.SessionFactory
import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.config.ConstructorArgumentValues
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.core.Ordered
import org.springframework.transaction.PlatformTransactionManager

import javax.sql.DataSource


/**
 * A factory bean that looks up a datastore by connection name
 *
 * @author Graeme Rocher
 * @since 6.0.6
 */
@CompileStatic
class HibernateDatastoreConnectionSourcesRegistrar implements BeanDefinitionRegistryPostProcessor, Ordered {

    final Iterable<String> dataSourceNames

    HibernateDatastoreConnectionSourcesRegistrar(Iterable<String> dataSourceNames) {
        this.dataSourceNames = dataSourceNames
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        for(String dataSourceName in dataSourceNames) {
            boolean isDefault = dataSourceName == ConnectionSource.DEFAULT || dataSourceName == Settings.SETTING_DATASOURCE
            boolean shouldConfigureDataSourceBean = GrailsVersion.isAtLeastMajorMinor(3,3)
            String dataSourceBeanName = isDefault ? Settings.SETTING_DATASOURCE : "${Settings.SETTING_DATASOURCE}_$dataSourceName"

            if(!registry.containsBeanDefinition(dataSourceBeanName) && shouldConfigureDataSourceBean) {
                def dataSourceBean = new RootBeanDefinition()
                dataSourceBean.setTargetType(DataSource)
                dataSourceBean.setBeanClass(InstanceFactoryBean)
                def args = new ConstructorArgumentValues()
                String spel = "#{dataSourceConnectionSourceFactory.create('$dataSourceName', environment).source}".toString()
                args.addGenericArgumentValue(spel)
                dataSourceBean.setConstructorArgumentValues(
                        args
                )
                registry.registerBeanDefinition(dataSourceBeanName, dataSourceBean)
            }

            if(!isDefault) {
                String suffix = '_' + dataSourceName
                String sessionFactoryName = "sessionFactory$suffix"
                String transactionManagerBeanName = "transactionManager$suffix"

                def sessionFactoryBean = new RootBeanDefinition()
                sessionFactoryBean.setTargetType(SessionFactory)
                sessionFactoryBean.setBeanClass(InstanceFactoryBean)
                def args = new ConstructorArgumentValues()
                args.addGenericArgumentValue("#{hibernateDatastore.getDatastoreForConnection('$dataSourceName').sessionFactory}".toString())
                sessionFactoryBean.setConstructorArgumentValues(
                        args
                )
                registry.registerBeanDefinition(
                        sessionFactoryName,
                        sessionFactoryBean
                )

                def transactionManagerBean = new RootBeanDefinition()
                transactionManagerBean.setTargetType(PlatformTransactionManager)
                transactionManagerBean.setBeanClass(InstanceFactoryBean)
                def txMgrArgs = new ConstructorArgumentValues()
                txMgrArgs.addGenericArgumentValue("#{hibernateDatastore.getDatastoreForConnection('$dataSourceName').transactionManager}".toString())
                transactionManagerBean.setConstructorArgumentValues(
                        txMgrArgs
                )
                registry.registerBeanDefinition(
                        transactionManagerBeanName,
                        transactionManagerBean
                )
            }
        }
    }

    @Override
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }

    @Override
    int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100
    }
}
