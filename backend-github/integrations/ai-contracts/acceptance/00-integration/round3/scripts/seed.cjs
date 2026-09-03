const { root, work, evidence, run, sql } = require('./runtime.cjs')
const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const assert = require('node:assert/strict')
const quote = value => "'" + String(value).replaceAll("'", "''") + "'"
const id = value => crypto.createHash('md5').update('00-round3:' + value).digest('hex')
const accountFile = path.join(work, 'accounts.private.json')
const accounts = fs.existsSync(accountFile) ? JSON.parse(fs.readFileSync(accountFile))
  : ['owner_a', 'owner_b', 'viewer', 'nomenu'].map(name => ({
    name, username: 'ri00_' + name, id: id(name), salt: crypto.randomBytes(4).toString('hex'),
    password: crypto.randomBytes(18).toString('base64url'), infer: name.startsWith('owner'), aiMenu: name !== 'nomenu'
  }))
fs.writeFileSync(accountFile, JSON.stringify(accounts, null, 2), { mode: 0o600 })
const classes = path.join(work, 'seeder')
fs.mkdirSync(classes, { recursive: true })
run('javac', ['-d', classes,
  path.join(root, 'backend-github/jeecg-boot-base-core/src/main/java/org/jeecg/common/util/PasswordUtil.java'),
  path.join(__dirname, 'AccountPassword.java')])
const hashes = run('java', ['-cp', classes, 'AccountPassword'],
  { input: accounts.map(a => [a.username, a.password, a.salt].join('\t')).join('\n') + '\n' }).trim().split('\n')
assert.equal(hashes.length, accounts.length)
const menuRows = sql("SELECT id,COALESCE(parent_id,'') FROM sys_permission WHERE component IN ('tab/TabAiModelList','tab/TabAiHistoryList','dashboard/Analysis')").trim().split('\n')
const menus = new Set(menuRows.map(row => row.split('\t')[0]))
for (const row of menuRows) {
  let parent = row.split('\t')[1]
  while (parent) {
    menus.add(parent)
    parent = sql('SELECT COALESCE(parent_id,\'\') FROM sys_permission WHERE id=' + quote(parent)).trim()
  }
}
const inferId = id('permission:ai:infer')
const disabledId = id('permission:legacy')
const statements = [
  "INSERT INTO sys_permission(id,name,menu_type,perms,status,del_flag,is_route) VALUES(" +
    [quote(inferId), quote('Round3 AI inference'), 2, quote('ai:infer'), quote('1'), 0, 0].join(',') + ") ON DUPLICATE KEY UPDATE perms=VALUES(perms)",
  "INSERT INTO sys_permission(id,name,url,component,menu_type,status,del_flag,is_route,is_leaf,keep_alive) VALUES(" +
    [quote(disabledId), quote('停用入口验收'), quote('/round3/retired'), quote('easy'), 0, quote('1'), 0, 1, 1, 0].join(',') + ") ON DUPLICATE KEY UPDATE component=VALUES(component)"
]
accounts.forEach((a, index) => {
  const role = id('role:' + a.name)
  statements.push("INSERT INTO sys_user(id,username,realname,password,salt,status,del_flag,user_identity,create_by,create_time) VALUES(" +
    [quote(a.id), quote(a.username), quote('集成验收 ' + a.name), quote(hashes[index]), quote(a.salt), 1, 0, 1, quote('round3'), 'NOW()'].join(',') +
    ") ON DUPLICATE KEY UPDATE password=VALUES(password),salt=VALUES(salt)")
  statements.push("INSERT INTO sys_role(id,role_name,role_code,description) VALUES(" +
    [quote(role), quote('Round3 ' + a.name), quote('ri00_' + a.name), quote('00 isolated acceptance role only')].join(',') + ") ON DUPLICATE KEY UPDATE description=VALUES(description)")
  statements.push("INSERT INTO sys_user_role(id,user_id,role_id) VALUES(" +
    [quote(id('user-role:' + a.name)), quote(a.id), quote(role)].join(',') + ") ON DUPLICATE KEY UPDATE role_id=VALUES(role_id)")
  const grants = a.aiMenu ? [...menus, disabledId] : []
  if (a.infer) grants.push(inferId)
  for (const permission of grants) statements.push("INSERT INTO sys_role_permission(id,role_id,permission_id) VALUES(" +
    [quote(id(a.name + ':' + permission)), quote(role), quote(permission)].join(',') + ") ON DUPLICATE KEY UPDATE permission_id=VALUES(permission_id)")
})
const capability = { snapshot: { capabilityCode: 'image-detection.v1', capabilityVersion: 'mock-v1', providerKey: 'mock',
  adapterId: 'mock-v1', providerCapabilityCode: 'image-detection.v1', providerVersion: null,
  features: { query: false, cancel: false, deduplication: false } }, displayName: '模拟图片检测', enabled: true,
  available: true, simulated: true, unavailableReason: '', inputMediaTypes: ['image/png', 'image/jpeg'],
  maxInputBytes: 10485760, maxOutputBytes: 10485760, maxWaitMillis: 1500 }
statements.push("INSERT INTO ai_capability_binding(capability_code,descriptor_json) VALUES('image-detection.v1'," +
  quote(JSON.stringify(capability)) + ") ON DUPLICATE KEY UPDATE descriptor_json=VALUES(descriptor_json)")
sql('START TRANSACTION;\n' + statements.join(';\n') + ';\nCOMMIT;')
fs.writeFileSync(path.join(evidence, 'seed.json'), JSON.stringify({ status: 'PASS', database: '00 isolated only',
  accounts: accounts.map(({ name, id, infer, aiMenu }) => ({ name, id, infer, aiMenu })), capability,
  note: 'Only explicit acceptance users/roles granted ai:infer; no production-wide grants; credentials excluded.' }, null, 2) + '\n')
console.log('Prepared four isolated acceptance accounts and explicit simulated capability. Credentials remain private.')
