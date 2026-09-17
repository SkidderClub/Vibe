// Run: npm install --prefix build/chess-assets @resvg/resvg-js@2.6.2
//      node tools/import-lichess-pieces.cjs
// Rasterizes the original GPL-2.0-or-later SVGs without altering their artwork.
const fs = require('node:fs/promises');
const path = require('node:path');
const { Resvg } = require('../build/chess-assets/node_modules/@resvg/resvg-js');
const commit = '60acb9d51787f60fd4504b614d83114857010ed1';
async function main() {
    const directory = path.join(__dirname, '../src/main/resources/assets/vibe/chess');
    await fs.mkdir(directory, { recursive: true });
    for (const color of ['w', 'b']) for (const piece of ['K', 'Q', 'R', 'B', 'N', 'P']) {
        const name = color + piece;
        const response = await fetch(`https://raw.githubusercontent.com/lichess-org/lila/${commit}/public/piece/cburnett/${name}.svg`);
        if (!response.ok) throw new Error(`${name}: ${response.status}`);
        const svg = await response.text();
        await fs.writeFile(path.join(directory, name + '.svg'), svg);
        const png = new Resvg(svg, { fitTo: { mode: 'width', value: 128 } }).render().asPng();
        await fs.writeFile(path.join(directory, name + '.png'), png);
    }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
