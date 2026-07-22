import assert from 'node:assert/strict';
import test from 'node:test';
import { highlightContent, highlightVariables } from '../../src/utils/syntax-highlight.ts';

test('highlightVariables marks plain variables', () => {
  const html = highlightVariables('hello ${name}');
  assert.match(html, /<mark>\$\{name\}<\/mark>/);
});

test('highlightVariables marks function calls with parentheses', () => {
  const html = highlightVariables('id=${__UUID()}');
  assert.match(html, /<mark>\$\{__UUID\(\)\}<\/mark>/);
});

test('highlightVariables marks function calls with simple args', () => {
  const html = highlightVariables('${__Random(1,100)}');
  assert.match(html, /<mark>\$\{__Random\(1,100\)\}<\/mark>/);
});

test('highlightContent text mode marks both variable and function', () => {
  const html = highlightContent('${token} ${__time()}', 'text');
  assert.match(html, /<mark>\$\{token\}<\/mark>/);
  assert.match(html, /<mark>\$\{__time\(\)\}<\/mark>/);
});

test('highlightContent json mode marks function inside string', () => {
  const html = highlightContent('{"id":"${__UUID()}"}', 'json');
  assert.match(html, /<mark>\$\{__UUID\(\)\}<\/mark>/);
});
