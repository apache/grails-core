package org.grails.web.converters.configuration

import grails.converters.JSON

/**
 * @author Siegfried Puchbauer
 */

new ConvertersConfigurationInitializer().initalize()

def defcfg = ConvertersConfigurationHolder.getConverterConfiguration(JSON)
def imcfg = new ImmutableConverterConfiguration<JSON>(defcfg)
def chcfg = new ChainedConverterConfiguration<JSON>(defcfg)

sleep 30

println defcfg
println imcfg
println chcfg
try {
    println defcfg.getMarshaller(new Object())
    println imcfg.getMarshaller(new Object())
    println chcfg.getMarshaller(new Object())
}
catch (e) {
    e.printStackTrace()
}

def map = [ immutable: 0, chained: 0, default: 0 ]

def test = { label, jsonConfig ->
def start = System.currentTimeMillis()
30000.times {
    assert jsonConfig.getMarshaller(new Object())
}
def time = System.currentTimeMillis()-start
println "$label --> ${time}ms"
map[label] = map[label] + time
}

test("default", defcfg)
test("chained", chcfg)
test("immutable", imcfg)

 map = [ immutable: 0, chained: 0, default: 0 ]

100.times {
    test("chained", chcfg)
    test("immutable", imcfg)
    test("default", defcfg)
}

println "======"

println map
