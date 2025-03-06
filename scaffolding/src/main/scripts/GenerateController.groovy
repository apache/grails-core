import org.grails.cli.interactive.completers.DomainClassCompleter

description( "Generates a controller that performs CRUD operations" ) {
  usage "grails generate-controller [DOMAIN CLASS]"
  completer DomainClassCompleter
  flag name:'force', description:"Whether to overwrite existing files"
}


if(args) {
  def classNames = args
  if(args[0] == '*') {
    classNames = resources("file:grails-app/domain/**/*.groovy")
                    .collect { className(it) }
  }
  for(arg in classNames) {
    def sourceClass = source(arg)
    def overwrite = flag('force') ? true : false
    if(sourceClass) {
      def model = model(sourceClass)
      render template: template('scaffolding/Controller.groovy'), 
             destination: file("grails-app/controllers/${model.packagePath}/${model.convention('Controller')}.groovy"),
             model: model,
             overwrite: overwrite

      render template: template('scaffolding/Service.groovy'),
              destination: file("grails-app/services/${model.packagePath}/${model.convention('Service')}.groovy"),
              model: model,
              overwrite: overwrite

      render template: template('scaffolding/Spec.groovy'), 
             destination: file("src/test/groovy/${model.packagePath}/${model.convention('ControllerSpec')}.groovy"),
             model: model,
             overwrite: overwrite

      render template: template('scaffolding/ServiceSpec.groovy'),
             destination: file("src/integration-test/groovy/${model.packagePath}/${model.convention('ServiceSpec')}.groovy"),
             model: model,
             overwrite: overwrite


      addStatus "Scaffolding completed for ${projectPath(sourceClass)}"                                         
    }
    else {
      error "Domain class not found for name $arg"
    }
  }
}
else {
    error "No domain class specified"
}
