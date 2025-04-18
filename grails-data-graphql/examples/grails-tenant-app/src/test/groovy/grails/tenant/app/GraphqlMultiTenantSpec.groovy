package grails.tenant.app


import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.web.SessionTenantResolver
import org.grails.gorm.graphql.plugin.GormGraphqlGrailsPlugin
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.spring.beans.factory.InstanceFactoryBean
import org.grails.testing.GrailsUnitTest
import spock.lang.Specification

class GraphqlMultiTenantSpec extends Specification implements GrailsUnitTest {

    void "test GraphQl with multi tenancy mode schema and session tenant resolver"() {

        given:
        HibernateDatastore datastore = new HibernateDatastore(
                DatastoreUtils.createPropertyResolver(
                        [(Settings.SETTING_MULTI_TENANCY_MODE)          : MultiTenancySettings.MultiTenancyMode.SCHEMA,
                         (Settings.SETTING_MULTI_TENANT_RESOLVER_CLASS) : SessionTenantResolver.name,
                         (Settings.SETTING_DB_CREATE)                   : 'create-drop']),
                [User] as Class[])

        defineBeans {
            hibernateDatastore(InstanceFactoryBean, datastore, HibernateDatastore)
            grailsDomainClassMappingContext(hibernateDatastore: "getMappingContext")
        }

        GormGraphqlGrailsPlugin graphqlGrailsPlugin = new GormGraphqlGrailsPlugin()
        graphqlGrailsPlugin.grailsApplication = grailsApplication
        this.defineBeans(graphqlGrailsPlugin)

        expect:
        grailsApplication.mainContext.containsBean("graphQL")

    }

}
