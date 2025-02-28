package bean.injection

import groovy.transform.CompileStatic

import jakarta.inject.Named
import jakarta.inject.Singleton

@Singleton
@CompileStatic
@Named('special')
class SpecialNamedService implements NamedService {

    final String name = 'special'

}
