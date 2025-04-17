description("Creates a scaffolded controller") {
  	usage 'create-controller [controller name]'
    completer org.grails.cli.interactive.completers.DomainClassCompleter
    argument name:'Controller Name', description:"The name of controller", required:true
    flag name:'force', description:"Whether to overwrite existing files"
 }

def model = model(args[0])
def overwrite = flag('force') ? true : false

render 	 template: template('scaffolding/ScaffoldedController.groovy'),
	     destination: file("grails-app/controllers/${model.packagePath}/${model.convention("Controller")}.groovy"),
	     model: model,
	     overwrite: overwrite

return true
