const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const script = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/data-sync.js'), 'utf8');

function loadFunction(name, context) {
    const start = script.indexOf('    function ' + name + '(');
    const end = script.indexOf('\n    function ', start + 1);
    vm.runInNewContext(script.slice(start, end), context);
}

test('Master header sync selects every category on the current page and excludes License and unrelated pages', () => {
    const context = { URLSearchParams };
    loadFunction('masterPageDatasets', context);
    const select = context.masterPageDatasets;
    assert.equal(select('/pms-configuration', ''), 'users');
    for (const page of ['/sync-configuration', '/email-configuration', '/smtp-configuration']) {
        assert.equal(select(page, ''), ''); // Reuse the configured sync plan.
    }
    const expected = {
        'kpi-plant-name': ['PLANT', 'DEPARTMENT', 'PROCESS_AREA', 'DESIGNATION'],
        'master-designation': ['DESIGNATION'],
        'master-abnormality': ['ABT_TAG_TYPE', 'ABNORMALITY_DEFECT_TYPE'],
        'master-gemba-walk': ['GEMBA_CATEGORY', 'LIFE_SAVER_RULE'],
        'master-gemba-kaizen': ['CLASSIFICATION_OF_KAIZEN'],
        'master-process': ['ZM_OBSERVATION', 'PM_OBSERVATION', 'OM_OBSERVATION', 'QM_OBSERVATION']
    };
    for (const [page, categories] of Object.entries(expected)) {
        const selected = select('/settings', '?config=' + page).split('\n');
        assert.deepEqual(selected.map(spec => spec.split(':')[1]), categories);
    }
    assert.equal(select('/settings', '?config=license'), null);
    assert.equal(select('/settings', '?config=info-portal'), null);
    assert.equal(select('/settings', ''), null);
    assert.equal(select('/issue-board', ''), null);
});

test('Master Sync appears after the date and before the profile and mounts the existing sync behavior once', () => {
    const date = {}, profile = {}, children = [date, profile];
    const header = {
        querySelector: selector => selector === '.pms-profile' ? profile : children.find(child => child.className?.includes('master-header-sync')),
        insertBefore: (button, anchor) => children.splice(children.indexOf(anchor), 0, button)
    };
    const mounted = [];
    const context = {
        document: {
            querySelector: () => header,
            createElement: () => ({ setAttribute() {} })
        },
        mount: (...args) => mounted.push(args)
    };
    loadFunction('mountMasterHeader', context);
    context.mountMasterHeader('abnormality-master:ABT_TAG_TYPE\nabnormality-master:ABNORMALITY_DEFECT_TYPE');
    context.mountMasterHeader('users');
    assert.equal(children.length, 3);
    assert.equal(children[0], date);
    assert.equal(children[2], profile);
    assert.equal(mounted.length, 1);
    assert.equal(mounted[0][0], header);
    assert.equal(mounted[0][1], 'abnormality-master:ABT_TAG_TYPE\nabnormality-master:ABNORMALITY_DEFECT_TYPE');
    assert.equal(mounted[0][2], children[1]);
});

test('sync dialog uses cloud sync only', () => {
    assert.match(script, /\/api\/cloud-sync\/status/);
    assert.match(script, /\/api\/cloud-sync\/run/);
    assert.match(script, /Sync now/);
    assert.match(script, /Sync settings are not configured/);
    assert.doesNotMatch(script, /Export CSV/);
    assert.doesNotMatch(script, /Import CSV/);
    assert.doesNotMatch(script, /Import template/);
});

test('sync dialog is mounted on supported configuration surfaces', () => {
    for (const page of ['/gemba-kaizen-config', '/abnormality-reporting-config', '/gemba-walk-config', '/process-confirmation-config', '/pms-configuration']) {
        assert.match(script, new RegExp(page.replaceAll('-', '\\-')));
    }
});

test('master subpages mount only the header sync, while report pages retain their action', () => {
    const mounted = [], headers = [];
    const context = {
        URLSearchParams,
        window: { location: { pathname: '/settings', search: '' } },
        document: { querySelector: () => ({}) },
        mount: (...args) => mounted.push(args),
        mountMasterHeader: dataset => headers.push(dataset)
    };
    loadFunction('masterPageDatasets', context);
    const start = script.indexOf('    function init()');
    const end = script.indexOf('    if (document.readyState', start);
    vm.runInNewContext(script.slice(start, end), context);
    for (const page of ['kpi-plant-name', 'master-designation', 'master-abnormality', 'master-gemba-walk', 'master-gemba-kaizen', 'master-process']) {
        context.window.location.search = '?config=' + page;
        context.init();
    }
    assert.equal(headers.length, 6);
    assert.equal(mounted.length, 0);
    context.window.location.pathname = '/gemba-walk-config';
    context.init();
    assert.equal(mounted.length, 1);
    assert.equal(mounted[0][1], 'gemba-walk');
});
