/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
import grails.core.*
import grails.util.*
import grails.plugins.*
import org.grails.core.artefact.*

model {
	GrailsApplication grailsApplication
	GrailsPluginManager pluginManager
}

xmlDeclaration()
application {
    message "Welcome to Grails!"
    environment Environment.current.name
    appversion grailsApplication.metadata.getApplicationVersion()
    grailsversion GrailsUtil.grailsVersion
    groovyversion GroovySystem.getVersion()
    jvmversion System.getProperty('java.version')
    reloadingagentenabled Environment.reloadingAgentEnabled

    artefacts {
        artefact(type: "controllers") {
         count grailsApplication.getArtefactInfo(ControllerArtefactHandler.TYPE).classesByName.size()
        }
        artefact(type: "domains") {
            count grailsApplication.getArtefactInfo(DomainClassArtefactHandler.TYPE).classesByName.size()
        }
        arefact(type: "services") {
            count grailsApplication.getArtefactInfo(ServiceArtefactHandler.TYPE).classesByName.size()
        }
    }
    controllers {
      grailsApplication.getArtefacts(ControllerArtefactHandler.TYPE).each { GrailsClass c ->
        controller {
            name c.fullName
            logicalPropertyName c.logicalPropertyName
        }
      }
    }
    plugins {
      pluginManager.allPlugins.each { GrailsPlugin p ->
        plugin {
          name p.name
          version p.version
        }
      }
    }
}
