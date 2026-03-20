import { spawn } from 'node:child_process'

const args = process.argv.slice(2)
const env = {
  ...process.env,
  SASS_SILENCE_DEPRECATIONS: [process.env.SASS_SILENCE_DEPRECATIONS, 'legacy-js-api']
    .filter(Boolean)
    .join(','),
}

const child = process.platform === 'win32'
  ? spawn('cmd.exe', ['/d', '/s', '/c', 'npx vite ' + args.join(' ')], {
      stdio: 'inherit',
      env,
    })
  : spawn('npx', ['vite', ...args], {
      stdio: 'inherit',
      env,
    })

child.on('exit', (code) => {
  process.exit(code ?? 0)
})
