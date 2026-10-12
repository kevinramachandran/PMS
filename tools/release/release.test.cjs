const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),cp=require('node:child_process');
const {hash,snapshot,sourceState,compareSnapshot,validateVisual,preflight,parseArgs}=require('./release.cjs');
function fixture(t) {const dir=fs.mkdtempSync(path.join(os.tmpdir(),'pms-release-'));t.after(()=>{
 const resolved=path.resolve(dir);
 if(path.dirname(resolved)!==path.resolve(os.tmpdir())||!path.basename(resolved).startsWith('pms-release-'))throw Error('Unsafe fixture cleanup path');
 fs.rmSync(resolved,{recursive:true,force:true});
});return dir;}
function write(root,relative,text){fs.mkdirSync(path.dirname(path.join(root,relative)),{recursive:true});fs.writeFileSync(path.join(root,relative),text);}
function git(root,...args){return cp.execFileSync('git',['-c',`safe.directory=${root.replaceAll('\\','/')}`,'-C',root,...args],{encoding:'utf8',stdio:['ignore','pipe','pipe']}).trim();}
function repo(root,branch) {fs.mkdirSync(root,{recursive:true});git(root,'init','-b',branch);git(root,'config','user.name','Release fixture');git(root,'config','user.email','fixture@example.test');write(root,'src/app.java','class App {}');git(root,'add','src');git(root,'commit','-m','Fixture');}
test('rejects wrong branches, modified and untracked source; runtime logs do not block',t=>{
 const root=fixture(t);repo(root,'onprem');
 const original=sourceState(root,'onprem',['src']);assert.match(original.commit,/^[a-f0-9]{40}$/);
 write(root,'logs/runtime.log','runtime');assert.deepEqual(sourceState(root,'onprem',['src']),original);
 assert.throws(()=>sourceState(root,'cloud',['src']),/Wrong release branch/);
 write(root,'src/app.java','changed');assert.throws(()=>sourceState(root,'onprem',['src']),/Uncommitted/);
 write(root,'src/app.java','class App {}');write(root,'src/new.java','new');assert.throws(()=>sourceState(root,'onprem',['src']),/Uncommitted/);
});
test('ignored source cannot silently enter a committed build',t=>{
 const root=fixture(t);repo(root,'onprem');write(root,'.gitignore','src/secret.java\n');git(root,'add','.gitignore');git(root,'commit','-m','Fixture ignore');write(root,'src/secret.java','ignored');
 assert.throws(()=>sourceState(root,'onprem',['src']),/Ignored application inputs/);
});
test('snapshot detects additions, edits and removals, including legacy source drift',t=>{
 const root=fixture(t);write(root,'src/a','first');const before=snapshot(root,['src']);
 write(root,'src/a','second');assert.throws(()=>compareSnapshot(before,snapshot(root,['src']),'legacy'),/legacy changed/);
 write(root,'src/a','first');write(root,'src/b','added');assert.throws(()=>compareSnapshot(before,snapshot(root,['src']),'legacy'),/src\/b/);
 fs.unlinkSync(path.join(root,'src/a'));assert.throws(()=>compareSnapshot(before,snapshot(root,['src']),'legacy'),/src\/a/);
});
test('both repositories must pass and unreviewed historical copies block preflight',t=>{
 const root=fixture(t);repo(path.join(root,'local'),'onprem');repo(path.join(root,'cloud'),'cloud');write(root,'archive/src/a','reviewed');
 const config={variants:{onprem:{directory:'local',branch:'onprem'},cloud:{directory:'cloud',branch:'cloud'}},releaseInputs:['src'],legacySources:{old:'archive'},legacyInputs:['src']};
 const audit={sources:{old:snapshot(path.join(root,'archive'),['src'])}};
 assert.equal(Object.keys(preflight(root,config,audit)).length,2);
 write(root,'archive/src/a','new work');assert.throws(()=>preflight(root,config,audit),/Historical source old changed/);
});
function visualFixture(root) {
 const png=Buffer.from('89504e470d0a1a0a00000000','hex');write(root,'register.png',png);write(root,'form.png',png);
 const config={variants:{onprem:{},cloud:{}},visualPages:['walk','kaizen'],visualWidths:[375,768,1440],visualChecks:['tables','forms','headers','buttons','navigation']};
 const states={onprem:{commit:'a'.repeat(40)},cloud:{commit:'b'.repeat(40)}};
 const review={reviewer:'Tester',reviewedAt:new Date().toISOString(),commits:{onprem:states.onprem.commit,cloud:states.cloud.commit},results:[]};
 for(const variant of Object.keys(config.variants))for(const page of config.visualPages)for(const width of config.visualWidths) review.results.push({variant,page,width,tables:'pass',forms:'pass',headers:'pass',buttons:'pass',navigation:'pass',screenshots:[{path:'register.png',sha256:hash(png)},{path:'form.png',sha256:hash(png)}]});
 return {config,states,review};
}
test('visual gate requires every page and width for the exact two commits',t=>{
 const root=fixture(t),{config,states,review}=visualFixture(root);
 validateVisual(review,states,config,root);
 const missing=structuredClone(review);missing.results.pop();assert.throws(()=>validateVisual(missing,states,config,root),/incomplete/);
 const failed=structuredClone(review);failed.results[0].forms='fail';assert.throws(()=>validateVisual(failed,states,config,root),/incomplete/);
 const stale=structuredClone(review);stale.commits.cloud='c'.repeat(40);assert.throws(()=>validateVisual(stale,states,config,root),/commit mismatch/);
 const duplicate=structuredClone(review);duplicate.results.push(duplicate.results[0]);assert.throws(()=>validateVisual(duplicate,states,config,root),/incomplete/);
});
test('visual evidence must exist, match hashes and remain within evidence folder',t=>{
 const root=fixture(t),{config,states,review}=visualFixture(root);
 const escaped=structuredClone(review);escaped.results[0].screenshots[0].path='../outside.png';assert.throws(()=>validateVisual(escaped,states,config,root),/inside/);
 write(root,'form.png','changed');assert.throws(()=>validateVisual(review,states,config,root),/Invalid or changed screenshot/);
});
test('no skip-checks or force-release options',()=>{
 assert.throws(()=>parseArgs(['release','--skip-tests','true']),/Invalid argument/);
 assert.throws(()=>parseArgs(['release','--force','true']),/Invalid argument/);
 assert.deepEqual(parseArgs(['preflight']),{command:'preflight',options:{}});
});
test('Windows release scripts have valid PowerShell syntax',{skip:process.platform!=='win32'},()=>{
 cp.execFileSync('powershell.exe',['-NoProfile','-ExecutionPolicy','Bypass','-File',path.join(__dirname,'test-powershell.ps1')],{stdio:'pipe'});
});
