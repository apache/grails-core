package grails.plugin.json.view

import grails.plugin.json.view.api.GrailsJsonViewHelper
import grails.plugin.json.view.api.internal.TemplateRenderer
import spock.lang.Specification

/**
 * Created by graemerocher on 13/04/16.
 */
class TemplateRendererSpec extends Specification {

    void "Test template renderer calls the correct render method"() {
        given:"A template renderer"

        def mockViewHelper = Mock(GrailsJsonViewHelper)
        def tmpl = new TemplateRenderer(mockViewHelper)

        def o = new Object()
        when:
        tmpl.foo(o)

        then:
        1 * mockViewHelper.render([template:"foo", model:[foo:o, object:o]])

        when:
        tmpl."/foo/foo"(o)

        then:
        1 * mockViewHelper.render([template:"/foo/foo", model:[foo:o, object: o]])

        when:
        tmpl."/foo/foo"(null)

        then:
        0 * mockViewHelper.render([template:"/foo/foo", model:[foo:o]])

        when:
        tmpl.foo(null)

        then:
        0 * mockViewHelper.render([template:"foo", model:[foo:null]])

        when:
        tmpl.foo([o])

        then:
        1 * mockViewHelper.render([template:"foo", var:'foo', collection:[o]])

        when:
        tmpl."/foo/foo"([o])

        then:
        1 * mockViewHelper.render([template:"/foo/foo", var:'foo', collection:[o]])

        when:
        tmpl."/foo/foo"("bar", [o])

        then:
        1 * mockViewHelper.render([template:"/foo/foo", var:'bar', collection:[o]])

        when:
        tmpl."/foo/foo"("bar", [o], [foo:null])

        then:
        1 * mockViewHelper.render([template:"/foo/foo", model:[foo:null], collection:[o], var:'bar'])

        when:
        tmpl."/foo/foo"([o], [foo:null])

        then:
        1 * mockViewHelper.render([template:"/foo/foo", model:[foo:null], collection:[o], var:'foo'])
    }
}
