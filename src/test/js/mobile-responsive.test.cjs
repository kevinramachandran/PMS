const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/js/mobile-responsive.js', 'utf8');
function fixture(startMobile = true, startPhone = true) {
    const handlers = {}, mediaHandlers = {}, phoneHandlers = {};
    let mutation;
    let sidebarMutation;
    let document;
    class Element {
        constructor(tag = 'div', classes = '') {
            this.tagName = tag.toUpperCase(); this.children = []; this.dataset = {}; this.attrs = {};
            this.textContent = '';
            this.className = classes; this.disabled = false; this.style = {}; this.hidden = false;
            this.classList = {
                contains: name => this.className.split(' ').includes(name),
                add: (...names) => { this.className = [...new Set([...this.className.split(' ').filter(Boolean), ...names])].join(' '); },
                remove: (...names) => { this.className = this.className.split(' ').filter(c => !names.includes(c)).join(' '); },
                toggle: (name, force) => { if (force) this.classList.add(name); else this.classList.remove(name); }
            };
        }
        appendChild(child) { if (child.parentElement) child.parentElement.children = child.parentElement.children.filter(c => c !== child); this.children.push(child); child.parentElement = child.parentNode = this; return child; }
        prepend(child) { this.appendChild(child); this.children = [child, ...this.children.filter(c => c !== child)]; }
        insertBefore(child, before) { this.appendChild(child); this.children = this.children.filter(c => c !== child); this.children.splice(this.children.indexOf(before), 0, child); }
        remove() { this.parentElement.children = this.parentElement.children.filter(c => c !== this); }
        setAttribute(key, value) { this.attrs[key] = value; }
        removeAttribute(key) { delete this.attrs[key]; }
        hasAttribute(key) { return key in this.attrs; }
        contains(el) { return el === this || this.children.some(c => c.contains(el)); }
        matches(selector) { return selector.split(',').some(s => {
            if (s.startsWith('.sidebar a[href]')) return this.tagName === 'A' && this.attrs.href && this.attrs.href !== '#';
            if (s.startsWith('.')) return this.classList.contains(s.slice(1));
            if (s === 'td[data-mobile-label]') return this.tagName === 'TD' && 'data-mobile-label' in this.attrs;
            return s === 'table' && this.tagName === 'TABLE';
        }); }
        closest(selector) { if (this.matches(selector)) return this; return this.parentElement ? this.parentElement.closest(selector) : null; }
        querySelectorAll(selector) {
            const all = this.children.flatMap(c => [c, ...c.querySelectorAll('*')]);
            if (selector === '*') return all;
            if (selector === '[data-mobile-generated-wrapper]') return all.filter(c => c.dataset.mobileGeneratedWrapper);
            if (selector === 'a[href],button,[tabindex="0"]') return all.filter(c => c.tagName === 'BUTTON' || c.tagName === 'A');
            return all.filter(c => c.matches(selector));
        }
        querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
        addEventListener(name, callback) { this['on' + name] = callback; }
        focus() { document.activeElement = this; }
        getClientRects() { return this.hidden ? [] : [{}]; }
        click() { dispatch('click', this); }
    }
    const body = new Element('body');
    const sidebar = body.appendChild(new Element('aside', 'sidebar collapsed')); sidebar.id = 'sidebar';
    const link = sidebar.appendChild(new Element('a')); link.setAttribute('href', '/settings');
    const toggle = body.appendChild(new Element('div', 'hamburger')); toggle.id = 'hamburger';
    const overlay = body.appendChild(new Element('div', 'sidebar-overlay')); overlay.id = 'sidebarOverlay';
    const main = body.appendChild(new Element('main'));
    const bareTable = main.appendChild(new Element('table'));
    const existing = main.appendChild(new Element('div', 'daily-data-table'));
    const existingTable = existing.appendChild(new Element('table'));
    const cardTable = main.appendChild(new Element('table'));
    const cardHead = cardTable.appendChild(new Element('thead'));
    const cardHeaderRow = cardHead.appendChild(new Element('tr'));
    const nameHeader = cardHeaderRow.appendChild(new Element('th')); nameHeader.textContent = 'Name';
    const areaHeader = cardHeaderRow.appendChild(new Element('th')); areaHeader.textContent = 'Area';
    cardHeaderRow.cells = [nameHeader, areaHeader]; cardHead.rows = [cardHeaderRow]; cardTable.tHead = cardHead;
    const cardBody = cardTable.appendChild(new Element('tbody'));
    const cardRow = cardBody.appendChild(new Element('tr'));
    const nameCell = cardRow.appendChild(new Element('td')); nameCell.textContent = 'A user';
    const areaCell = cardRow.appendChild(new Element('td')); areaCell.textContent = 'Line 1';
    cardRow.cells = [nameCell, areaCell]; cardBody.rows = [cardRow]; cardTable.tBodies = [cardBody];
    const denied = main.appendChild(new Element('button')); denied.hidden = true; denied.disabled = true; denied.dataset.permissionHidden = 'true';
    document = { body, readyState: 'complete', activeElement: null,
        getElementById: id => ({ sidebar, hamburger: toggle, sidebarOverlay: overlay }[id]),
        querySelector: () => toggle, querySelectorAll: s => body.querySelectorAll(s),
        createElement: tag => new Element(tag),
        addEventListener: (name, callback) => { (handlers[name] ||= []).push(callback); }
    };
    const media = { matches: startMobile, addEventListener: (name, callback) => { mediaHandlers[name] = callback; } };
    const phone = { matches: startPhone, addEventListener: (name, callback) => { phoneHandlers[name] = callback; } };
    function dispatch(name, target, options = {}) {
        const event = { target, prevented: false, stopped: false, preventDefault() { this.prevented = true; }, stopImmediatePropagation() { this.stopped = true; }, ...options };
        for (const handler of handlers[name] || []) { handler(event); if (event.stopped) break; }
        return event;
    }
    vm.runInNewContext(source, { document, window: { matchMedia: query => query.includes('600px') ? phone : media, requestAnimationFrame: callback => callback() }, MutationObserver: class { constructor(callback) { this.callback = callback; } observe(target) { if (target === sidebar) sidebarMutation = this.callback; else mutation = this.callback; } } });
    return { body, sidebar, toggle, overlay, link, main, bareTable, existingTable, existing, cardTable, nameCell, areaCell, denied, document, dispatch,
        resize(value, phoneValue = value) { media.matches = value; phone.matches = phoneValue; mediaHandlers.change(); phoneHandlers.change(); },
        delayedCollapse() { sidebar.classList.add('collapsed'); sidebarMutation(); },
        addTable() { const table = main.appendChild(new Element('table')); mutation([{ addedNodes: [table] }]); return table; }
    };
}
const mobile = fixture();
assert.equal(mobile.sidebar.hasAttribute('inert'), true, 'closed mobile navigation cannot receive focus');
assert.equal(mobile.sidebar.classList.contains('collapsed'), false);
assert.equal(mobile.bareTable.parentElement.dataset.mobileGeneratedWrapper, 'true');
assert.equal(mobile.existingTable.parentElement, mobile.existing, 'reuse existing scrollports');
assert.equal(mobile.cardTable.classList.contains('pms-mobile-table-cards'), true, 'simple phone tables use labeled cards');
assert.equal(mobile.areaCell.attrs['data-mobile-label'], 'Area');
assert.equal(mobile.denied.hidden, true); assert.equal(mobile.denied.disabled, true); assert.equal(mobile.denied.dataset.permissionHidden, 'true');
let event = mobile.dispatch('click', mobile.toggle);
assert.equal(event.stopped, true, 'mobile handler suppresses conflicting page toggle handlers');
assert.equal(mobile.body.classList.contains('pms-mobile-nav-open'), true);
assert.equal(mobile.sidebar.hasAttribute('inert'), false);
const close = mobile.sidebar.children[0]; assert.equal(mobile.document.activeElement, close);
mobile.dispatch('keydown', close, { key: 'Tab', shiftKey: true });
assert.equal(mobile.document.activeElement, mobile.sidebar.children[1], 'focus wraps within drawer');
mobile.dispatch('click', mobile.link);
assert.equal(mobile.body.classList.contains('pms-mobile-nav-open'), false, 'page links close the drawer');
assert.equal(mobile.sidebar.hasAttribute('inert'), false, 'page links remain activatable after the drawer closes');
mobile.dispatch('click', mobile.toggle);
assert.equal(mobile.body.classList.contains('pms-mobile-nav-open'), true);
mobile.dispatch('keydown', mobile.document.activeElement, { key: 'Escape' });
assert.equal(mobile.body.classList.contains('pms-mobile-nav-open'), false); assert.equal(mobile.document.activeElement, mobile.toggle);
mobile.dispatch('keydown', mobile.toggle, { key: 'Enter' });
assert.equal(mobile.body.classList.contains('pms-mobile-nav-open'), true);
mobile.dispatch('click', mobile.overlay); assert.equal(mobile.body.classList.contains('pms-mobile-nav-open'), false);
const dynamicTable = mobile.addTable(); assert.equal(dynamicTable.parentElement.dataset.mobileGeneratedWrapper, 'true');
mobile.resize(false);
assert.equal(mobile.bareTable.parentElement, mobile.main, 'desktop restores original bare table structure');
assert.equal(dynamicTable.parentElement, mobile.main);
assert.equal(mobile.cardTable.classList.contains('pms-mobile-table-cards'), false, 'desktop restores table layout');
assert.equal('data-mobile-label' in mobile.areaCell.attrs, false, 'desktop removes mobile labels');
assert.equal(mobile.sidebar.classList.contains('collapsed'), true, 'restore desktop sidebar state');
assert.equal(mobile.sidebar.hasAttribute('inert'), false);
event = mobile.dispatch('click', mobile.toggle); assert.equal(event.stopped, false, 'desktop page handlers retain control');
mobile.resize(true); assert.equal(mobile.bareTable.parentElement.dataset.mobileGeneratedWrapper, 'true');
mobile.delayedCollapse(); assert.equal(mobile.sidebar.classList.contains('collapsed'), false, 'delayed legacy collapse must not hide mobile labels');
mobile.resize(true, false); assert.equal(mobile.cardTable.classList.contains('pms-mobile-table-cards'), false, 'tablet widths keep tables scrollable');
mobile.resize(true, true); assert.equal(mobile.cardTable.classList.contains('pms-mobile-table-cards'), true, 'phone widths switch simple tables to cards');
mobile.resize(false); assert.equal(mobile.sidebar.classList.contains('collapsed'), true, 'preserve delayed desktop preference');
const desktop = fixture(false); assert.equal(desktop.bareTable.parentElement, desktop.main, 'desktop initialization does not wrap tables');
console.log('Mobile navigation, focus, table scrolling, resize restoration, dynamic content and permission preservation checks passed.');
