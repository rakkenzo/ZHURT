// Kinship Engine (ТЗ v2.0, раздел 3).
// Работает только по явно внесённым и ПОДТВЕРЖДЁННЫМ связям. Ничего не угадывает по фамилиям/контактам.
// Ищет кратчайший путь от «меня» до человека и сопоставляет его с шаблонами казахских терминов.
// При неизвестном старшинстве возвращает ambiguous + уточняющий вопрос, а не выдумывает ответ.

import { TERMS, type TermDef } from './terms.ts';

export type Gender = 'm' | 'f';
export type RelKind = 'parent' | 'spouse' | 'sibling';
export type RelStatus = 'proposed' | 'pending' | 'confirmed' | 'rejected' | 'disputed' | 'needs_clarification';

export interface Person {
  id: string;
  name: string;
  gender?: Gender | null;
  birthYear?: number | null;
  birthOrder?: number | null; // меньше = старше; используется, если год неизвестен
}

export interface Relation {
  from: string; // parent: from = родитель, to = ребёнок
  to: string;
  kind: RelKind;
  status: RelStatus;
}

// Шаг пути: P — к родителю, C — к ребёнку, S — к брату/сестре, W — к супругу
type Step = 'P' | 'C' | 'S' | 'W';

export interface KinshipResult {
  code: string;
  term: TermDef;
  path: string[];          // id людей по пути, включая меня и цель
  steps: Step[];
  explanation: string;     // «Мен → Ана → Нағашы аға»
  ambiguous: boolean;
  question?: string;       // что уточнить у пользователя
  alternatives?: string[]; // возможные коды при неоднозначности
}

interface Edge { to: string; step: Step }

export class KinshipGraph {
  private people = new Map<string, Person>();
  private adj = new Map<string, Edge[]>();

  constructor(people: Person[], relations: Relation[]) {
    for (const p of people) { this.people.set(p.id, p); this.adj.set(p.id, []); }
    const confirmed = relations.filter(r => r.status === 'confirmed' && this.people.has(r.from) && this.people.has(r.to));
    const parentsOf = new Map<string, Set<string>>();
    for (const r of confirmed) {
      if (r.kind === 'parent') {
        this.add(r.to, r.from, 'P');
        this.add(r.from, r.to, 'C');
        if (!parentsOf.has(r.to)) parentsOf.set(r.to, new Set());
        parentsOf.get(r.to)!.add(r.from);
      } else if (r.kind === 'spouse') {
        this.add(r.from, r.to, 'W'); this.add(r.to, r.from, 'W');
      } else {
        this.add(r.from, r.to, 'S'); this.add(r.to, r.from, 'S');
      }
    }
    // Братья/сёстры через общего родителя — выводятся, а не требуют отдельного ввода
    const kids = [...parentsOf.entries()];
    for (let i = 0; i < kids.length; i++) for (let j = i + 1; j < kids.length; j++) {
      const [a, pa] = kids[i]; const [b, pb] = kids[j];
      if ([...pa].some(x => pb.has(x))) { this.add(a, b, 'S'); this.add(b, a, 'S'); }
    }
  }

  private add(a: string, b: string, step: Step) {
    const list = this.adj.get(a)!;
    if (!list.some(e => e.to === b && e.step === step)) list.push({ to: b, step });
  }

  person(id: string) { return this.people.get(id); }

  /** Все кратчайшие пути (BFS). Один человек — один Person ID, даже при нескольких путях. */
  paths(from: string, to: string, maxLen = 4): { nodes: string[]; steps: Step[] }[] {
    const out: { nodes: string[]; steps: Step[] }[] = [];
    let frontier: { nodes: string[]; steps: Step[] }[] = [{ nodes: [from], steps: [] }];
    for (let depth = 0; depth < maxLen && out.length === 0; depth++) {
      const next: typeof frontier = [];
      for (const p of frontier) {
        const last = p.nodes[p.nodes.length - 1];
        for (const e of this.adj.get(last) ?? []) {
          if (p.nodes.includes(e.to)) continue;
          const np = { nodes: [...p.nodes, e.to], steps: [...p.steps, e.step] };
          if (e.to === to) out.push(np); else next.push(np);
        }
      }
      frontier = next;
    }
    return out;
  }

  /** Сравнение старшинства: 1 — a старше b, -1 — младше, 0 — неизвестно */
  older(a: string, b: string): 1 | -1 | 0 {
    const pa = this.people.get(a), pb = this.people.get(b);
    if (!pa || !pb) return 0;
    if (pa.birthYear && pb.birthYear && pa.birthYear !== pb.birthYear) return pa.birthYear < pb.birthYear ? 1 : -1;
    if (pa.birthOrder != null && pb.birthOrder != null && pa.birthOrder !== pb.birthOrder) return pa.birthOrder < pb.birthOrder ? 1 : -1;
    return 0;
  }
}

function g(graph: KinshipGraph, id: string): Gender | undefined {
  return graph.person(id)?.gender ?? undefined;
}

/** Определить, как «я» называю человека target. */
export function kinshipTerm(graph: KinshipGraph, me: string, target: string): KinshipResult | null {
  if (me === target) return build(graph, 'men', [me], [], false);
  const paths = graph.paths(me, target);
  if (paths.length === 0) return null;
  // Если путей несколько — берём первый, давший однозначный термин
  let fallback: KinshipResult | null = null;
  for (const p of paths) {
    const r = match(graph, p.nodes, p.steps);
    if (r && !r.ambiguous && r.code !== 'tuys') return r;
    if (r && (!fallback || (fallback.code === 'tuys' && r.code !== 'tuys'))) fallback = r;
  }
  return fallback;
}

function build(graph: KinshipGraph, code: string, nodes: string[], steps: Step[], ambiguous: boolean, question?: string, alternatives?: string[]): KinshipResult {
  const term = TERMS[code] ?? TERMS.tuys;
  const names = nodes.map((id, i) => i === 0 ? 'Мен' : graph.person(id)?.name ?? '?');
  return { code, term, path: nodes, steps, explanation: names.join(' → '), ambiguous, question, alternatives };
}

function match(graph: KinshipGraph, n: string[], s: Step[]): KinshipResult {
  const key = s.join('');
  const me = n[0];
  const t = n[n.length - 1];
  const tg = g(graph, t);
  const myG = g(graph, me);
  const B = (code: string) => build(graph, code, n, s, false);
  const A = (codes: [string, string], q: string) => build(graph, codes[0], n, s, true, q, codes);
  const byGender = (m: string, f: string) => tg === 'm' ? B(m) : tg === 'f' ? B(f) : A([m, f], 'Укажите пол человека');
  // старше ли X меня (или другой опорной точки)
  const age = (x: string, ref: string, olderCode: string, youngerCode: string, q: string) => {
    const o = graph.older(x, ref);
    return o === 1 ? B(olderCode) : o === -1 ? B(youngerCode) : A([olderCode, youngerCode], q);
  };
  const viaMother = (idx: number) => g(graph, n[idx]) === 'f';

  switch (key) {
    case 'P': return byGender('ake', 'ana');
    case 'C': return byGender('ul', 'kyz');
    case 'W': return byGender('kuieu', 'aiel');

    case 'S':
      if (tg === 'm') return age(t, me, 'aga', 'ini', `${graph.person(t)?.name} старше вас?`);
      if (tg === 'f') {
        const younger = myG === 'm' ? 'karyndas' : myG === 'f' ? 'sinli' : 'sinli';
        if (!myG && graph.older(t, me) !== 1) return A(['karyndas', 'sinli'], 'Укажите ваш пол в профиле');
        return age(t, me, 'apke', younger, `${graph.person(t)?.name} старше вас?`);
      }
      return A(['aga', 'apke'], 'Укажите пол человека');

    case 'PP':
      return viaMother(1) ? byGender('nagashy_ata', 'nagashy_azhe') : byGender('ata', 'azhe');
    case 'PPP':
      return viaMother(1) ? byGender('nagashy_ata', 'nagashy_azhe') : byGender('ata', 'azhe');

    case 'PS':
      return viaMother(1) ? byGender('nagashy_aga', 'nagashy_apa') : byGender('aga_ake', 'apa');
    case 'PSW':
      return viaMother(1) ? byGender('nagashy_zhezde', 'nagashy_zhenge') : byGender('zhezde_ake', 'zhenge_ake');
    case 'PSC': {
      const parentIsMother = viaMother(1);
      const auntUncleF = g(graph, n[2]) === 'f';
      if (parentIsMother && auntUncleF) return B('bole');           // дети двух сестёр
      if (parentIsMother) return B('nagashy_bauyr');                // дети брата матери
      if (auntUncleF) return B('zhien');                             // дети сестры отца
      // дети брата отца — как родные братья/сёстры (ағайын)
      return match(graph, [me, t], ['S']);
    }

    case 'SW': {
      const sib = n[1];
      const o = graph.older(sib, me);
      if (g(graph, sib) === 'm') return o === 1 ? B('zhenge') : o === -1 ? B('kelin') : A(['zhenge', 'kelin'], `${graph.person(sib)?.name} старше вас?`);
      return o === 1 ? B('zhezde') : o === -1 ? B('kuieu_bala') : A(['zhezde', 'kuieu_bala'], `${graph.person(sib)?.name} старше вас?`);
    }
    case 'SC':
      return g(graph, n[1]) === 'f' ? B('zhien') : B('bauyr_bala');

    case 'CC':
      return g(graph, n[1]) === 'f' ? B('zhien') : B('nemere');
    case 'CCC':
      return g(graph, n[1]) === 'f' ? B('zhienshar') : B('shobere');
    case 'CW':
      return byGender('kuieu_bala', 'kelin');
    case 'CWP':
      return byGender('kuda', 'kudagi');

    case 'WP':
      return byGender('kayin_ata', 'kayin_ene');
    case 'WS': {
      const spouse = n[1];
      const o = graph.older(t, spouse);
      const q = `${graph.person(t)?.name} старше вашего супруга(и)?`;
      if (g(graph, spouse) === 'f') { // я — муж, родня жены
        if (o === 1) return tg === 'f' ? B('kayin_bike') : B('kayinaga');
        if (o === -1) return B('baldyz');
        return tg === 'f' ? A(['kayin_bike', 'baldyz'], q) : A(['kayinaga', 'baldyz'], q);
      }
      // я — жена, родня мужа
      if (tg === 'm') return o === 1 ? B('kayinaga') : o === -1 ? B('kayin_ini') : A(['kayinaga', 'kayin_ini'], q);
      return o === 1 ? B('kayin_apke') : o === -1 ? B('kayin_sinli') : A(['kayin_apke', 'kayin_sinli'], q);
    }
    case 'WSW': {
      // мужья двух сестёр — бажа; жёны двух братьев — абысын
      if (g(graph, n[1]) === 'f' && tg === 'm') return B('bazha');
      if (g(graph, n[1]) === 'm' && tg === 'f') return B('abysyn');
      return B('tuys');
    }
  }
  return B('tuys');
}

// --------------------------------------------------------------------
// Подсказки «кого добавить дальше» (ТЗ 3: добавили мать → предложить её родителей и братьев/сестёр)
// --------------------------------------------------------------------
export interface Suggestion {
  anchor: string;           // к кому привязать нового человека
  relation: RelKind;        // тип связи с anchor
  direction: 'parent_of_anchor' | 'child_of_anchor' | 'spouse' | 'sibling';
  expectedCode: string;     // каким термином этот человек будет для меня
  label: string;            // текст кнопки
}

export function suggestNext(graph: KinshipGraph, me: string, added: string): Suggestion[] {
  const r = kinshipTerm(graph, me, added);
  if (!r) return [];
  const T = (c: string) => TERMS[c].kk;
  const out: Suggestion[] = [];
  const push = (relation: RelKind, direction: Suggestion['direction'], code: string, ru: string) =>
    out.push({ anchor: added, relation, direction, expectedCode: code, label: `${ru} — ${T(code)}` });

  switch (r.code) {
    case 'ana':
      push('parent', 'parent_of_anchor', 'nagashy_ata', 'Добавить отца матери');
      push('parent', 'parent_of_anchor', 'nagashy_azhe', 'Добавить мать матери');
      push('sibling', 'sibling', 'nagashy_aga', 'Добавить брата матери');
      push('sibling', 'sibling', 'nagashy_apa', 'Добавить сестру матери');
      break;
    case 'ake':
      push('parent', 'parent_of_anchor', 'ata', 'Добавить отца отца');
      push('parent', 'parent_of_anchor', 'azhe', 'Добавить мать отца');
      push('sibling', 'sibling', 'aga_ake', 'Добавить брата отца');
      push('sibling', 'sibling', 'apa', 'Добавить сестру отца');
      break;
    case 'ini':
      push('spouse', 'spouse', 'kelin', 'Добавить его жену');
      break;
    case 'aga':
      push('spouse', 'spouse', 'zhenge', 'Добавить его жену');
      break;
    case 'apke':
      push('spouse', 'spouse', 'zhezde', 'Добавить её мужа');
      push('parent', 'child_of_anchor', 'zhien', 'Добавить её детей');
      break;
    case 'karyndas': case 'sinli':
      push('spouse', 'spouse', 'kuieu_bala', 'Добавить её мужа');
      push('parent', 'child_of_anchor', 'zhien', 'Добавить её детей');
      break;
    case 'aiel': case 'kuieu':
      push('parent', 'parent_of_anchor', 'kayin_ata', 'Добавить отца супруга(и)');
      push('parent', 'parent_of_anchor', 'kayin_ene', 'Добавить мать супруга(и)');
      push('sibling', 'sibling', r.code === 'aiel' ? 'kayinaga' : 'kayinaga', 'Добавить брата/сестру супруга(и)');
      break;
    case 'ul':
      push('spouse', 'spouse', 'kelin', 'Добавить жену сына');
      break;
    case 'kyz':
      push('spouse', 'spouse', 'kuieu_bala', 'Добавить мужа дочери');
      break;
  }
  return out;
}

/** В какой круг логично пригласить человека (только подсказка; доступ не выдаётся автоматически). */
export function suggestedCircle(code: string): 'shanyrak' | 'oz' | 'nagashy' | 'kayin' | 'toy' {
  if (['ake', 'ana', 'aiel', 'kuieu', 'ul', 'kyz'].includes(code)) return 'shanyrak';
  if (['kuda', 'kudagi'].includes(code)) return 'toy';
  const side = TERMS[code]?.side;
  if (side === 'nagashy') return 'nagashy';
  if (side === 'kayin') return 'kayin';
  return 'oz';
}
