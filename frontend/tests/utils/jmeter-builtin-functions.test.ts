import assert from 'node:assert/strict';
import test from 'node:test';
import { jmeterBuiltinFunctions } from '../../src/utils/jmeter-builtin-functions.ts';

test('jmeterBuiltinFunctions exports common built-ins with example syntax', () => {
  assert.ok(jmeterBuiltinFunctions.length >= 8);
  for (const item of jmeterBuiltinFunctions) {
    assert.ok(item.key);
    assert.ok(item.displayName);
    assert.ok(item.category);
    assert.ok(item.description);
    assert.match(item.example, /^\$\{__.+\}$/);
  }
  const keys = new Set(jmeterBuiltinFunctions.map((item) => item.key));
  assert.ok(keys.has('__UUID'));
  assert.ok(keys.has('__time'));
  assert.ok(keys.has('__Random'));
});
