package issue11767.plugin

import groovy.transform.CompileStatic
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@CompileStatic
@Configuration
@ConfigurationProperties(prefix = 'my')
class PluginGroovySpringBean {
    String value1
    String value2
}
