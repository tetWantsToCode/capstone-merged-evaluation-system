const fs = require("fs");
const path = require("path");
const ts = require("typescript");

const SRC_DIR = path.join(__dirname, "..", "src");

function isTsLike(file) {
  return (file.endsWith(".ts") || file.endsWith(".tsx")) && !file.endsWith(".d.ts");
}

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(fullPath, files);
    } else if (isTsLike(fullPath)) {
      files.push(fullPath);
    }
  }
  return files;
}

const files = walk(SRC_DIR);

const compilerOptions = {
  jsx: ts.JsxEmit.Preserve,
  target: ts.ScriptTarget.ES2020,
  module: ts.ModuleKind.ESNext,
  moduleResolution: ts.ModuleResolutionKind.Bundler,
  isolatedModules: true,
  allowJs: true,
};

for (const file of files) {
  const source = fs.readFileSync(file, "utf8");
  const { outputText } = ts.transpileModule(source, { compilerOptions, fileName: file });
  const ext = path.extname(file);
  const base = file.slice(0, -ext.length);
  const newExt = ext === ".tsx" ? ".jsx" : ".js";
  const outPath = base + newExt;
  fs.writeFileSync(outPath, outputText, "utf8");
  fs.unlinkSync(file);
}

console.log("Transpiled TS/TSX files:", files.length);

