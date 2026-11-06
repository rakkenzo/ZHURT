import type { Person, Relation } from '../../packages/kinship/engine.ts';

// Вымышленная семья для демо на сайте. «Я» — Нұрлан, 1990 г.р.
export const DEMO_PEOPLE: (Person & { group: 'shanyrak' | 'oz' | 'nagashy' | 'kayin' })[] = [
  { id: 'father', name: 'Сейіт', gender: 'm', birthYear: 1960, group: 'shanyrak' },
  { id: 'mother', name: 'Айгүл', gender: 'f', birthYear: 1963, group: 'shanyrak' },
  { id: 'wife', name: 'Дина', gender: 'f', birthYear: 1992, group: 'shanyrak' },
  { id: 'son', name: 'Қуаныш', gender: 'm', birthYear: 2015, group: 'shanyrak' },
  { id: 'daughter', name: 'Әлия', gender: 'f', birthYear: 2016, group: 'shanyrak' },

  { id: 'f_father', name: 'Бекен', gender: 'm', birthYear: 1930, group: 'oz' },
  { id: 'f_brother', name: 'Ерлан', gender: 'm', birthYear: 1958, group: 'oz' },
  { id: 'f_sister', name: 'Күләш', gender: 'f', birthYear: 1962, group: 'oz' },
  { id: 'aga', name: 'Талғат', gender: 'm', birthYear: 1987, group: 'oz' },
  { id: 'aga_wife', name: 'Жанар', gender: 'f', birthYear: 1988, group: 'oz' },
  { id: 'ini', name: 'Бек', gender: 'm', birthYear: 1993, group: 'oz' },
  { id: 'ini_wife', name: 'Мөлдір', gender: 'f', birthYear: 1996, group: 'oz' },
  { id: 'older_sis', name: 'Самал', gender: 'f', birthYear: 1986, group: 'oz' },
  { id: 'older_sis_husband', name: 'Бауыржан', gender: 'm', birthYear: 1984, group: 'oz' },
  { id: 'older_sis_kid', name: 'Әли', gender: 'm', birthYear: 2012, group: 'oz' },
  { id: 'young_sis', name: 'Дана', gender: 'f', birthYear: 1995, group: 'oz' },
  { id: 'young_sis_husband', name: 'Ержан', gender: 'm', birthYear: 1993, group: 'oz' },
  { id: 'unknown_bro', name: 'Марат', gender: 'm', group: 'oz' },

  { id: 'm_father', name: 'Қасым', gender: 'm', birthYear: 1935, group: 'nagashy' },
  { id: 'm_mother', name: 'Зейнеп', gender: 'f', birthYear: 1938, group: 'nagashy' },
  { id: 'm_brother', name: 'Серік', gender: 'm', birthYear: 1966, group: 'nagashy' },
  { id: 'm_brother_wife', name: 'Гүлнар', gender: 'f', birthYear: 1968, group: 'nagashy' },
  { id: 'm_sister', name: 'Роза', gender: 'f', birthYear: 1968, group: 'nagashy' },
  { id: 'm_sister_kid', name: 'Арман', gender: 'm', birthYear: 1995, group: 'nagashy' },

  { id: 'wife_father', name: 'Мұрат', gender: 'm', birthYear: 1962, group: 'kayin' },
  { id: 'wife_mother', name: 'Сәуле', gender: 'f', birthYear: 1965, group: 'kayin' },
  { id: 'wife_older_bro', name: 'Асқар', gender: 'm', birthYear: 1988, group: 'kayin' },
  { id: 'wife_older_sis', name: 'Әсем', gender: 'f', birthYear: 1989, group: 'kayin' },
  { id: 'wife_older_sis_husband', name: 'Қайрат', gender: 'm', birthYear: 1987, group: 'kayin' },
  { id: 'wife_young_sis', name: 'Аружан', gender: 'f', birthYear: 1998, group: 'kayin' },
];

const c = (from: string, to: string, kind: Relation['kind']): Relation => ({ from, to, kind, status: 'confirmed' });

export const DEMO_RELATIONS: Relation[] = [
  c('father', 'me', 'parent'), c('mother', 'me', 'parent'), c('father', 'mother', 'spouse'),
  c('me', 'wife', 'spouse'), c('me', 'son', 'parent'), c('me', 'daughter', 'parent'),
  c('f_father', 'father', 'parent'), c('f_father', 'f_brother', 'parent'), c('f_father', 'f_sister', 'parent'),
  c('father', 'aga', 'parent'), c('aga', 'aga_wife', 'spouse'),
  c('father', 'ini', 'parent'), c('ini', 'ini_wife', 'spouse'),
  c('father', 'older_sis', 'parent'), c('older_sis', 'older_sis_husband', 'spouse'), c('older_sis', 'older_sis_kid', 'parent'),
  c('father', 'young_sis', 'parent'), c('young_sis', 'young_sis_husband', 'spouse'),
  c('father', 'unknown_bro', 'parent'),
  c('m_father', 'mother', 'parent'), c('m_mother', 'mother', 'parent'),
  c('m_father', 'm_brother', 'parent'), c('m_father', 'm_sister', 'parent'),
  c('m_brother', 'm_brother_wife', 'spouse'), c('m_sister', 'm_sister_kid', 'parent'),
  c('wife_father', 'wife', 'parent'), c('wife_mother', 'wife', 'parent'),
  c('wife_father', 'wife_older_bro', 'parent'), c('wife_father', 'wife_older_sis', 'parent'),
  c('wife_father', 'wife_young_sis', 'parent'), c('wife_older_sis', 'wife_older_sis_husband', 'spouse'),
];

export const ME: Person = { id: 'me', name: 'Нұрлан', gender: 'm', birthYear: 1990 };
