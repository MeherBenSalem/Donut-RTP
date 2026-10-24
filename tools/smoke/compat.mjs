// Turns a smoke report into marketing/source/compat.json, the data behind the compatibility card.
// Usage: node tools/smoke/compat.mjs [report.json]   (default: the newest report in $RTPLAB_HOME/reports)
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..', '..');
const lab = process.env.RTPLAB_HOME || path.join(os.homedir(), '.cache', 'donutrtp-smoke');
const reports = path.join(lab, 'reports');
const file = process.argv[2] || path.join(reports, fs.readdirSync(reports).filter((f) => /^smoke-.*\.json$/.test(f)).sort().at(-1));
const results = JSON.parse(fs.readFileSync(file, 'utf8'));
const version = fs.readFileSync(path.join(repo, 'build.gradle'), 'utf8').match(/^version = '([^']+)'/m);

const compat = {
  plugin: version ? version[1] : null,
  date: new Date(fs.statSync(file).mtimeMs).toISOString().slice(0, 10),
  targets: results.map((r) => {
    const [project, gameVersion] = r.target.split(/-(.+)/);
    return { project, version: gameVersion, java: r.java, client: r.bot, economy: r.economy, worldGuard: r.hooks.worldGuard,
      griefPrevention: r.hooks.griefPrevention, passed: r.steps.filter((s) => s.ok).length, steps: r.steps.length,
      errors: r.errors.length, ok: r.ok };
  }),
};
const out = path.join(repo, 'marketing', 'source', 'compat.json');
fs.writeFileSync(out, JSON.stringify(compat, null, 2) + '\n');
console.log(`${path.relative(repo, out)}: ${compat.targets.filter((t) => t.ok).length}/${compat.targets.length} targets pass (from ${file})`);
