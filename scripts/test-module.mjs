// Recovery tests rewrite every absolute device path into a disposable host fixture.
// No device partitions or user APKs are touched.
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {spawnSync} from 'node:child_process';
import assert from 'node:assert/strict';
const root = path.resolve(import.meta.dirname, '..');
const bash = process.env.BASH_EXE || (process.platform === 'win32' ? 'C:/Program Files/Git/bin/bash.exe' : 'bash');
const posix = p => p.replaceAll('\\', '/').replace(/^([A-Z]):/i, (_, letter) => '/' + letter.toLowerCase());
const run = (code, cwd) => spawnSync(bash, ['-c', code], {cwd, encoding:'utf8', env:{...process.env, MSYS_NO_PATHCONV:'1'}});
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'call-recorder-tests-'));
let count = 0;
try {
  for (const file of ['magisk/update-binary', 'magisk-module/META-INF/com/google/android/update-binary', 'magisk-module/customize.sh', 'magisk-module/recovery-install.sh']) {
    const r = spawnSync(bash, ['-n', posix(path.join(root, file))], {encoding:'utf8'});
    assert.equal(r.status, 0, r.stderr); count++;
  }
  assert.equal(fs.readFileSync(path.join(root,'magisk/update-binary'),'utf8'), fs.readFileSync(path.join(root,'magisk-module/META-INF/com/google/android/update-binary'),'utf8'));
  for (const manager of ['magisk', 'ksu', 'ksu-next', 'apatch']) {
    const dir = path.join(tmp, manager); fs.mkdirSync(dir);
    fs.cpSync(path.join(root,'magisk-module'), path.join(dir,'module'), {recursive:true});
    const r = run(`API=34; MODPATH='${posix(path.join(dir,'module'))}'; KSU=${manager.startsWith('ksu')}; APATCH=${manager==='apatch'}; ui_print() { echo "$*"; }; abort() { echo "$*"; exit 1; }; set_perm_recursive() { :; }; set_perm() { :; }; . '${posix(path.join(root,'magisk-module/customize.sh'))}'`, dir);
    assert.equal(r.status,0,r.stdout+r.stderr); count++;
  }
  const oldAndroid = run(`API=28; ui_print() { :; }; abort() { exit 9; }; . '${posix(path.join(root,'magisk-module/customize.sh'))}'`, tmp);
  assert.equal(oldAndroid.status,9); count++;
  const original = fs.readFileSync(path.join(root,'magisk-module/recovery-install.sh'),'utf8');
  for (const scenario of ['magisk','ksu','ksu-next','apatch','system','system-root','system-nested','readonly','rollback','android9','labels','ownership','data-unavailable','ksu-labels']) {
    const dir = path.join(tmp, 'recovery-'+scenario); fs.mkdirSync(dir);
    const prefix = posix(dir);
    for (const folder of ['work','data/adb','system/priv-app','system/etc/permissions','system/addon.d']) fs.mkdirSync(path.join(dir,folder),{recursive:true});
    if (['magisk','ksu','ksu-next','apatch'].includes(scenario)) fs.mkdirSync(path.join(dir,'data/adb',scenario==='apatch'?'ap':scenario==='magisk'?'magisk':'ksu'),{recursive:true});
    if (scenario==='ksu-labels') fs.mkdirSync(path.join(dir,'data/adb/ksu'));
    if (scenario==='data-unavailable') {
      fs.mkdirSync(path.join(dir,'data/adb/ksu'));
      fs.writeFileSync(path.join(dir,'data/adb/modules'),'not a mounted modules directory');
    }
    if (scenario==='android9') fs.writeFileSync(path.join(dir,'system/build.prop'),'ro.build.version.sdk=28\n');
    if (scenario==='system-root' || scenario==='system-nested') {
      const target = scenario==='system-root'?'system_root/system':'system/system';
      fs.mkdirSync(path.join(dir,target),{recursive:true});
      fs.renameSync(path.join(dir,'system/priv-app'),path.join(dir,target,'priv-app'));
      fs.renameSync(path.join(dir,'system/etc'),path.join(dir,target,'etc'));
    }
    const rewritten = original.replaceAll('/data/adb',prefix+'/data/adb').replaceAll('/system_root',prefix+'/system_root').replaceAll(' /system;',` ${prefix}/system;`).replaceAll('mount /system ',`mount ${prefix}/system `).replaceAll('rw /system ',`rw ${prefix}/system `).replaceAll(' /system/system ',` ${prefix}/system/system `);
    // The payload archive is mocked to copy ONLY the fixed module files into WORK.
    fs.writeFileSync(path.join(dir,'installer.sh'),rewritten);
    const pre = scenario==='rollback' ? `mkdir -p '${prefix}/system/priv-app/zoro.benojir.callrecorder'; echo oldapk > '${prefix}/system/priv-app/zoro.benojir.callrecorder/app-release.apk'; echo oldxml > '${prefix}/system/etc/permissions/privapp-permissions-zoro.benojir.callrecorder.xml'; mv() { case "$1" in *.xml.new) return 1;; esac; command mv "$@"; };` : '';
    const r = run(`WORK='${prefix}/work'; ZIPFILE=unused; ui_print() { echo "$*"; }; abort() { echo "ABORT: $*"; exit 9; }; mount() { return 1; }; chown() { ${scenario==='ownership'?'return 1;':':;'} }; chcon() { ${scenario==='labels'||scenario==='ksu-labels'?'return 1;':':;'} }; unzip() { cp -R '${posix(path.join(root,'magisk-module'))}/.' "$WORK/"; }; ${pre} ${scenario==='readonly'?`mkdir -p '${prefix}/system/priv-app/.call-recorder-write-'$$;`:''} . '${prefix}/installer.sh'`, dir);
    if (scenario==='readonly') {
      assert.equal(r.status,9,r.stdout+r.stderr);
      assert.match(r.stdout,/System is read-only/);
      assert.ok(!fs.existsSync(path.join(dir,'system/priv-app/zoro.benojir.callrecorder')));
    } else if (scenario==='rollback') {
      assert.equal(r.status,9,r.stdout+r.stderr);
      assert.equal(fs.readFileSync(path.join(dir,'system/priv-app/zoro.benojir.callrecorder/app-release.apk'),'utf8').trim(),'oldapk');
      assert.equal(fs.readFileSync(path.join(dir,'system/etc/permissions/privapp-permissions-zoro.benojir.callrecorder.xml'),'utf8').trim(),'oldxml');
    } else if (['android9','labels','ownership','data-unavailable','ksu-labels'].includes(scenario)) {
      assert.equal(r.status,9,r.stdout+r.stderr);
      assert.ok(!fs.existsSync(path.join(dir,'system/priv-app/zoro.benojir.callrecorder')));
      assert.ok(!fs.existsSync(path.join(dir,'system/etc/permissions/privapp-permissions-zoro.benojir.callrecorder.xml')));
      assert.ok(!fs.existsSync(path.join(dir,'data/adb/modules/zoro.benojir.callrecorder')));
    } else {
      assert.equal(r.status,0,r.stdout+r.stderr);
      const base = ['magisk','ksu','ksu-next','apatch'].includes(scenario)?'data/adb/modules/zoro.benojir.callrecorder/system':scenario==='system-root'?'system_root/system':scenario==='system-nested'?'system/system':'system';
      assert.ok(fs.existsSync(path.join(dir,base,'priv-app/zoro.benojir.callrecorder/app-release.apk')));
      assert.ok(fs.existsSync(path.join(dir,base,'etc/permissions/privapp-permissions-zoro.benojir.callrecorder.xml')));
    }
    count++;
  }
  console.log(`${count} module installer checks passed (host fixtures; device flashing still needs hardware testing).`);
} finally {
  const resolved = fs.realpathSync(tmp);
  assert.ok(path.basename(resolved).startsWith('call-recorder-tests-') && path.dirname(resolved)===fs.realpathSync(os.tmpdir()));
  fs.rmSync(resolved,{recursive:true,force:true});
}
