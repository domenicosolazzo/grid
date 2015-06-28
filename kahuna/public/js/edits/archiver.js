import angular from 'angular';
import template from './archiver.html!text';
import {component} from '../util/component';

const directive = { scope: { archived: '=', withText: '=', disabled: '=' }};

export const archiver =
    component('kahuna.edits', 'archiver', template, directive, ['$window', Archiver]);

function Archiver($window) {
    var ctrl = this;

    ctrl.toggleArchived = toggleArchived;
    ctrl.isArchived = ctrl.archived.data;
    ctrl.archiving = false;

    function toggleArchived() {
        var setVal = !ctrl.isArchived;
        ctrl.archiving = true;

        // FIXME: theseus should return a `Resource` on `put` that we can
        // update `ctrl.archived` with.
        ctrl.archived
            .put({ data: setVal })
            .then(
                res => ctrl.isArchived = res.data,
                ()  => $window.alert('Failed to save the changes, please try again.')
            ).finally(() => ctrl.archiving = false);
    }
}
