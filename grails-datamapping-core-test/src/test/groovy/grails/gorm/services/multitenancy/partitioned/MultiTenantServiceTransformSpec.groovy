package grails.gorm.services.multitenancy.partitioned

import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MultiTenantServiceTransformSpec extends Specification {

    @Shared @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore(
            [(Settings.SETTING_MULTI_TENANCY_MODE)   : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
             (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver(),
             (Settings.SETTING_DB_CREATE)            : "create-drop"
            ],
            this.getClass().getPackage()
    )

    @Shared def gcl

    void setupSpec() {
        gcl = new GroovyClassLoader()
    }

    void "test service transform applied with @WithoutTenant"() {
        when: "The service transform is applied to an interface it can't implement"
        Class service = gcl.parseClass('''

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.WithoutTenant
import grails.gorm.services.Service
import grails.gorm.multitenancy.CurrentTenant

@Service(Foo)
@CurrentTenant
interface IFooService {

    @WithoutTenant
    Foo saveFoo(Foo foo)

    Integer countFoos()
}

@Entity
class Foo implements MultiTenant<Foo> {
    String title
    Long tenantId
}
''')

        then: "service is an interface"
        service.isInterface()

        when: "implementation of service is generated"
        Class impl = service.classLoader.loadClass("\$IFooServiceImplementation")
        def Foo = service.classLoader.loadClass('Foo')
        def fooService = impl.newInstance()
        fooService.datastore = datastore
        def foo = Foo.newInstance(title: "test", tenantId: 11l)
        fooService.saveFoo(foo)

        then:
        thrown(IllegalStateException)

    }

}
