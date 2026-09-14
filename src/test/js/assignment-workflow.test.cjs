const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const fields = new Map();
const handlers = [];
function field(selector) {
 if (!fields.has(selector)) fields.set(selector, {value:'', props:{}, options:[], text:''});
 const item=fields.get(selector);
 return {
 val(value) {if(value===undefined)return item.value; item.value=value;return this;},
 prop(name,value) {if(value===undefined)return item.props[name];item.props[name]=value;return this;},
 find() {return {toArray:()=>item.options,text:()=>this};},
 append(option) {item.options.push(option);return this;},
 html(value) {item.options=[];item.value='';return this;},
 closest() {return this;},toggleClass(){return this;},text(value){item.text=value;return this;},
 on(...args) {handlers.push(args);return this;}
 };
}
function $(selector) {return field(typeof selector==='object'&&selector.id?'#'+selector.id:selector);}
const context={window:{},jQuery:$,document:{},Option:function(text,value){this.text=text;this.value=value;}};
vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/assignment-workflow.js','utf8'),context);
const workflow=context.window.AssignmentWorkflow;
workflow.setRecord({},'#responsibility');
assert.equal($('#responsibility').prop('disabled'),false);
for(const n of [1,2]){assert.equal($('#reassignedTo'+n).prop('disabled'),true);assert.equal($('#reassignment'+n+'Remark').prop('required'),false);}
workflow.setRecord({id:1,responsibility:'original'},'#responsibility');
assert.equal($('#responsibility').prop('disabled'),true);
assert.equal($('#reassignedTo1').prop('disabled'),false);
assert.equal($('#reassignedTo2').prop('disabled'),true);
workflow.select('#reassignedTo1','next');workflow.refresh();
assert.equal($('#reassignment1Remark').prop('disabled'),false);
assert.equal($('#reassignment1Remark').prop('required'),true);
assert.equal($('#reassignedTo2').prop('disabled'),true,'unsaved stage 1 must not unlock stage 2');
workflow.setRecord({id:1,responsibility:'original',reassignedTo1:'next',reassignment1Remark:'Reason'},'#responsibility');
assert.equal($('#reassignedTo1').prop('disabled'),true);
assert.equal($('#reassignment1Remark').val(),'Reason');
assert.equal($('#reassignedTo2').prop('disabled'),false);
workflow.options('#reassignedTo1','<option value="other">Other</option>');
assert.equal($('#reassignedTo1').val(),'next','removed user remains selected');
workflow.setRecord({id:1,assignedTo:'original',reassignedTo1:'next',reassignedTo2:'last'},'#assignedTo',true);
for(const selector of ['#assignedTo','#reassignedTo1','#reassignedTo2','#reassignment1Remark','#reassignment2Remark']) assert.equal($(selector).prop('disabled'),true);
workflow.setRecord({},'#assignedTo');assert.equal($('#reassignedTo1').val(),'');assert.equal($('#reassignedTo2').val(),'');
for(const [name,initial] of [['gemba-walk','responsibility'],['gemba-kaizen','assignedTo'],['abnormality-reporting','assignTo'],['process-confirmation','assignedTo']]) {
 const source=fs.readFileSync('src/main/resources/static/js/'+name+'-config.js','utf8');
 assert.match(source,/let assignmentOptionsRequest = 0/);
 assert.ok(source.includes("AssignmentWorkflow.setRecord("));
 assert.ok(source.includes("'#"+initial+"'"));
 assert.match(source,/request !== assignmentOptionsRequest/);
}
console.log('Assignment workflow create, staged edit, conditional remark validation, saved option preservation, view and reset checks passed.');
