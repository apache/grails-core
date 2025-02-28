package bean.injection

import groovy.transform.CompileStatic

import jakarta.inject.Named
import jakarta.inject.Singleton

@Named('regular')
@Singleton
@CompileStatic
class RegularNamedService implements NamedService {

    final String name = 'regular'
    
}
