import { mkdir, readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const __dirname = dirname(fileURLToPath(import.meta.url));
const rootDir = resolve(__dirname, '..');
const sourceSvgPath = resolve(rootDir, 'public/brand/familion-icon.svg');
const outputDir = resolve(rootDir, 'public/icons');

const ICON_SIZES = [72, 96, 128, 144, 152, 192, 384, 512];
const MASKABLE_SIZE = 512;
const MASKABLE_INNER_SIZE = 384;
const BRAND_ACCENT = '#0f766e';

async function generateIcons() {
  await mkdir(outputDir, { recursive: true });

  const rawSvg = await readFile(sourceSvgPath, 'utf8');
  // Resolve CSS custom property fallbacks because librsvg does not evaluate var()
  const resolvedSvg = rawSvg.replace(/var\([^,]+,\s*([^)]+)\)/g, '$1');
  const svgBuffer = Buffer.from(resolvedSvg, 'utf8');

  for (const size of ICON_SIZES) {
    const outPath = resolve(outputDir, `icon-${size}x${size}.png`);
    await sharp(svgBuffer, { density: 600 }).resize(size, size).png().toFile(outPath);
  }

  const innerBuffer = await sharp(svgBuffer, { density: 600 })
    .resize(MASKABLE_INNER_SIZE, MASKABLE_INNER_SIZE)
    .png()
    .toBuffer();

  const maskablePath = resolve(outputDir, `icon-${MASKABLE_SIZE}x${MASKABLE_SIZE}-maskable.png`);
  await sharp({
    create: {
      width: MASKABLE_SIZE,
      height: MASKABLE_SIZE,
      channels: 4,
      background: BRAND_ACCENT,
    },
  })
    .composite([{ input: innerBuffer, gravity: 'centre' }])
    .png()
    .toFile(maskablePath);

  console.log(`Generated ${ICON_SIZES.length + 1} PWA icons in ${outputDir}`);
}

generateIcons().catch((error) => {
  console.error('Failed to generate PWA icons:', error);
  process.exit(1);
});
