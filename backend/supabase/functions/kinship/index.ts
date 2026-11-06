// Edge Function «kinship»: термин родства и подсказки для текущего пользователя.
// POST { space_id, target_person_id }  →  { code, term_kk, term_ru, explanation, ambiguous, question, alternatives, suggestions }
// Читает граф от имени пользователя (его JWT), поэтому RLS гарантирует, что он видит только своё пространство.
import { createClient } from 'npm:@supabase/supabase-js@2.45.4';
import { KinshipGraph, kinshipTerm, suggestNext, suggestedCircle } from '../_shared/kinship/engine.ts';

const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors });
  try {
    const { space_id, target_person_id } = await req.json();
    const supabase = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_ANON_KEY')!, {
      global: { headers: { Authorization: req.headers.get('Authorization') ?? '' } },
    });
    const { data: auth } = await supabase.auth.getUser();
    if (!auth.user) return json({ error: 'unauthorized' }, 401);

    const [{ data: persons, error: e1 }, { data: rels, error: e2 }] = await Promise.all([
      supabase.from('persons').select('id, user_id, display_name, gender, birth_year, birth_order').eq('family_space_id', space_id),
      supabase.from('relationship_assertions').select('from_person, to_person, kind, status').eq('family_space_id', space_id),
    ]);
    if (e1 || e2) return json({ error: (e1 ?? e2)!.message }, 400);

    const me = persons!.find((p) => p.user_id === auth.user!.id);
    if (!me) return json({ error: 'you are not in this family graph' }, 403);

    const graph = new KinshipGraph(
      persons!.map((p) => ({ id: p.id, name: p.display_name, gender: p.gender, birthYear: p.birth_year, birthOrder: p.birth_order })),
      rels!.map((r) => ({ from: r.from_person, to: r.to_person, kind: r.kind, status: r.status })),
    );
    const r = kinshipTerm(graph, me.id, target_person_id);
    if (!r) return json({ code: null, explanation: 'Связь не найдена среди подтверждённых' });
    return json({
      code: r.code,
      term_kk: r.term.kk,
      term_ru: r.term.ru,
      hint: r.term.hint,
      explanation: r.explanation,
      ambiguous: r.ambiguous,
      question: r.question ?? null,
      alternatives: r.alternatives ?? null,
      suggested_circle: suggestedCircle(r.code),
      suggestions: suggestNext(graph, me.id, target_person_id),
    });
  } catch (e) {
    return json({ error: String(e) }, 500);
  }
});

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, 'Content-Type': 'application/json' } });
}
