// Корень: переадресация на язык браузера (статический экспорт, без сервера)
export default function Root() {
  const js = `var l=(navigator.language||'kk').slice(0,2);location.replace(l==='ru'?'/ru/':'/kk/');`;
  return (
    <>
      <script dangerouslySetInnerHTML={{ __html: js }} />
      <noscript><meta httpEquiv="refresh" content="0; url=/kk/" /></noscript>
      <p style={{ padding: 24 }}><a href="/kk/">Қазақша</a> · <a href="/ru/">Русский</a></p>
    </>
  );
}
