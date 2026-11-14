// Bundles the backend into dist/ for file-based Vercel deployment.
//
//   node scripts/bundle.mjs
//   # -> dist/api/[...path].js, dist/package.json, dist/vercel.json
//
// The bundled output is the same code the test suite runs; bundling only removes
// the need to upload a file per module. `node --test` still runs against the
// modular source.
import { build } from 'esbuild';
import { mkdir, writeFile, copyFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const outDir = resolve(root, 'dist');

await mkdir(resolve(outDir, 'api'), { recursive: true });

await build({
  entryPoints: [resolve(root, 'scripts/bundle-entry.js')],
  outfile: resolve(outDir, 'api/[...path].js'),
  bundle: true,
  minify: true,
  // Wrapped so the artifact stays inspectable line by line in deployments and diffs.
  lineLimit: 120,
  format: 'esm',
  platform: 'node',
  target: 'node20',
  banner: {
    js: '// Jarvis backend — bundled from server/ (api/, lib/). Built for Vercel file deployment.',
  },
  legalComments: 'none',
});

await writeFile(
  resolve(outDir, 'package.json'),
  JSON.stringify({ name: 'jarvis-backend', private: true, type: 'module', engines: { node: '>=20' } }, null, 2),
);

await writeFile(
  resolve(outDir, 'vercel.json'),
  JSON.stringify(
    {
      $schema: 'https://openapi.vercel.sh/vercel.json',
      functions: { 'api/**/*.js': { maxDuration: 60 } },
      headers: [
        {
          source: '/api/(.*)',
          headers: [{ key: 'X-Content-Type-Options', value: 'nosniff' }],
        },
      ],
    },
    null,
    2,
  ),
);

console.log('bundled backend ->', outDir);
