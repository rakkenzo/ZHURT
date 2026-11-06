// Национальные узоры фоном (ТЗ v2.0, раздел 10): линия ~7% золота, не попадает на карточки.
export type Place = 'KZ' | 'UZ' | 'TM' | 'KG';
export const PLACES: { id: Place; label: string }[] = [
  { id: 'KZ', label: 'Қазақстан' },
  { id: 'UZ', label: 'Oʻzbekiston' },
  { id: 'TM', label: 'Türkmenistan' },
  { id: 'KG', label: 'Кыргызстан' },
];

const S = 148;

function horn(cx: number, cy: number) {
  // қошқар мүйіз — две закрученные спирали
  const spiral = (dir: 1 | -1) => {
    let d = '';
    let a = 0.4;
    for (let i = 0; i <= 28; i++) {
      const t = i / 4.2;
      const x = cx + dir * (10 + Math.cos(t) * a);
      const y = cy + Math.sin(t) * a;
      d += (i === 0 ? 'M' : 'L') + x.toFixed(1) + ' ' + y.toFixed(1) + ' ';
      a += 0.7;
    }
    return `<path d="${d}"/>`;
  };
  return spiral(1) + spiral(-1) + `<path d="M${cx - 10} ${cy + 22} Q${cx} ${cy + 8} ${cx + 10} ${cy + 22}"/>`;
}

function star(cx: number, cy: number) {
  const pts: string[] = [];
  for (let i = 0; i < 16; i++) {
    const t = (i * Math.PI) / 8;
    const r = i % 2 === 0 ? 22 : 10;
    pts.push(`${(cx + Math.cos(t) * r).toFixed(1)},${(cy + Math.sin(t) * r).toFixed(1)}`);
  }
  return `<polygon points="${pts.join(' ')}"/>`;
}

function gul(cx: number, cy: number) {
  let s = `<ellipse cx="${cx}" cy="${cy}" rx="24" ry="16"/><ellipse cx="${cx}" cy="${cy}" rx="10" ry="7"/>`;
  for (let i = 0; i < 8; i++) {
    const t = (i * Math.PI) / 4;
    s += `<circle cx="${(cx + Math.cos(t) * 24).toFixed(1)}" cy="${(cy + Math.sin(t) * 16).toFixed(1)}" r="3"/>`;
  }
  return s;
}

function knot(cx: number, cy: number) {
  return `<path d="M${cx - 16} ${cy}H${cx + 16}M${cx} ${cy - 16}V${cy + 16}"/><rect x="${cx - 8}" y="${cy - 8}" width="16" height="16" transform="rotate(45 ${cx} ${cy})"/>`;
}

export function ornamentUrl(place: Place, color = '#E8C468', opacity = 0.07): string {
  const draw = { KZ: horn, UZ: star, TM: gul, KG: knot }[place];
  const body = draw(S / 4, S / 4) + draw((S * 3) / 4, (S * 3) / 4);
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${S}" height="${S}" viewBox="0 0 ${S} ${S}"><g fill="none" stroke="${color}" stroke-opacity="${opacity}" stroke-width="1.4">${body}</g></svg>`;
  return `url("data:image/svg+xml;utf8,${encodeURIComponent(svg)}")`;
}
