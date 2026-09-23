const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/js/settings.js', 'utf8');

for (const [buttons, modal, close] of [
    ['#closeMasterPlantView', '#masterPlantViewModal', 'closeMasterPlantView'],
    ['#closeMasterPlantAddModal, #cancelMasterPlantAdd', '#masterPlantAddModal', 'closeMasterPlantAddModal'],
    ['#closeMasterPlantDeleteConfirm, #cancelMasterPlantDelete', '#masterPlantDeleteConfirmModal', 'closeMasterPlantDeleteConfirm']
]) {
    test(`${modal}: button, nested icon and backdrop close; content clicks stay open`, () => {
        const handlers = {};
        let closed = 0;
        const context = {
            [close]: () => { closed++; },
            $: selector => ({ on: (event, handler) => {
                for (const id of selector.split(',').map(value => value.trim())) handlers[id] = handler;
            } })
        };
        const start = source.indexOf(`    $('${buttons}').on('click',`);
        assert.ok(start >= 0, 'Close buttons have a handler');
        const backdrop = source.indexOf(`    $('${modal}').on('click',`, start);
        assert.ok(backdrop > start, 'Backdrop has a separate handler');
        const end = source.indexOf('\n    });', backdrop);
        assert.ok(end > backdrop);
        vm.runInNewContext(source.slice(start, end + '\n    });'.length), context);
        for (const id of buttons.split(',').map(value => value.trim())) {
            const button = { id };
            const icon = { parentElement: button };
            let before = closed;
            handlers[id].call(button, { target: icon, currentTarget: button });
            assert.equal(closed, before + 1, 'Clicking the icon closes');
            before = closed;
            handlers[id].call(button, { target: button, currentTarget: button });
            assert.equal(closed, before + 1, 'Clicking the button closes');
        }
        const overlay = {};
        const before = closed;
        handlers[modal].call(overlay, { target: {}, currentTarget: overlay });
        assert.equal(closed, before, 'Clicking popup content does not close it');
        handlers[modal].call(overlay, { target: overlay, currentTarget: overlay });
        assert.equal(closed, before + 1, 'Clicking the backdrop closes');
    });
}
