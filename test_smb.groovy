import groovy.xml.StreamingMarkupBuilder

def myNamespace = "context"
def name = "annotation-config"
def args = [[:]]

def builder = new StreamingMarkupBuilder()
def callable = {
    mkp.declareNamespace(context: "http://www.springframework.org/schema/context")
    delegate."$myNamespace"."$name"(*args)
}
def writable = builder.bind(callable)
println "XML: " + writable.toString()
