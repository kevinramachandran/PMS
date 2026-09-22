const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/settings.js'), 'utf8');
const modalCode = source.slice(source.indexOf('window.openLicenseModal ='), source.indexOf('// ==================== SAVE LICENSE ===================='));

function setup() {
    const elements = { licenseModal: { style: {} }, licenseMaskedInput: { value: '' }, licenseModalMessage: { textContent: '' } };
    const messages = [];
    const button = { val() {}, prop() { return this; }, html() { return this; } };
    const context = { window: {}, document: { getElementById: id => elements[id] }, realLicenseValue: 'stale-token',
        $: () => button, showMessage: (...args) => messages.push(args), loadLicenseConfig() {}, console };
    context.$.ajax = options => { context.request = options; };
    vm.createContext(context);
    vm.runInContext(modalCode, context);
    context.closeLicenseModal = context.window.closeLicenseModal;
    return { context, elements, messages };
}

test('modal submits the actual edited token and never submits a stale token', () => {
    const { context, elements, messages } = setup();
    context.window.openLicenseModal();
    context.window.saveLicenseFromModal();
    assert.equal(context.request, undefined);
    assert.match(messages[0][1], /required/);
    elements.licenseMaskedInput.value = 'first';
    elements.licenseMaskedInput.value += 'second';
    context.window.saveLicenseFromModal();
    assert.equal(JSON.parse(context.request.data).licenseToken, 'firstsecond');
    assert.equal(elements.licenseModal.style.display, 'flex');
    context.request.success({ status: 'success' });
    assert.equal(elements.licenseModal.style.display, 'none');
});

test('modal displays server rejection and HTTP errors without closing', () => {
    const { context, elements, messages } = setup();
    context.window.openLicenseModal();
    elements.licenseMaskedInput.value = 'invalid';
    context.window.saveLicenseFromModal();
    context.request.success({ status: 'error', message: 'Invalid or tampered token' });
    assert.equal(messages.at(-1)[0], 'licenseModalMessage');
    assert.equal(messages.at(-1)[1], 'Invalid or tampered token');
    context.request.error({ responseJSON: { message: 'Unauthorized' } });
    assert.equal(messages.at(-1)[1], 'Unauthorized');
    assert.equal(elements.licenseModal.style.display, 'flex');
});
