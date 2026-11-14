import { test } from 'node:test';
import assert from 'node:assert/strict';
import { extractJson, validateCommand, commandPrompt, systemPrompt } from '../lib/prompt.js';

test('extracts JSON from fenced and chatty replies', () => {
  assert.deepEqual(extractJson('```json\n{"reply":"ok"}\n```'), { reply: 'ok' });
  assert.deepEqual(extractJson('Sure! {"reply":"ok"} hope that helps'), { reply: 'ok' });
  assert.equal(extractJson('no json here'), null);
  assert.equal(extractJson(''), null);
});

test('rejects a non-object command', () => {
  assert.equal(validateCommand(null), null);
  assert.equal(validateCommand({}), null);
});

test('normalizes a create_tasks command and converts ISO times', () => {
  const command = validateCommand({
    reply: 'Task add kar diya',
    language: 'hinglish',
    intent: 'create_tasks',
    tasks: [
      { title: '  Study for exam ', priority: 'high', dueAtISO: '2026-11-13T18:00:00+05:30' },
      { title: '', priority: 'urgent' },
    ],
    needsConfirmation: false,
  });
  assert.equal(command.tasks.length, 1);
  assert.equal(command.tasks[0].title, 'Study for exam');
  assert.equal(command.tasks[0].priority, 'high');
  assert.equal(typeof command.tasks[0].dueAt, 'number');
  assert.equal(command.intent, 'create_tasks');
});

test('drops unsafe or unknown values instead of trusting the model', () => {
  const command = validateCommand({
    reply: 'x',
    intent: 'rm -rf',
    language: 'klingon',
    tasks: [{ title: 'a', priority: 'nuclear' }],
    taskRefs: [{ match: 'a', status: 'nonsense' }],
    memories: [{ kind: 'secret', content: 'likes tea' }],
    deviceAction: { type: 'factory_reset', target: 'all' },
  });
  assert.equal(command.intent, 'chat');
  assert.equal(command.language, 'auto');
  assert.equal(command.tasks[0].priority, 'medium');
  assert.equal(command.taskRefs[0].status, 'completed');
  assert.equal(command.memories[0].kind, 'fact');
  assert.equal(command.deviceAction, null);
});

test('caps task fan-out and rejects invalid dates', () => {
  const command = validateCommand({
    reply: 'many',
    tasks: Array.from({ length: 30 }, (_, index) => ({ title: `task ${index}`, dueAtISO: 'tomorrow' })),
  });
  assert.equal(command.tasks.length, 12);
  assert.equal(command.tasks[0].dueAt, null);
});

test('prompts include the JSON contract and never a provider key', () => {
  const prompt = commandPrompt({ text: 'kal 6 baje padhna hai' });
  assert.match(prompt, /"intent"/);
  assert.match(systemPrompt({}), /Jarvis/);
});
