package grails.plugin.json.view

import grails.plugin.json.builder.JsonOutput
import grails.plugin.json.builder.StreamingJsonBuilder
import grails.plugin.json.view.api.JsonView
import grails.plugin.json.view.api.internal.DefaultGrailsJsonViewHelper
import grails.plugin.json.view.api.internal.ParentInfo
import grails.util.GrailsNameUtils
import grails.views.AbstractWritableScript
import grails.views.GrailsViewTemplate
import grails.views.api.GrailsView
import groovy.transform.CompileStatic
import org.grails.buffer.FastStringWriter

@CompileStatic
abstract class JsonViewWritableScript extends AbstractWritableScript implements JsonView {

    public static final String EXTENSION = "gson"
    public static final String TYPE = "view.gson"

    Object root
    boolean inline = false

    @Override
    Writer doWrite(Writer out) throws IOException {

        if(!prettyPrint) {
            this.json = new StreamingJsonBuilder(out, this.generator)
            run()
            return out
        }
        else {
            def writer = new FastStringWriter()
            setOut(writer)
            this.json = new StreamingJsonBuilder(writer, this.generator)
            run()
            def prettyOutput = JsonOutput.prettyPrint(writer.toString())
            out.write(prettyOutput)
            return out
        }
    }


    /**
     * TODO: When Groovy 2.4.5 go back to JsonBuilder from groovy-json
     *
     * @param callable
     * @return
     */
    StreamingJsonBuilder json(@DelegatesTo(value = StreamingJsonBuilder.StreamingJsonDelegate, strategy = Closure.DELEGATE_FIRST) Closure callable) {
        if(parentData.size() > 0) {
            if (!inline) {
                out.write(JsonOutput.OPEN_BRACE)
            }
            Iterator parentInfoIt = parentData.iterator()
            while ( parentInfoIt.hasNext() ) {
                ParentInfo parentInfo = parentInfoIt.next()
                def parentWritable = prepareParentWritable(parentInfo.parentTemplate, parentInfo.parentModel)
                parentWritable.writeTo(out)
                resetProcessedObjects()
                if ( parentInfoIt.hasNext() ) {
                    out.write(JsonOutput.COMMA)
                }
            }
            def jsonDelegate = new StreamingJsonBuilder.StreamingJsonDelegate(out, false, generator)
            callable.setDelegate(jsonDelegate)
            callable.call()
            if (!inline) {
                out.write(JsonOutput.CLOSE_BRACE)
            }
        }
        else {

            this.root = callable
            if(inline) {
                def jsonDelegate = new StreamingJsonBuilder.StreamingJsonDelegate(out, true, generator)
                callable.setDelegate(jsonDelegate)
                callable.call()
            }
            else {
                json.call callable
            }
        }
        return json
    }

    StreamingJsonBuilder json(Iterable iterable) {
        this.root = iterable
        json.call iterable.asList()
        return json
    }

    StreamingJsonBuilder json(Map map) {
        this.root = map
        json.call map
        return json
    }

    /**
     * Print unescaped json directly
     *
     * @param unescaped The unescaped JSON produced from templates
     *
     * @return The json builder
     */
    StreamingJsonBuilder json(JsonOutput.JsonUnescaped unescaped) {
        print(unescaped.text)
        return json
    }

    /**
     * Print unescaped json directly
     *
     * @param writable The unescaped JSON produced from templates
     *
     * @return The json builder
     */
    StreamingJsonBuilder json(JsonOutput.JsonWritable writable) {
        if(parentData.size() > 0) {
            if (!inline) {
                out.write(JsonOutput.OPEN_BRACE)
            }
            Iterator parentInfoIt = parentData.iterator()
            while ( parentInfoIt.hasNext() ) {
                ParentInfo parentInfo = parentInfoIt.next()
                def parentWritable = prepareParentWritable(parentInfo.parentTemplate, parentInfo.parentModel)
                parentWritable.writeTo(out)
                resetProcessedObjects()
                if ( parentInfoIt.hasNext() ) {
                    out.write(JsonOutput.COMMA)
                }
            }
            writable.setInline(true)
            writable.setFirst(false)
            writable.writeTo(out)
            if (!inline) {
                out.write(JsonOutput.CLOSE_BRACE)
            }
        }
        else {
            writable.setInline(inline)
            writable.writeTo(out)
        }
        return json
    }

    /**
     * TODO: When Groovy 2.4.5 go back to JsonBuilder from groovy-json
     *
     * @param callable
     * @return
     */
    StreamingJsonBuilder json(Iterable iterable, @DelegatesTo(value = StreamingJsonBuilder.StreamingJsonDelegate, strategy = Closure.DELEGATE_FIRST) Closure callable) {
        json.call(iterable.asList(), callable)
        return json
    }

    StreamingJsonBuilder json(Object...args) {
        if(args.length == 1) {
            def val = args[0]
            if(val instanceof JsonOutput.JsonUnescaped) {
                this.json((JsonOutput.JsonUnescaped)val)
            }
            else if(val instanceof JsonOutput.JsonWritable) {
                this.json((JsonOutput.JsonWritable)val)
            }
            else {
                json.call val
            }
        }
        else {
            json.call args
        }
        return json
    }

    private GrailsView prepareParentWritable(GrailsViewTemplate parentTemplate, Map parentModel) {
        parentModel.putAll(binding.variables)
        for(o in binding.variables.values()) {
            if (o != null) {
                parentModel.put(GrailsNameUtils.getPropertyName(o.getClass().getSuperclass().getName()), o)
            }
        }
        JsonViewWritableScript writable = (JsonViewWritableScript) parentTemplate.make((Map) parentModel)
        writable.inline = true
        writable.locale = locale
        writable.response = response
        writable.request = request
        writable.controllerNamespace = controllerNamespace
        writable.controllerName = controllerName
        writable.actionName = actionName
        writable.config = config
        writable.generator = generator
        return writable
    }


    private void resetProcessedObjects() {
        if (binding.hasVariable(DefaultGrailsJsonViewHelper.PROCESSED_OBJECT_VARIABLE)) {
            Map processed = (Map) binding.getVariable(DefaultGrailsJsonViewHelper.PROCESSED_OBJECT_VARIABLE)
            processed.clear()
        }
    }
}
