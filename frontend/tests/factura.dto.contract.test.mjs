import { test } from 'node:test';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

test('el reporte tipa los campos fiscales presentes y sus valores nulos', () => {
    execFileSync(process.execPath, [
        'node_modules/typescript/bin/tsc', '--ignoreConfig', '--noEmit', '--strict', '--skipLibCheck',
        '--target', 'ES2022', '--moduleResolution', 'bundler', '--module', 'ES2022',
        'tests/factura.dto.contract.ts'
    ], { cwd: fileURLToPath(new URL('..', import.meta.url)), stdio: 'inherit' });
});
