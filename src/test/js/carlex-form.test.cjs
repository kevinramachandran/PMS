const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/js/process-confirmation-config.js', 'utf8');
const html = fs.readFileSync('src/main/resources/templates/process-confirmation-config.html', 'utf8');
const context = {
    esc: value => String(value || '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;'),
    GROUPS: { zm: { label: 'ZM' }, pm: { label: 'PM' }, qm: { label: 'QM' } },
    observationOptions: { zm: ['Configured'] },
    observationState: { zm: [], pm: [], qm: [] }
};
vm.createContext(context);
for (const name of ['escAttribute', 'observationDescriptionOptions', 'isObservationTouched', 'isObservationComplete', 'validatePayload']) {
    const start = source.indexOf('    function ' + name + '(');
    assert.ok(start >= 0);
    const end = source.indexOf('\n    function ', start + 1);
    vm.runInContext(source.slice(start, end), context);
}
// Editing a saved choice remains possible even when the master option was removed.
let options = context.observationDescriptionOptions('zm', 'Old "choice"');
assert.match(options, /value="Old &quot;choice&quot;" selected/);
assert.match(options, /Configured/);
options = context.observationDescriptionOptions('zm', 'Configured');
assert.equal((options.match(/value="Configured"/g) || []).length, 1);
const data = { department: 'Packaging', areaOfGwProcessConfirmationConducted: 'Line 1' };
assert.match(context.validatePayload(data), /at least one/);
context.observationState.zm = [{ description: 'Issue', counterMeasureActions: '', status: 'P' }];
assert.match(context.validatePayload(data), /ZM 1 must include/);
context.observationState.zm[0].counterMeasureActions = 'Repair';
assert.equal(context.validatePayload(data), '');
context.observationState.pm = [{ description: '', counterMeasureActions: '', status: '', observationImage: '' }];
assert.equal(context.validatePayload(data), '');
// Validation stays in the visible action footer, rather than below scrolled observations.
assert.ok(html.indexOf('id="carlexMessage"') > html.indexOf('class="carlex-drawer-footer"'));
assert.match(html, /id="carlexMessage"[^>]*role="alert"/);
console.log('CarlEX saved choices, partial/complete validation, and visible error placement passed.');
