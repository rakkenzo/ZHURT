import Landing from '../../components/Landing';
import { dict, LANGS, type Lang } from '../../lib/i18n';

export function generateStaticParams() {
  return LANGS.map(lang => ({ lang }));
}

export function generateMetadata({ params }: { params: { lang: Lang } }) {
  const t = dict[params.lang];
  return {
    title: t.meta.title,
    description: t.meta.desc,
    alternates: { languages: { kk: '/kk/', ru: '/ru/' } },
    openGraph: { title: t.meta.title, description: t.meta.desc, locale: params.lang === 'kk' ? 'kk_KZ' : 'ru_RU' },
  };
}

export default function Page({ params }: { params: { lang: Lang } }) {
  return (
    <>
      <script dangerouslySetInnerHTML={{ __html: `document.documentElement.lang='${params.lang}'` }} />
      <Landing lang={params.lang} />
    </>
  );
}
