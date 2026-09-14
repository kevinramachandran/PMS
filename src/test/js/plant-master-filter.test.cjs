const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/js/settings.js', 'utf8');
const values = { '#masterPlantSelect': 'Plant A', '#masterProcessAreaDepartmentSelect': 'Packaging' };
const rendered = {};
const context = {
    MASTER_PLANT_CONFIG: {
        PLANT: { label: 'Plant' },
        DEPARTMENT: { label: 'Department', inheritPlant: true },
        PROCESS_AREA: { label: 'Process Area', inheritPlant: true, parentDepartment: '#masterProcessAreaDepartmentSelect' }
    },
    masterPlantItems: {
        DEPARTMENT: [
            { name: 'Packaging', parentPlant: 'Plant A' },
            { name: 'Brewing', parentPlant: 'Plant B' }
        ],
        PROCESS_AREA: [
            { name: 'Line 1', parentPlant: 'Plant A', parentDepartment: 'Packaging' },
            { name: 'Tank', parentPlant: 'Plant A', parentDepartment: 'Brewing' },
            { name: 'Line 2', parentPlant: 'Plant B', parentDepartment: 'Packaging' }
        ]
    },
    masterPlantViewCategory: 'PROCESS_AREA',
    masterPlantRowHtml: (_, item) => item.name,
    escapeHtml: value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('"', '&quot;'),
    escapeAttributeValue: value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('"', '&quot;'),
    masterPlantOptionsHtml: items => items.map(item => item.name).join('|'),
    $: selector => ({
        val(value) { if (arguments.length) { values[selector] = value; return this; } return values[selector] || ''; },
        text(value) { rendered[selector] = value; return this; },
        html(value) { rendered[selector] = value; return this; }
    })
};
vm.createContext(context);
for (const name of ['masterPlantItemNames', 'normalizeMasterPlantKey', 'selectedMasterPlant', 'refreshMasterPlantDepartmentSelect', 'renderMasterPlantInlineLists', 'renderMasterPlantView']) {
    const start = source.indexOf('    function ' + name + '(');
    assert.ok(start >= 0, 'Function exists: ' + name);
    const end = source.indexOf('\n    function ', start + 1);
    vm.runInContext(source.slice(start, end), context);
}
context.renderMasterPlantView();
assert.equal(rendered['#masterPlantViewList'], 'Line 1');
assert.equal(Array.from(context.masterPlantItemNames('PROCESS_AREA')).join(), 'Line 1');
context.refreshMasterPlantDepartmentSelect('');
assert.equal(rendered['#masterProcessAreaDepartmentSelect'], 'Packaging');
context.renderMasterPlantView();
assert.match(rendered['#masterPlantViewList'], /Select a department/);
assert.equal(context.masterPlantItemNames('PROCESS_AREA').length, 0);
context.masterPlantViewCategory = 'DEPARTMENT';
context.renderMasterPlantView();
assert.equal(rendered['#masterPlantViewList'], 'Packaging');
values['#masterPlantSelect'] = 'Plant B';
context.renderMasterPlantView();
assert.equal(rendered['#masterPlantViewList'], 'Brewing');
values['#masterPlantSelect'] = '';
context.renderMasterPlantView();
assert.match(rendered['#masterPlantViewList'], /Select a plant/);
assert.equal(context.masterPlantItemNames('DEPARTMENT').length, 0);
context.refreshMasterPlantDepartmentSelect('');
assert.equal(rendered['#masterProcessAreaDepartmentSelect'], '');
console.log('Plant and department scope, plant changes, and empty selections passed.');
values['#masterPlantSelect'] = 'Plant A';
values['#masterProcessAreaDepartmentSelect'] = 'Packaging';
context.renderMasterPlantInlineLists();
assert.match(rendered['#masterDepartmentList'], /Packaging/);
assert.match(rendered['#masterDepartmentList'], /aria-pressed="true"/);
assert.doesNotMatch(rendered['#masterDepartmentList'], /Brewing/);
assert.match(rendered['#masterProcessAreaList'], /Line 1/);
assert.doesNotMatch(rendered['#masterProcessAreaList'], /Tank|Line 2/);
values['#masterProcessAreaDepartmentSelect'] = '';
context.renderMasterPlantInlineLists();
assert.match(rendered['#masterProcessAreaList'], /Select a department/);
values['#masterPlantSelect'] = '';
context.renderMasterPlantInlineLists();
assert.match(rendered['#masterDepartmentList'], /Select a plant/);
assert.doesNotMatch(rendered['#masterDepartmentList'], /Packaging/);
console.log('Inline department and area visibility and scope passed.');
