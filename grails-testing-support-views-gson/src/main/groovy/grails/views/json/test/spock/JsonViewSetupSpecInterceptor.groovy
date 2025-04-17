package grails.views.json.test.spock

import grails.core.GrailsApplication
import grails.plugin.json.view.JsonViewGrailsPlugin
import grails.views.json.test.JsonViewUnitTest
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.mapping.UrlMappingsHolderFactoryBean
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.springframework.web.servlet.i18n.SessionLocaleResolver

@CompileStatic
class JsonViewSetupSpecInterceptor implements IMethodInterceptor {

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        JsonViewUnitTest test = (JsonViewUnitTest)invocation.instance
        setup(test)
        invocation.proceed()
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected void setup(JsonViewUnitTest test) {

        GrailsApplication grailsApp = test.grailsApplication
        def config = grailsApp.config

        test.defineBeans {
            grailsLinkGenerator(DefaultLinkGenerator, config?.grails?.serverURL ?: "http://localhost:8080")
            localeResolver(SessionLocaleResolver)
            grailsUrlMappingsHolder(UrlMappingsHolderFactoryBean) {
                grailsApplication = grailsApp
            }
            grailsDomainClassMappingContext(KeyValueMappingContext, 'test') {
                canInitializeEntities = true
            }
        }
        JsonViewGrailsPlugin plugin = new JsonViewGrailsPlugin()
        plugin.setApplicationContext(grailsApp.mainContext)
        test.defineBeans(plugin)
    }
}
