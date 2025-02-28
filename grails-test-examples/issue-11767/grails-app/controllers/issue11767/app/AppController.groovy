package issue11767.app

import issue11767.plugin.PluginGroovyMicronautBean
import issue11767.plugin.PluginGroovySpringBean
import issue11767.plugin.PluginJavaMicronautBean
import org.springframework.beans.factory.annotation.Autowired

class AppController {

    PluginGroovySpringBean pluginGroovySpringBean
    @Autowired PluginGroovyMicronautBean pluginGroovyMicronautConfigBean
    @Autowired PluginJavaMicronautBean pluginJavaMicronautConfigBean
    @Autowired AppGroovyMicronautBean appGroovyMicronautConfigBean

    def index() {
        render """
            <pre>
                Plugin Groovy Spring Bean - my.value1: ${pluginGroovySpringBean.value1}
                Plugin Groovy Spring Bean - my.value2: ${pluginGroovySpringBean.value2}
                <br>
                Plugin Groovy Micronaut Bean - my.value1: ${pluginGroovyMicronautConfigBean.value1}
                Plugin Groovy Micronaut Bean - my.value2: ${pluginGroovyMicronautConfigBean.value2}
                <br>
                Plugin Java Micronaut Bean - my.value1: ${pluginJavaMicronautConfigBean.value1}
                Plugin Java Micronaut Bean - my.value2: ${pluginJavaMicronautConfigBean.value2}
                <br>
                App Groovy Micronaut Bean - my.value1: ${appGroovyMicronautConfigBean.value1}
                App Groovy Micronaut Bean - my.value2: ${appGroovyMicronautConfigBean.value2}
            </pre>
        """.stripIndent()
    }
}