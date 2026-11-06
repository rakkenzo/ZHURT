'use client';

import { useEffect, useMemo, useState } from 'react';
import { dict, type Lang } from '../lib/i18n';
import { ornamentUrl, PLACES, type Place } from '../lib/ornament';
import { TERMS } from '../../packages/kinship/terms.ts';
import { KinshipGraph, kinshipTerm, suggestedCircle } from '../../packages/kinship/engine.ts';
import { DEMO_PEOPLE, DEMO_RELATIONS, ME } from '../lib/demoFamily';
import { joinWaitlist, hasBackend } from '../lib/supabase';

const KIN_COLUMNS: { key: 'oz' | 'nagashy' | 'kayin'; codes: string[] }[] = [
  { key: 'oz', codes: ['ata', 'azhe', 'ake', 'aga_ake', 'apa', 'zhenge_ake', 'aga', 'ini', 'apke', 'karyndas', 'sinli', 'zhenge', 'kelin', 'zhezde', 'kuieu_bala', 'abysyn', 'zhien', 'nemere', 'zhienshar'] },
  { key: 'nagashy', codes: ['ana', 'nagashy_ata', 'nagashy_azhe', 'nagashy_aga', 'nagashy_apa', 'nagashy_zhenge', 'nagashy_zhezde', 'bole'] },
  { key: 'kayin', codes: ['aiel', 'kuieu', 'kayin_ata', 'kayin_ene', 'kayinaga', 'kayin_ini', 'kayin_sinli', 'kayin_bike', 'baldyz', 'bazha', 'kuda', 'kudagi'] },
];

const CIRCLE_TITLE: Record<string, string> = { shanyrak: 'Шаңырақ', oz: 'Өз жұрт', nagashy: 'Нағашы', kayin: 'Қайын', toy: 'Той' };

export default function Landing({ lang }: { lang: Lang }) {
  const t = dict[lang];
  const other: Lang = lang === 'kk' ? 'ru' : 'kk';
  const [place, setPlace] = useState<Place>('KZ');
  const [term, setTerm] = useState<string>('nagashy_aga');
  const [picked, setPicked] = useState<string>('m_brother');
  const [contact, setContact] = useState('');
  const [state, setState] = useState<'idle' | 'busy' | 'ok' | 'fail'>('idle');

  useEffect(() => {
    try { const p = localStorage.getItem('zhurt.place') as Place | null; if (p) setPlace(p); } catch {}
  }, []);
  useEffect(() => {
    document.body.style.setProperty('--ornament', ornamentUrl(place));
    try { localStorage.setItem('zhurt.place', place); } catch {}
  }, [place]);

  const graph = useMemo(() => new KinshipGraph([ME, ...DEMO_PEOPLE], DEMO_RELATIONS), []);
  const result = useMemo(() => kinshipTerm(graph, 'me', picked), [graph, picked]);
  const groups = (['shanyrak', 'oz', 'nagashy', 'kayin'] as const).map(g => ({ g, people: DEMO_PEOPLE.filter(p => p.group === g) }));
  const termName = (code: string) => (lang === 'kk' ? TERMS[code].kk : `${TERMS[code].kk} · ${TERMS[code].ru}`);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (contact.trim().length < 5) return;
    setState('busy');
    const ok = await joinWaitlist(contact.trim(), lang, place);
    setState(ok ? 'ok' : 'fail');
  }

  return (
    <>
      <header className="top">
        <div className="wrap row">
          <a href={`/${lang}/`} className="logo">Жұрт</a>
          <nav className="nav">
            <a href="#circles">{t.nav.circles}</a>
            <a href="#kin">{t.nav.kin}</a>
            <a href="#calc">{t.nav.calc}</a>
            <a href="#safe">{t.nav.safe}</a>
          </nav>
          <a className="lang" href={`/${other}/`}>{other === 'kk' ? 'ҚАЗ' : 'РУС'}</a>
        </div>
      </header>

      <main>
        <section className="hero wrap">
          <div className="heroText">
            <p className="kicker">{t.hero.kicker}</p>
            <h1>{t.hero.title}</h1>
            <p className="lead">{t.hero.lead}</p>
            <div className="ctaRow">
              <a className="btn" href="#join">{t.hero.cta}</a>
              <a className="btn ghost" href="#circles">{t.hero.cta2}</a>
            </div>
            <p className="muted small">{t.hero.note}</p>
          </div>
          <Phone lang={lang} />
        </section>

        <section id="circles" className="wrap section">
          <h2>{t.circles.title}</h2>
          <p className="lead narrow">{t.circles.lead}</p>
          <div className="circles">
            {t.circles.items.map(c => (
              <article key={c.k} className={`card circle c-${c.k}`}>
                <span className="dot" />
                <h3>{c.t}</h3>
                <p>{c.d}</p>
              </article>
            ))}
          </div>
        </section>

        <section id="kin" className="wrap section">
          <h2>{t.kin.title}</h2>
          <p className="lead narrow">{t.kin.lead}</p>
          <div className="kinGrid">
            {KIN_COLUMNS.map(col => (
              <div key={col.key} className={`card kinCol k-${col.key}`}>
                <h3>{t.kin[col.key]}</h3>
                <p className="muted small">{t.kin[`${col.key}Sub` as 'ozSub']}</p>
                <div className="chips">
                  {col.codes.map(code => (
                    <button key={code} className={`chip ${term === code ? 'on' : ''}`} onClick={() => setTerm(code)} aria-pressed={term === code}>
                      {TERMS[code].kk}
                    </button>
                  ))}
                </div>
                {KIN_COLUMNS.find(k => k.codes.includes(term))?.key === col.key && (
                  <div className="explain">
                    <b>{TERMS[term].kk}</b>
                    <span>{TERMS[term].ru} — {TERMS[term].hint.toLowerCase()}</span>
                  </div>
                )}
              </div>
            ))}
          </div>
          <p className="muted small">{t.kin.source}</p>
        </section>

        <section id="calc" className="wrap section">
          <h2>{t.calc.title}</h2>
          <p className="lead narrow">{t.calc.lead}</p>
          <div className="calc">
            <div className="card people">
              {groups.map(({ g, people }) => (
                <div key={g} className="pgroup">
                  <p className="glabel">{CIRCLE_TITLE[g]}</p>
                  <div className="chips">
                    {people.map(p => (
                      <button key={p.id} className={`chip person ${picked === p.id ? 'on' : ''}`} onClick={() => setPicked(p.id)} aria-pressed={picked === p.id}>
                        {p.name}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
            <div className="card answer" aria-live="polite">
              {result && (
                <>
                  <p className="muted small">{DEMO_PEOPLE.find(p => p.id === picked)?.name} — {lang === 'kk' ? 'сізге' : 'вам'}</p>
                  <p className="big">{result.ambiguous ? result.alternatives!.map(c => TERMS[c].kk).join(' / ') : TERMS[result.code].kk}</p>
                  {!result.ambiguous && <p className="muted">{lang === 'ru' ? TERMS[result.code].ru : TERMS[result.code].hint}</p>}
                  {result.ambiguous && <p className="warn">{t.calc.ask}: {lang === 'ru' ? result.question : t.calc.askAge}</p>}
                  <p className="glabel">{t.calc.path}</p>
                  <p className="path">{result.explanation.replace(/^Мен/, t.calc.me)}</p>
                  <p className="glabel">{t.calc.circle}</p>
                  <p className="pill">{CIRCLE_TITLE[suggestedCircle(result.code)]}</p>
                </>
              )}
              <p className="muted small demoNote">{t.calc.demo}</p>
            </div>
          </div>
        </section>

        <section id="safe" className="wrap section">
          <h2>{t.safe.title}</h2>
          <p className="lead narrow">{t.safe.lead}</p>
          <div className="safe">
            {t.safe.items.map(([h, d]) => (
              <article key={h} className="card">
                <h3>{h}</h3>
                <p>{d}</p>
              </article>
            ))}
          </div>
          <p className="muted small">{t.safe.honest}</p>
        </section>

        <section className="wrap section">
          <h2>{t.pattern}</h2>
          <div className="chips">
            {PLACES.map(p => (
              <button key={p.id} className={`chip ${place === p.id ? 'on' : ''}`} onClick={() => setPlace(p.id)} aria-pressed={place === p.id}>{p.label}</button>
            ))}
          </div>
        </section>

        <section className="wrap section">
          <h2>{t.roadmap.title}</h2>
          <ol className="road">
            {t.roadmap.items.map(([h, d]) => (
              <li key={h}><b>{h}</b><span>{d}</span></li>
            ))}
          </ol>
        </section>

        <section id="join" className="wrap section">
          <div className="card joinCard">
            <h2>{t.join.title}</h2>
            <p className="lead">{t.join.lead}</p>
            {state === 'ok' ? (
              <p className="okMsg">{t.join.ok}</p>
            ) : (
              <form onSubmit={submit} className="join">
                <input value={contact} onChange={e => setContact(e.target.value)} placeholder={t.join.placeholder} aria-label={t.join.placeholder} maxLength={120} />
                <button className="btn" disabled={state === 'busy'}>{t.join.button}</button>
              </form>
            )}
            {state === 'fail' && <p className="warn">{hasBackend ? t.join.fail : t.join.noBackend}</p>}
          </div>
        </section>
      </main>

      <footer className="wrap foot muted small">{t.footer} · {new Date().getFullYear()}</footer>
    </>
  );
}

function Phone({ lang }: { lang: Lang }) {
  const chats = [
    { l: 'Ү', t: 'Үй', w: lang === 'kk' ? 'ана, әке, әйел' : 'ана, әке, әйел', p: 'Нан ыстық 🍞', c: 'shanyrak' },
    { l: 'А', t: 'Ағайын', w: 'аға, жеңге, іні, келін', p: 'Жеңге шай қойды', c: 'oz' },
    { l: 'С', t: 'Ата · хабарландыру', w: lang === 'kk' ? 'тек ата жазады' : 'пишет только ата', p: 'Жексенбіде ауыл', c: 'oz' },
    { l: 'С', t: 'Серік', w: 'нағашы аға', p: 'Жиен, сенбіде кел', c: 'nagashy' },
    { l: 'Р', t: 'Сәуле', w: 'ене · 30 күн', p: 'Тойға смета', c: 'kayin' },
    { l: 'Қ', t: 'Құдалар', w: 'той', p: 'Келін түсер 18:00', c: 'toy' },
  ];
  return (
    <div className="phone" aria-hidden="true">
      <div className="pscreen">
        <p className="ptitle">Жұрт</p>
        <div className="pfolds"><span className="on">{lang === 'kk' ? 'бәрі' : 'все'}</span><span>шаңырақ</span><span>өз жұрт</span><span>нағашы</span></div>
        {chats.map(c => (
          <div key={c.t} className="prow">
            <span className={`pav c-${c.c}`}>{c.l}</span>
            <span className="ptext"><b>{c.t}</b><small>{c.w} · {c.p}</small></span>
          </div>
        ))}
      </div>
    </div>
  );
}
