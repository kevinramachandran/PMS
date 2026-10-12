const assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/js/delete-access.js','utf8');
function fixture(allowed) {
  const elements = [], handlers = {}; let observer;
  const body = {dataset:{canDeleteRecords:String(allowed)}};
  const document = {body, readyState:'complete', querySelectorAll:()=>elements, addEventListener:(name, fn)=>handlers[name]=fn};
  vm.runInNewContext(source, {document, MutationObserver:class {constructor(fn){observer=fn;} observe(){}}});
  function button(text, recognized=false, disabled=false) {
    const el = {textContent:text,dataset:{},disabled,matches:()=>recognized,getAttribute:()=>null,closest:()=>el};
    elements.push(el); return el;
  }
  return {button,apply:()=>observer(),click:el=>{let blocked=false;handlers.click({target:el,preventDefault(){blocked=true},stopImmediatePropagation(){}});return blocked;}};
}
const editor=fixture(false), del=editor.button('Delete'), icon=editor.button('',true), save=editor.button('Save'), cancel=editor.button('Cancel');
editor.apply(); assert.equal(del.dataset.deleteHidden,'true'); assert.equal(icon.disabled,true); assert.equal(save.disabled,false); assert.equal(cancel.disabled,false);
assert.equal(editor.click(del),true); assert.equal(editor.click(save),false);
del.disabled=false; editor.apply(); assert.equal(del.disabled,true);
const dynamic=editor.button('',true); editor.apply(); assert.equal(dynamic.dataset.deleteHidden,'true');
const admin=fixture(true), allowed=admin.button('Delete'), denied=admin.button('Delete',true,true); admin.apply(); assert.equal(allowed.disabled,false); assert.equal(admin.click(allowed),false); assert.equal(denied.disabled,true);
const absent=fixture(undefined), hidden=absent.button('Delete'); absent.apply(); assert.equal(hidden.disabled,true);
for(const file of fs.readdirSync('src/main/resources/templates').filter(n=>n.endsWith('.html')&&n!=='login.html')) {
 const html=fs.readFileSync('src/main/resources/templates/'+file,'utf8');
 assert.match(html,/data-can-delete-records=\$\{canDeleteRecords\}/,file);
 assert.match(html,/delete-access\.js\?v=20261012/,file);
 assert.match(html,/delete-access\.css\?v=20261012/,file);
}
console.log('Action visibility, event blocking, dynamic updates, existing restrictions, and page coverage passed.');
