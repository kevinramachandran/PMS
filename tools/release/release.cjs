#!/usr/bin/env node
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const cp = require('node:child_process');
const TOOL = __dirname;
const json = file => JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, ''));
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
const fileHash = file => hash(fs.readFileSync(file));
function run(exe, args, cwd, log) {
  const result = cp.spawnSync(exe, args, {cwd, encoding:'utf8', maxBuffer:64 * 1024 * 1024, windowsHide:true});
  if (log) fs.writeFileSync(log, (result.stdout || '') + (result.stderr || ''));
  if (result.error || result.status !== 0) throw new Error(`${exe} failed${log ? `; see ${log}` : ': ' + (result.stderr || result.error || result.stdout)}`);
  return result.stdout.trim();
}
function git(root, args) {
  // Trust only the explicitly selected worktree for this invocation, never global Git settings.
  return run('git', ['-c', `safe.directory=${root.replaceAll('\\','/')}`, '-C', root, ...args]);
}
function walk(root, relative = '') {
  const full = path.join(root, relative);
  if (!fs.existsSync(full)) return [];
  const stat = fs.lstatSync(full);
  if (stat.isSymbolicLink()) throw new Error(`Release inputs cannot be symlinks: ${full}`);
  if (!stat.isDirectory()) return [relative.replaceAll('\\','/')];
  return fs.readdirSync(full).sort().flatMap(name => walk(root, path.join(relative, name)));
}
function snapshot(root, inputs) {
  if (!fs.existsSync(root)) throw new Error(`Source audit location missing: ${root}`);
  return Object.fromEntries(inputs.flatMap(input => walk(root, input)).sort().map(file => [file, fileHash(path.join(root, file))]));
}
function sourceState(root, branch, inputs) {
  const actual = git(root, ['branch','--show-current']);
  if (actual !== branch) throw new Error(`Wrong release branch at ${root}: expected ${branch}, got ${actual || 'detached HEAD'}`);
  const dirty = git(root, ['status','--porcelain','--untracked-files=all','--',...inputs]);
  if (dirty) throw new Error(`Uncommitted release inputs in ${root}. Review and commit them before releasing:\n${dirty}`);
  // Ignored source files can affect Gradle too. Reject ignored files within application inputs.
  const ignored = git(root, ['ls-files','--others','--ignored','--exclude-standard','--',...inputs]);
  if (ignored) throw new Error(`Ignored application inputs could enter the build: ${ignored}`);
  return {branch:actual, commit:git(root,['rev-parse','HEAD']), files:snapshot(root, inputs)};
}
function compareSnapshot(expected, actual, label) {
  const changed = [...new Set([...Object.keys(expected),...Object.keys(actual)])].filter(file => expected[file] !== actual[file]);
  if (changed.length) throw new Error(`${label} changed; review and reconcile before release:\n${changed.join('\n')}`);
}
function legacyState(workspace, config) {
  return Object.fromEntries(Object.entries(config.legacySources).map(([key, relative]) => [key, snapshot(path.resolve(workspace, relative), config.legacyInputs)]));
}
function validateVisual(review, states, config, evidenceRoot) {
  if (!review.reviewer || !Number.isFinite(Date.parse(review.reviewedAt))) throw new Error('Visual review requires reviewer and ISO reviewedAt');
  if (Date.parse(review.reviewedAt) > Date.now() + 300000) throw new Error('Visual review cannot be future-dated');
  for (const variant of Object.keys(config.variants)) {
    if (review.commits?.[variant] !== states[variant].commit) throw new Error(`Visual review commit mismatch: ${variant}`);
    for (const page of config.visualPages) for (const width of config.visualWidths) {
      const matches = (review.results || []).filter(row => row.variant === variant && row.page === page && row.width === width);
      if (matches.length !== 1 || config.visualChecks.some(check => matches[0][check] !== 'pass')) throw new Error(`Mobile/desktop review incomplete: ${variant}/${page}/${width}`);
      const row = matches[0];
      if (!Array.isArray(row.screenshots) || row.screenshots.length < 2) throw new Error(`Provide register and open-form screenshots: ${variant}/${page}/${width}`);
      for (const shot of row.screenshots) {
        if (!shot.path || !/^[a-f0-9]{64}$/.test(shot.sha256 || '')) throw new Error('Screenshot path and SHA256 required');
        const file = path.resolve(evidenceRoot, shot.path);
        const relative = path.relative(evidenceRoot, file);
        if (relative.startsWith('..') || path.isAbsolute(relative)) throw new Error('Screenshots must be inside the visual review folder');
        const bytes = fs.readFileSync(file);
        const png = bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]));
        const jpeg = bytes[0] === 255 && bytes[1] === 216;
        if ((!png && !jpeg) || fileHash(file) !== shot.sha256) throw new Error(`Invalid or changed screenshot: ${shot.path}`);
      }
    }
  }
}
function preflight(workspace, config, audit) {
  const states = {};
  for (const [key, variant] of Object.entries(config.variants)) states[key] = sourceState(path.join(workspace,variant.directory), variant.branch, config.releaseInputs);
  const legacy = legacyState(workspace, config);
  for (const key of Object.keys(config.legacySources)) {
    if (!audit.sources?.[key]) throw new Error(`Missing reviewed source audit: ${key}`);
    compareSnapshot(audit.sources[key], legacy[key], `Historical source ${key}`);
  }
  return states;
}
function parseArgs(args) {
  const command = args.shift() || 'preflight', options = {};
  while (args.length) {
    const key = args.shift();
    if (!['--workspace','--id','--visual','--notes','--output','--deliver','--review-note'].includes(key) || !args.length || args[0].startsWith('--')) throw new Error(`Invalid argument: ${key}`);
    options[key.slice(2)] = args.shift();
  }
  return {command,options};
}
function main(args) {
  const {command,options} = parseArgs(args);
  const config = json(path.join(TOOL,'release-config.json'));
  const workspace = path.resolve(options.workspace || path.join(TOOL,'../../..'));
  if (command === 'audit-legacy') {
    if (!options['review-note']) throw new Error('Only acknowledge historical changes after reconciliation; supply --review-note describing the review');
    const target = path.join(TOOL,'legacy-audit.json');
    const result = {schemaVersion:1,reviewedAt:new Date().toISOString(),note:options['review-note'],sources:legacyState(workspace,config)};
    fs.writeFileSync(target, JSON.stringify(result,null,2)+'\n');
    console.log(`Recorded reviewed historical-source fingerprints: ${target}. Commit this audit after review.`);
    return;
  }
  if (!['preflight','release'].includes(command)) throw new Error('Use preflight, audit-legacy, or release');
  const states = preflight(workspace, config, json(path.join(TOOL,'legacy-audit.json')));
  if (command === 'preflight') {console.log('Source gates passed for both variants. Visual review, builds, and package checks still required.');return;}
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$/.test(options.id || '')) throw new Error('Provide a unique --id using letters, digits, dots, underscores or hyphens');
  if (!options.visual || !options.notes) throw new Error('Release requires --visual review.json and --notes changes.md');
  const visualPath = path.resolve(options.visual), review = json(visualPath);
  validateVisual(review, states, config, path.dirname(visualPath));
  const notes = fs.readFileSync(path.resolve(options.notes),'utf8').trim();
  if (!notes) throw new Error('Release notes cannot be empty');
  const output = path.resolve(options.output || path.join(workspace,'releases/verified'));
  const releaseRoot = path.join(output,options.id);
  if (fs.existsSync(releaseRoot)) throw new Error(`Release ID already exists: ${releaseRoot}`);
  fs.mkdirSync(releaseRoot,{recursive:true});
  const manifest = {schemaVersion:1,id:options.id,status:'building',createdAt:new Date().toISOString(),sources:states,packages:{}};
  const save = () => fs.writeFileSync(path.join(releaseRoot,'release-manifest.json'),JSON.stringify(manifest,null,2)+'\n');
  save();
  try {
    fs.writeFileSync(path.join(releaseRoot,'CHANGES.md'),notes+'\n');
    fs.writeFileSync(path.join(releaseRoot,'visual-review.json'),JSON.stringify(review,null,2)+'\n');
    fs.mkdirSync(path.join(releaseRoot,'visual-evidence'));
    for (const row of review.results) for (const shot of row.screenshots) {
      const source = path.resolve(path.dirname(visualPath),shot.path);
      const extension = path.extname(source).toLowerCase();
      fs.copyFileSync(source,path.join(releaseRoot,'visual-evidence',shot.sha256+extension));
    }
    for (const [key, variant] of Object.entries(config.variants)) {
      const root = path.join(workspace,variant.directory);
      // Both variants must carry this exact committed release toolchain.
      const toolInputs=['release.cjs','release.test.cjs','build-variant.ps1','package.ps1','metadata.gradle','release-config.json','package-hashes.json','legacy-audit.json','allowed-test-skips.json','package'];
      compareSnapshot(snapshot(TOOL,toolInputs),snapshot(path.join(root,'tools/release'),toolInputs), `${key} release tooling`);
      const metadataPath = path.join(releaseRoot,key+'-build.json');
      run('powershell.exe',['-NoProfile','-ExecutionPolicy','Bypass','-File',path.join(TOOL,'build-variant.ps1'),'-Root',root,'-Metadata',metadataPath],root,path.join(releaseRoot,key+'-build.log'));
      const metadata = json(metadataPath);
      const checks = path.join(releaseRoot,key+'-package-checks.json');
      run('powershell.exe',['-NoProfile','-ExecutionPolicy','Bypass','-File',path.join(TOOL,'package.ps1'),'-Variant',key,'-Metadata',metadataPath,'-Output',path.join(releaseRoot,variant.zip),'-Checks',checks,'-Commit',states[key].commit,'-ReleaseId',options.id],root,path.join(releaseRoot,key+'-package.log'));
      manifest.packages[key] = {file:variant.zip,sha256:fileHash(path.join(releaseRoot,variant.zip)),commit:states[key].commit,checks:json(checks),build:metadata};
    }
    // Detect edits/commits while tests and packaging were running, including historical copies.
    const finalStates = preflight(workspace,config,json(path.join(TOOL,'legacy-audit.json')));
    for (const key of Object.keys(states)) {
      if (finalStates[key].commit !== states[key].commit) throw new Error(`${key} commit changed during release`);
      compareSnapshot(states[key].files,finalStates[key].files,`${key} build inputs`);
    }
    validateVisual(review, finalStates, config, path.dirname(visualPath));
    manifest.status='verified';manifest.completedAt=new Date().toISOString();save();
    if (options.deliver) {
      const destination = path.resolve(options.deliver);
      fs.mkdirSync(destination,{recursive:true});
      const backup = path.join(destination,'previous-releases',options.id);
      if (fs.existsSync(backup)) throw new Error(`Delivery backup already exists: ${backup}`);
      fs.mkdirSync(backup,{recursive:true});
      // Both packages were verified before touching either canonical filename.
      for (const item of Object.values(manifest.packages)) {
        const target=path.join(destination,item.file);
        if(fs.existsSync(target)) fs.copyFileSync(target,path.join(backup,item.file));
      }
      try {
        for(const item of Object.values(manifest.packages)) {
          const target=path.join(destination,item.file);
          fs.copyFileSync(path.join(releaseRoot,item.file),target);
          if(fileHash(target)!==item.sha256) throw new Error(`Delivered checksum mismatch: ${item.file}`);
        }
      } catch(error) {
        for(const item of Object.values(manifest.packages)) {
          const old=path.join(backup,item.file), target=path.join(destination,item.file);
          if(fs.existsSync(old)) fs.copyFileSync(old,target);
          else if(fs.existsSync(target)) fs.unlinkSync(target);
        }
        throw error;
      }
      fs.copyFileSync(path.join(releaseRoot,'release-manifest.json'),path.join(destination,'release-'+options.id+'.json'));
    }
    console.log(`Verified both releases: ${releaseRoot}`);
  } catch(error) {manifest.status='failed';manifest.error=error.message;save();throw error;}
}
module.exports = {hash,snapshot,sourceState,compareSnapshot,validateVisual,preflight,parseArgs};
if(require.main===module) {try {main(process.argv.slice(2));} catch(error) {console.error('RELEASE BLOCKED: '+error.message);process.exitCode=1;}}
