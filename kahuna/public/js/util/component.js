import angular from 'angular';

export function component(namespace, name, template, directive, ctrl) {

    // TODO: Make pre & postfixes configurable
    const moduleName = `${namespace}.${name}`;
    const capitalisedName =  name.charAt(0).toUpperCase() + name.substring(1);
    const ctrlName = `${capitalisedName}Ctrl`;
    const directiveName = `ui${capitalisedName}`;

    let module = angular.module(moduleName, []);

    const defaultDirective = {
        restrict: 'E',
        controller: ctrlName,
        controllerAs: 'ctrl',
        bindToController: true,
        template: template
    };

    module.controller(ctrlName, ctrl);

    module.directive(directiveName, function() {
         return angular.extend({}, defaultDirective, directive);
    });

    return module;
}
