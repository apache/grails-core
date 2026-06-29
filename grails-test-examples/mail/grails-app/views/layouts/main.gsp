<!doctype html>
<html lang="en" class="no-js">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta http-equiv="X-UA-Compatible" content="IE=edge"/>
    <title>
    <g:layoutTitle default="Grails"/>
    </title>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <asset:link rel="icon" href="favicon.ico" type="image/x-ico"/>
    <asset:javascript src="theme.js"/>
    <asset:stylesheet src="application.css"/>
    <g:layoutHead/>
</head>

<body>

<nav class="navbar navbar-expand-lg bg-body-tertiary">
    <div class="container-fluid">
        <a class="navbar-brand" href="/#"><asset:image class="w-75" src="grails.svg" alt="Grails Logo"/></a>
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarContent" aria-controls="navbarSupportedContent" aria-expanded="false" aria-label="Toggle navigation">
            <span class="navbar-toggler-icon"></span>
        </button>

        <div class="collapse navbar-collapse" aria-expanded="false" id="navbarContent">
            <ul class="navbar-nav">
                <g:pageProperty name="page.nav"/>
            </ul>
        </div>
    </div>
</nav>

<g:layoutBody/>

<div class="footer" role="contentinfo">
    <div class="container-fluid">
        <div class="row">
            <div class="card border-0 col-12 col-md">
                <div class="card-body">
                    <h6 class="card-title">
                        <a class="link-underline link-underline-opacity-0" href="https://guides.grails.org" target="_blank">
                            <asset:image src="advancedgrails.svg" alt="Grails Guides" class="me-2" width="34" />Grails Guides
                        </a>
                    </h6>
                    <p class="card-text">Building your first Grails app? Looking to add security, or create a Single-Page-App? Check out the <a href="https://guides.grails.org" target="_blank">Grails Guides</a> for step-by-step tutorials.</p>
                </div>
            </div>
            <div class="card border-0 col-12 col-md">
                <div class="card-body">
                    <h6 class="card-title">
                        <a class="link-underline link-underline-opacity-0" href="https://docs.grails.org" target="_blank">
                            <asset:image src="documentation.svg" alt="Grails Documentation" class="me-2" width="34" />Documentation
                        </a>
                    </h6>
                    <p class="card-text">Ready to dig in? You can find in-depth documentation for all the features of Grails in the <a href="https://docs.grails.org" target="_blank">User Guide</a>.</p>
                </div>
            </div>
            <div class="card border-0 col-12 col-md">
                <div class="card-body">
                    <h6 class="card-title">
                        <a class="link-underline link-underline-opacity-0" href="https://slack.grails.org" target="_blank">
                            <asset:image src="slack.svg" alt="Grails Slack" class="me-2" width="34" />Join the Community
                        </a>
                    </h6>
                    <p class="card-text">Get feedback and share your experience with other Grails developers in the community <a href="https://slack.grails.org" target="_blank">Slack channel</a>.</p>
                </div>
            </div>
        </div>
    </div>
</div>

<div id="spinner" class="position-absolute top-0 end-0 p-1" style="display:none;">
    <div class="spinner-border spinner-border-sm" role="status">
        <span class="visually-hidden">Loading...</span>
    </div>
</div>


<asset:javascript src="application.js"/>

</body>
</html>
