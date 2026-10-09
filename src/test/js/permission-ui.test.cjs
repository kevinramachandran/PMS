const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/js/permission-ui.js', 'utf8');
function fixture(edit) {
    const handlers = {};
    let observe;
    const body = {dataset:{canEditCurrentPage:String(edit)}, hasAttribute:()=>true, closest:()=>null};
    const actions = [], fields = [];
    const context = {
        window:{}, document:{body, readyState:'complete',
            querySelectorAll: selector => selector.startsWith('form input') ? fields : actions,
            addEventListener:(name, callback)=>{handlers[name]=callback;}},
        MutationObserver:class {constructor(callback){observe=callback;} observe(){} }
    };
    vm.runInNewContext(source, context);
    function element({id='', type='', action=false, scope=null}={}) {
        const el={id,type,dataset:{},disabled:false,
            matches:()=>['search','date','month'].includes(type),
            closest:selector=>selector === '[data-edit-authorized]' ? scope : selector.startsWith('.metric-actions') ? null : action ? el : null};
        (action ? actions : fields).push(el);
        return el;
    }
    return {context,element,actions,handlers,apply:()=>observe()};
}
const viewer=fixture(false);
const add=viewer.element({action:true});
const name=viewer.element({id:'reportName'});
const search=viewer.element({id:'reportSearch',type:'search'});
const date=viewer.element({id:'dateInput',type:'date'});
const filter=viewer.element({id:'statusFilter'});
viewer.apply();
assert.equal(add.dataset.permissionHidden,'true'); assert.equal(add.disabled,true);
assert.equal(name.disabled,true); assert.equal(search.disabled,false); assert.equal(date.disabled,false); assert.equal(filter.disabled,false);
let blocked=0;
viewer.handlers.click({target:add,preventDefault(){blocked++;},stopImmediatePropagation(){blocked++;}});
assert.equal(blocked,2);
viewer.handlers.submit({target:name,preventDefault(){blocked++;},stopImmediatePropagation(){blocked++;}});
assert.equal(blocked,4);
// The next AJAX render and a page script re-enabling a button must not restore editing.
const laterDelete=viewer.element({action:true}); add.disabled=false; viewer.apply();
assert.equal(laterDelete.dataset.permissionHidden,'true'); assert.equal(add.disabled,true);
viewer.apply(); // Idempotent repeated observation.
const editor=fixture(true); const save=editor.element({action:true}); editor.apply(); assert.equal(save.disabled,false); assert.equal(save.dataset.permissionHidden,undefined);
const deniedSection={dataset:{editAuthorized:'false'}};
const costSave=editor.element({action:true,scope:deniedSection}); editor.apply(); assert.equal(costSave.disabled,true);
const crossPage=viewer.element({action:true,scope:{dataset:{editAuthorized:'true'}}}); viewer.apply(); assert.equal(crossPage.disabled,false);
// All protected templates load the shared rules; newly added mutation buttons must be marked.
for(const file of fs.readdirSync('src/main/resources/templates').filter(name=>name.endsWith('.html')&&!['login.html','sync-configuration.html'].includes(name))) {
 const html=fs.readFileSync('src/main/resources/templates/'+file,'utf8');
 assert.match(html,/permission-ui.js/,file); assert.match(html,/data-can-edit-current-page/,file);
 for(const match of html.matchAll(/<button\b([^>]*)>([\s\S]*?)<\/button>/g)) {
  const text=match[2].replace(/<[^>]*>/g,' ').replace(/\s+/g,' ').trim();
  if(/^(Add|Save|Update|Delete|Remove|Upload|Import|Edit)/i.test(text)) assert.match(match[1],/data-requires-edit/,file+': '+text);
 }
}
console.log('Permission UI checks passed: viewer/editor, dynamic actions, submit prevention, filters, section denial, cross-page grants, template audit.');
