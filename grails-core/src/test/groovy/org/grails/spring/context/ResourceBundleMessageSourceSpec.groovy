package org.grails.spring.context

import org.grails.spring.context.support.ReloadableResourceBundleMessageSource
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import spock.lang.Specification

class ResourceBundleMessageSourceSpec extends Specification {
    Resource messages
    Resource other 
    void setup(){
        messages = new TestResource('messages.properties','''\
            foo=bar
            shared.message=Messages Message
        '''.stripIndent().getBytes('UTF-8'))
         
        other = new TestResource('other.properties','''\
            bar=foo
            shared.message=Other Message
        '''.stripIndent().getBytes('UTF-8'))
    }
    
    void 'Check method to retrieve bundle codes per messagebundle'(){
        given:
            def messageSource = new ReloadableResourceBundleMessageSource(
                resourceLoader: new DefaultResourceLoader(){
                    Resource getResourceByPath(String path){
                        path.startsWith('messages') ? messages:other
                    }
                }
            )
            messageSource.setBasenames('messages','other')
            def locale = Locale.default
        expect:
            messageSource.getBundleCodes(locale,'messages') == (['foo'] as Set)
            messageSource.getBundleCodes(locale,'other') == (['bar'] as Set)
            messageSource.getBundleCodes(locale,'messages','other') == (['foo','bar'] as Set)
    }

    void 'Check method to verify ResourceBundle ordering prioritizes application over e.g. plugin messages'(){
        given:
        def messageSource = new ReloadableResourceBundleMessageSource(
          resourceLoader: new DefaultResourceLoader(){
              Resource getResourceByPath(String path){
                  path.startsWith('messages') ? messages:other
              }
          }
        )
        messageSource.setBasenames('other', 'messages')
        def locale = Locale.default
        expect: "other messages override plugin messages"
        messageSource.getMessage('shared.message', null, locale) == 'Other Message'
        messageSource.getMessage('foo', null, locale) == 'bar'
    }
    
    class TestResource extends ByteArrayResource{
        String filename

        TestResource(String filename, byte[] byteArray) {
            super(byteArray)
            this.filename=filename
        }
        
    }
    
}
