#!/usr/bin/env node
/*
 * Zero-dependency site checker for English Quest.
 * Run: node scripts/check-site.mjs
 *
 * Checks:
 *   1. Every local href/src in every .html file resolves to a real file.
 *   2. Every inline <script> block parses as valid JavaScript.
 *   3. Reports CSS class selectors in shared/css that appear to be unused
 *      anywhere in .html/.js (informational only — does not fail the run).
 *
 * Exits non-zero if (1) or (2) find problems, so it can be used as a CI gate.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const IGNORE_DIRS = new Set(['.git', 'node_modules', '.rtk', '.claude']);

function walk(dir, exts, out) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (IGNORE_DIRS.has(entry.name)) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(full, exts, out);
    } else if (exts.some((ext) => entry.name.endsWith(ext))) {
      out.push(full);
    }
  }
  return out;
}

const htmlFiles = walk(ROOT, ['.html'], []);
const cssFiles = walk(path.join(ROOT, 'shared', 'css'), ['.css'], []);
const jsFiles = walk(ROOT, ['.js'], []);

let problems = 0;

// ---------------------------------------------------------------------------
// 1. Broken local link check
// ---------------------------------------------------------------------------

function isCheckableRef(ref) {
  if (!ref) return false;
  if (/^(https?:)?\/\//.test(ref)) return false;
  if (/^(mailto|tel|javascript|data):/.test(ref)) return false;
  if (ref.startsWith('#')) return false;
  // Reject anything that looks like a JS expression fragment picked up by
  // accident (string concatenation, template literals, quotes, spaces).
  if (/['"+`]| |\$\{/.test(ref)) return false;
  return true;
}

const attrRe = /(?:href|src)="([^"]*)"/g;
const brokenLinks = [];

for (const file of htmlFiles) {
  const html = fs.readFileSync(file, 'utf8');
  const dir = path.dirname(file);
  let m;
  while ((m = attrRe.exec(html))) {
    let ref = m[1];
    if (!isCheckableRef(ref)) continue;
    ref = ref.split('#')[0].split('?')[0];
    if (!ref) continue;
    let target = ref.startsWith('/') ? path.join(ROOT, ref) : path.join(dir, ref);
    if (ref.endsWith('/')) target = path.join(target, 'index.html');
    if (!fs.existsSync(target)) {
      brokenLinks.push(`${path.relative(ROOT, file)}  ->  ${ref}`);
    }
  }
}

if (brokenLinks.length) {
  problems += brokenLinks.length;
  console.log(`\n[BROKEN LINKS] ${brokenLinks.length} found:`);
  brokenLinks.forEach((line) => console.log('  ' + line));
} else {
  console.log(`[OK] Local links checked across ${htmlFiles.length} HTML files — none broken.`);
}

// ---------------------------------------------------------------------------
// 2. Inline <script> parse check
// ---------------------------------------------------------------------------

const scriptBlockRe = /<script(\s[^>]*)?>([\s\S]*?)<\/script>/g;
let scriptCount = 0;
const scriptErrors = [];

for (const file of htmlFiles) {
  const html = fs.readFileSync(file, 'utf8');
  let m;
  let idx = 0;
  while ((m = scriptBlockRe.exec(html))) {
    const attrs = m[1] || '';
    if (/\bsrc=/.test(attrs)) continue; // external script, nothing inline to parse
    if (/type=["'](?!text\/javascript)[^"']+["']/.test(attrs)) continue; // e.g. application/json
    idx++;
    const body = m[2];
    if (!body.trim()) continue;
    scriptCount++;
    try {
      new Function(body);
    } catch (err) {
      scriptErrors.push(`${path.relative(ROOT, file)} script #${idx}: ${err.message}`);
    }
  }
}

if (scriptErrors.length) {
  problems += scriptErrors.length;
  console.log(`\n[SCRIPT PARSE ERRORS] ${scriptErrors.length} found:`);
  scriptErrors.forEach((line) => console.log('  ' + line));
} else {
  console.log(`[OK] ${scriptCount} inline <script> blocks parsed cleanly.`);
}

// ---------------------------------------------------------------------------
// 3. CSS class usage report (informational — does not affect exit code)
// ---------------------------------------------------------------------------

function extractClassSelectors(cssText) {
  const noComments = cssText.replace(/\/\*[\s\S]*?\*\//g, '');
  const classes = new Set();
  const re = /(?<![\w-])\.(-?[a-zA-Z_][\w-]*)/g;
  let m;
  while ((m = re.exec(noComments))) classes.add(m[1]);
  return classes;
}

const allClasses = new Map(); // className -> Set(cssFile)
for (const file of cssFiles) {
  const css = fs.readFileSync(file, 'utf8');
  for (const cls of extractClassSelectors(css)) {
    if (!allClasses.has(cls)) allClasses.set(cls, new Set());
    allClasses.get(cls).add(path.relative(ROOT, file));
  }
}

const usageHaystacks = [...htmlFiles, ...jsFiles]
  .filter((f) => !f.endsWith('.css'))
  .map((f) => fs.readFileSync(f, 'utf8'))
  .join('\n');

const unused = [];
for (const [cls, files] of allClasses) {
  const wordRe = new RegExp(`\\b${cls.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, '\\$&')}\\b`);
  if (!wordRe.test(usageHaystacks)) {
    unused.push({ cls, files: [...files] });
  }
}

console.log(`\n[INFO] ${allClasses.size} distinct CSS class selectors found across shared/css.`);
if (unused.length) {
  console.log(`[INFO] ${unused.length} appear unused anywhere in .html/.js (heuristic — verify before removing):`);
  unused
    .sort((a, b) => a.cls.localeCompare(b.cls))
    .forEach(({ cls, files }) => console.log(`  .${cls}  (${files.join(', ')})`));
} else {
  console.log('[OK] No obviously unused CSS classes found.');
}

// ---------------------------------------------------------------------------

console.log(`\n${problems === 0 ? 'PASS' : 'FAIL'}: ${problems} blocking problem(s) found.`);
process.exit(problems === 0 ? 0 : 1);
