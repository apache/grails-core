
try {
    def cls = Class.forName("yakworks.hibernate.proxy.ByteBuddyGroovyProxyFactory")
    println "Constructors for ${cls.name}:"
    cls.declaredConstructors.each { println it }
} catch (e) {
    println "Error: ${e.message}"
}
