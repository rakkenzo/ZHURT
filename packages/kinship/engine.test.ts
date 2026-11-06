import { test } from 'node:test';
import assert from 'node:assert/strict';
import { KinshipGraph, kinshipTerm, suggestNext, suggestedCircle, type Person, type Relation } from './engine.ts';

// Семья для тестов: я (Нұрлан, м, 1990)
const P: Person[] = [
  { id: 'me', name: 'Нұрлан', gender: 'm', birthYear: 1990 },
  { id: 'father', name: 'Сейіт', gender: 'm', birthYear: 1960 },
  { id: 'mother', name: 'Айгүл', gender: 'f', birthYear: 1963 },
  { id: 'm_father', name: 'Қасым', gender: 'm', birthYear: 1935 },
  { id: 'm_brother', name: 'Серік', gender: 'm', birthYear: 1966 },
  { id: 'm_brother_wife', name: 'Гүлнар', gender: 'f' },
  { id: 'm_sister', name: 'Роза', gender: 'f', birthYear: 1968 },
  { id: 'm_sister_kid', name: 'Арман', gender: 'm', birthYear: 1995 },
  { id: 'f_father', name: 'Бекен', gender: 'm', birthYear: 1930 },
  { id: 'f_brother', name: 'Ерлан', gender: 'm', birthYear: 1958 },
  { id: 'f_brother_kid', name: 'Дәурен', gender: 'm', birthYear: 1985 },
  { id: 'older_sis', name: 'Самал', gender: 'f', birthYear: 1986 },
  { id: 'older_sis_husband', name: 'Бауыржан', gender: 'm', birthYear: 1984 },
  { id: 'older_sis_kid', name: 'Әли', gender: 'm', birthYear: 2012 },
  { id: 'young_sis', name: 'Дана', gender: 'f', birthYear: 1995 },
  { id: 'young_sis_husband', name: 'Ержан', gender: 'm', birthYear: 1993 },
  { id: 'ini', name: 'Бек', gender: 'm', birthYear: 1993 },
  { id: 'ini_wife', name: 'Мөлдір', gender: 'f', birthYear: 1996 },
  { id: 'aga', name: 'Талғат', gender: 'm', birthYear: 1987 },
  { id: 'aga_wife', name: 'Жанар', gender: 'f', birthYear: 1988 },
  { id: 'wife', name: 'Дина', gender: 'f', birthYear: 1992 },
  { id: 'wife_father', name: 'Мұрат', gender: 'm', birthYear: 1962 },
  { id: 'wife_mother', name: 'Роза', gender: 'f', birthYear: 1965 },
  { id: 'wife_older_bro', name: 'Асқар', gender: 'm', birthYear: 1988 },
  { id: 'wife_young_sis', name: 'Аружан', gender: 'f', birthYear: 1998 },
  { id: 'wife_older_sis', name: 'Әсем', gender: 'f', birthYear: 1989 },
  { id: 'wife_older_sis_husband', name: 'Қайрат', gender: 'm', birthYear: 1987 },
  { id: 'son', name: 'Қуаныш', gender: 'm', birthYear: 2015 },
  { id: 'daughter', name: 'Альфия', gender: 'f', birthYear: 2016 },
  { id: 'unknown_age_bro', name: 'Марат', gender: 'm' },
];

const c = (from: string, to: string, kind: Relation['kind']): Relation => ({ from, to, kind, status: 'confirmed' });
const R: Relation[] = [
  c('father', 'me', 'parent'), c('mother', 'me', 'parent'), c('father', 'mother', 'spouse'),
  c('m_father', 'mother', 'parent'), c('m_father', 'm_brother', 'parent'), c('m_father', 'm_sister', 'parent'),
  c('m_brother', 'm_brother_wife', 'spouse'), c('m_sister', 'm_sister_kid', 'parent'),
  c('f_father', 'father', 'parent'), c('f_father', 'f_brother', 'parent'), c('f_brother', 'f_brother_kid', 'parent'),
  c('father', 'older_sis', 'parent'), c('older_sis', 'older_sis_husband', 'spouse'), c('older_sis', 'older_sis_kid', 'parent'),
  c('father', 'young_sis', 'parent'), c('young_sis', 'young_sis_husband', 'spouse'),
  c('father', 'ini', 'parent'), c('ini', 'ini_wife', 'spouse'),
  c('father', 'aga', 'parent'), c('aga', 'aga_wife', 'spouse'),
  c('me', 'wife', 'spouse'),
  c('wife_father', 'wife', 'parent'), c('wife_mother', 'wife', 'parent'),
  c('wife_father', 'wife_older_bro', 'parent'), c('wife_father', 'wife_young_sis', 'parent'),
  c('wife_father', 'wife_older_sis', 'parent'), c('wife_older_sis', 'wife_older_sis_husband', 'spouse'),
  c('me', 'son', 'parent'), c('me', 'daughter', 'parent'),
  c('father', 'unknown_age_bro', 'parent'),
];

const graph = new KinshipGraph(P, R);
const term = (id: string) => kinshipTerm(graph, 'me', id)!;

test('базовые: әке, ана, әйел, ұл, қыз', () => {
  assert.equal(term('father').code, 'ake');
  assert.equal(term('mother').code, 'ana');
  assert.equal(term('wife').code, 'aiel');
  assert.equal(term('son').code, 'ul');
  assert.equal(term('daughter').code, 'kyz');
});

test('ТЗ: добавление матери → Нағашы (ата, аға, апа, жеңге, бөле)', () => {
  assert.equal(term('m_father').code, 'nagashy_ata');
  assert.equal(term('m_brother').code, 'nagashy_aga');
  assert.equal(term('m_sister').code, 'nagashy_apa');
  assert.equal(term('m_brother_wife').code, 'nagashy_zhenge');
  assert.equal(term('m_sister_kid').code, 'bole');
  const s = suggestNext(graph, 'me', 'mother').map(x => x.expectedCode);
  assert.deepEqual(s, ['nagashy_ata', 'nagashy_azhe', 'nagashy_aga', 'nagashy_apa']);
});

test('Өз жұрт: ата, аға (брат отца), его ребёнок как брат', () => {
  assert.equal(term('f_father').code, 'ata');
  assert.equal(term('f_brother').code, 'aga_ake');
  assert.equal(term('f_brother_kid').code, 'aga'); // старше меня
});

test('ТЗ: іні и предложение супруги как келін; аға и жеңге', () => {
  assert.equal(term('ini').code, 'ini');
  assert.equal(term('ini_wife').code, 'kelin');
  assert.equal(term('aga_wife').code, 'zhenge');
  assert.equal(suggestNext(graph, 'me', 'ini')[0].expectedCode, 'kelin');
});

test('ТЗ: әпке, жезде, жиен; қарындас и күйеу бала', () => {
  assert.equal(term('older_sis').code, 'apke');
  assert.equal(term('older_sis_husband').code, 'zhezde');
  assert.equal(term('older_sis_kid').code, 'zhien');
  assert.equal(term('young_sis').code, 'karyndas'); // говорящий — мужчина
  assert.equal(term('young_sis_husband').code, 'kuieu_bala');
});

test('сіңлі — для говорящей женщины', () => {
  const g2 = new KinshipGraph(
    [{ id: 'a', name: 'Әсел', gender: 'f', birthYear: 1990 }, { id: 'b', name: 'Дана', gender: 'f', birthYear: 1995 }, { id: 'p', name: 'Ана', gender: 'f' }],
    [c('p', 'a', 'parent'), c('p', 'b', 'parent')]);
  assert.equal(kinshipTerm(g2, 'a', 'b')!.code, 'sinli');
});

test('Қайын жұрт: қайын ата, ене, қайынаға, балдыз, қайын бике, бажа', () => {
  assert.equal(term('wife_father').code, 'kayin_ata');
  assert.equal(term('wife_mother').code, 'kayin_ene');
  assert.equal(term('wife_older_bro').code, 'kayinaga');
  assert.equal(term('wife_young_sis').code, 'baldyz');
  assert.equal(term('wife_older_sis').code, 'kayin_bike');
  assert.equal(term('wife_older_sis_husband').code, 'bazha');
});

test('абысын: жёны братьев друг другу', () => {
  assert.equal(kinshipTerm(graph, 'ini_wife', 'aga_wife')!.code, 'abysyn');
});

test('неизвестный возраст → уточняющий вопрос, а не догадка', () => {
  const r = term('unknown_age_bro');
  assert.equal(r.ambiguous, true);
  assert.deepEqual(r.alternatives, ['aga', 'ini']);
  assert.match(r.question!, /старше/);
});

test('неподтверждённая или оспоренная связь не используется', () => {
  const g3 = new KinshipGraph(
    [{ id: 'me', name: 'Я', gender: 'm' }, { id: 'x', name: 'X', gender: 'm' }],
    [{ from: 'x', to: 'me', kind: 'parent', status: 'disputed' }]);
  assert.equal(kinshipTerm(g3, 'me', 'x'), null);
});

test('несколько путей к одному человеку — один результат', () => {
  // жена — одновременно двоюродная по другому пути не должна ломать ответ
  const r = term('wife');
  assert.equal(r.code, 'aiel');
  assert.equal(r.explanation, 'Мен → Дина');
});

test('объяснение пути', () => {
  assert.equal(term('m_brother').explanation, 'Мен → Айгүл → Серік');
});

test('подсказка круга', () => {
  assert.equal(suggestedCircle('nagashy_aga'), 'nagashy');
  assert.equal(suggestedCircle('kayin_ata'), 'kayin');
  assert.equal(suggestedCircle('kuda'), 'toy');
  assert.equal(suggestedCircle('ana'), 'shanyrak');
  assert.equal(suggestedCircle('aga'), 'oz');
});
