import { createClient } from '@supabase/supabase-js';

const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

export const hasBackend = Boolean(url && key);
const client = hasBackend ? createClient(url!, key!, { auth: { persistSession: false } }) : null;

export async function joinWaitlist(contact: string, locale: string, country: string): Promise<boolean> {
  if (!client) return false;
  const { error } = await client.from('waitlist').insert({ contact, locale, country });
  return !error;
}
