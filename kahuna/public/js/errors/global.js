import angular from 'angular';
import template from './global.html!text';

import 'angular-messages';
import 'pandular';
import '../sentry/sentry';
import './http';

export var global = angular.module(
    'kahuna.errors.global',
    ['ngMessages', 'pandular.session', 'kahuna.errors.http']
);

global.factory('globalErrors', [function() {
    var errors = {};

    function trigger(key, reason) {
console.log(key, reason)
        errors[key] = reason || true;
    }

    function destroy(key) {
        delete errors[key];
    }

    function getErrors() {
        return errors;
    }

    return {
        trigger,
        destroy,
        getErrors
    };
}]);


global.controller('GlobalErrorsCtrl',
                  ['$location', 'globalErrors',
                   function($location, globalErrors) {

    var ctrl = this;
    ctrl.errors = globalErrors.getErrors();


console.log("ERRORS", ctrl.errors);

    // handy as these can happen anywhere
    // ctrl.getLoginUrl = () => $location.url();
    // ctrl.getLoginUrl = () => `/login?redirectUri=${$location.url()}`;
    ctrl.getLoginUrl = () => {
        return ctrl.errors.unauthorised.data.links[0].href
    };

}]);


global.directive('uiGlobalErrors', [function() {
    return {
        restrict: 'E',
        controller: 'GlobalErrorsCtrl',
        controllerAs: 'ctrl',
        bindToController: true,
        template: template
    };
}]);
