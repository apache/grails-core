package example.grails

import groovy.transform.Memoized

class Utils {
    @Memoized
    static boolean available(String url) {
        try {
            url.toURL().openConnection().with {
                connectTimeout = 1000
                connect()
            }
            true
        } catch (IOException e) {
            false
        }
    }
}