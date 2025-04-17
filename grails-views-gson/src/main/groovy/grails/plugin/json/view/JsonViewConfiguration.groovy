package grails.plugin.json.view

import grails.views.GenericViewConfiguration
import grails.web.mime.MimeType
import groovy.transform.CompileStatic
import org.springframework.beans.BeanUtils
import org.springframework.boot.context.properties.ConfigurationProperties

import java.beans.PropertyDescriptor

/**
 * Default configuration for JSON views
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@ConfigurationProperties('grails.views.json')
class JsonViewConfiguration implements GenericViewConfiguration {

    public static final String MODULE_NAME = "json"

    List<String> mimeTypes = [MimeType.JSON.name, MimeType.HAL_JSON.name]

    JsonViewGeneratorConfiguration generator = new JsonViewGeneratorConfiguration()

    JsonViewConfiguration() {
        setExtension(JsonViewWritableScript.EXTENSION)
        setCompileStatic(true)
        setBaseTemplateClass(JsonViewWritableScript)
    }

    @Override
    String getViewModuleName() {
         MODULE_NAME
    }

    PropertyDescriptor[] findViewConfigPropertyDescriptor() {
        BeanUtils.getPropertyDescriptors(GenericViewConfiguration)
    }

}
