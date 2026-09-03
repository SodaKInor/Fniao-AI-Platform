const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = path.resolve(__dirname, '../../../../../..')
const frontend = path.join(root, 'frontend-vue')
const babel = require(path.join(frontend, 'node_modules/@babel/core'))
const compiler = require(path.join(frontend, 'node_modules/vue-template-compiler'))

function loadSource(relative, mocks = {}, globals = {}) {
  const filename = path.join(frontend, 'src', relative)
  let source = fs.readFileSync(filename, 'utf8')
  if (filename.endsWith('.vue')) source = compiler.parseComponent(source).script.content
  const { code } = babel.transformSync(source, {
    babelrc: false, configFile: false,
    plugins: [require(path.join(frontend, 'node_modules/@babel/plugin-transform-modules-commonjs'))]
  })
  const module = { exports: {} }
  const localRequire = name => {
    if (Object.prototype.hasOwnProperty.call(mocks, name)) return mocks[name]
    if (name.startsWith('./')) {
      const target = path.join(path.dirname(relative), name)
      return loadSource(target.endsWith('.js') ? target : target + '.js', mocks, globals)
    }
    return {}
  }
  vm.runInNewContext(code, { module, exports: module.exports, require: localRequire,
    setTimeout, clearTimeout, Blob, FormData, URL, Uint8Array, console, ...globals }, { filename })
  return module.exports
}
module.exports = { loadSource, root, frontend, compiler }
