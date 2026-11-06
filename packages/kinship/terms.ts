// Справочник терминов родства (по плакату «Вся казахская родня», К. Тасибеков «Ситуативный казахский»,
// и ТЗ v2.0, Приложение A). Все термины помечены needsReview до проверки филологом.

export type Side = 'shanyrak' | 'oz' | 'nagashy' | 'kayin' | 'other';

export interface TermDef {
  code: string;
  kk: string;
  ru: string;
  side: Side;
  hint: string; // пояснение по-русски
}

export const TERMS: Record<string, TermDef> = {
  men:          { code: 'men',          kk: 'Мен',             ru: 'Я',                  side: 'shanyrak', hint: 'Вы сами' },
  ake:          { code: 'ake',          kk: 'Әке',             ru: 'Отец',               side: 'shanyrak', hint: 'Отец' },
  ana:          { code: 'ana',          kk: 'Ана',             ru: 'Мать',               side: 'shanyrak', hint: 'Мать' },
  aiel:         { code: 'aiel',         kk: 'Әйел',            ru: 'Жена',               side: 'shanyrak', hint: 'Жена' },
  kuieu:        { code: 'kuieu',        kk: 'Күйеу',           ru: 'Муж',                side: 'shanyrak', hint: 'Муж' },
  ul:           { code: 'ul',           kk: 'Ұл',              ru: 'Сын',                side: 'shanyrak', hint: 'Сын' },
  kyz:          { code: 'kyz',          kk: 'Қыз',             ru: 'Дочь',               side: 'shanyrak', hint: 'Дочь' },

  // Өз жұрты — сторона отца
  ata:          { code: 'ata',          kk: 'Ата',             ru: 'Дедушка',            side: 'oz', hint: 'Дедушка со стороны отца' },
  azhe:         { code: 'azhe',         kk: 'Әже',             ru: 'Бабушка',            side: 'oz', hint: 'Бабушка со стороны отца' },
  aga_ake:      { code: 'aga_ake',      kk: 'Аға',             ru: 'Дядя',               side: 'oz', hint: 'Брат отца' },
  apa:          { code: 'apa',          kk: 'Апа / Тәте',      ru: 'Тётя',               side: 'oz', hint: 'Сестра отца' },
  zhenge_ake:   { code: 'zhenge_ake',   kk: 'Жеңге',           ru: 'Тётя (жена дяди)',   side: 'oz', hint: 'Жена брата отца' },
  zhezde_ake:   { code: 'zhezde_ake',   kk: 'Жезде',           ru: 'Муж тёти',           side: 'oz', hint: 'Муж сестры отца' },
  aga:          { code: 'aga',          kk: 'Аға',             ru: 'Старший брат',       side: 'oz', hint: 'Старший брат' },
  ini:          { code: 'ini',          kk: 'Іні',             ru: 'Младший брат',       side: 'oz', hint: 'Младший брат' },
  apke:         { code: 'apke',         kk: 'Әпке',            ru: 'Старшая сестра',     side: 'oz', hint: 'Старшая сестра' },
  karyndas:     { code: 'karyndas',     kk: 'Қарындас',        ru: 'Младшая сестра',     side: 'oz', hint: 'Младшая сестра (для брата)' },
  sinli:        { code: 'sinli',        kk: 'Сіңлі',           ru: 'Младшая сестра',     side: 'oz', hint: 'Младшая сестра (для сестры)' },
  zhenge:       { code: 'zhenge',       kk: 'Жеңге',           ru: 'Жена старшего брата', side: 'oz', hint: 'Жена старшего брата' },
  kelin:        { code: 'kelin',        kk: 'Келін',           ru: 'Келин',              side: 'oz', hint: 'Жена младшего брата / жена сына' },
  zhezde:       { code: 'zhezde',       kk: 'Жезде',           ru: 'Муж старшей сестры', side: 'oz', hint: 'Муж старшей сестры' },
  kuieu_bala:   { code: 'kuieu_bala',   kk: 'Күйеу бала',      ru: 'Зять',               side: 'oz', hint: 'Муж младшей сестры / муж дочери' },
  bazha:        { code: 'bazha',        kk: 'Бажа',            ru: 'Свояк',              side: 'kayin', hint: 'Мужья родных сестёр по отношению друг к другу' },
  abysyn:       { code: 'abysyn',       kk: 'Абысын',          ru: 'Невестка (ятровка)', side: 'oz', hint: 'Жёны родных братьев по отношению друг к другу' },
  bauyr_bala:   { code: 'bauyr_bala',   kk: 'Бауырдың баласы', ru: 'Племянник (по брату)', side: 'oz', hint: 'Ребёнок брата' },
  nemere:       { code: 'nemere',       kk: 'Немере',          ru: 'Внук / внучка',      side: 'oz', hint: 'Внуки со стороны сына' },
  shobere:      { code: 'shobere',      kk: 'Шөбере',          ru: 'Правнук',            side: 'oz', hint: 'Правнуки со стороны сына' },
  zhien:        { code: 'zhien',        kk: 'Жиен',            ru: 'Племянник (по сестре)', side: 'oz', hint: 'Дети сестры; внуки со стороны дочери; дети сестёр отца' },
  zhienshar:    { code: 'zhienshar',    kk: 'Жиеншар',         ru: 'Правнук (по дочери)', side: 'oz', hint: 'Правнуки со стороны дочери' },
  bole:         { code: 'bole',         kk: 'Бөле',            ru: 'Двоюродный (по сёстрам)', side: 'nagashy', hint: 'Дети родных сестёр по отношению друг к другу' },

  // Нағашы жұрты — сторона матери
  nagashy_ata:    { code: 'nagashy_ata',    kk: 'Нағашы ата',    ru: 'Дедушка (по матери)',  side: 'nagashy', hint: 'Отец матери' },
  nagashy_azhe:   { code: 'nagashy_azhe',   kk: 'Нағашы әже',    ru: 'Бабушка (по матери)',  side: 'nagashy', hint: 'Мать матери' },
  nagashy_aga:    { code: 'nagashy_aga',    kk: 'Нағашы аға',    ru: 'Дядя (по матери)',     side: 'nagashy', hint: 'Брат матери' },
  nagashy_apa:    { code: 'nagashy_apa',    kk: 'Нағашы апа',    ru: 'Тётя (по матери)',     side: 'nagashy', hint: 'Сестра матери' },
  nagashy_zhenge: { code: 'nagashy_zhenge', kk: 'Нағашы жеңге',  ru: 'Жена дяди (по матери)', side: 'nagashy', hint: 'Жена брата матери' },
  nagashy_zhezde: { code: 'nagashy_zhezde', kk: 'Нағашы жезде',  ru: 'Муж тёти (по матери)', side: 'nagashy', hint: 'Муж сестры матери' },
  nagashy_bauyr:  { code: 'nagashy_bauyr',  kk: 'Нағашы бауыр',  ru: 'Двоюродный (по дяде)', side: 'nagashy', hint: 'Дети брата матери' },

  // Қайын жұрты, құдалар — сторона супруга/супруги
  kayin_ata:    { code: 'kayin_ata',    kk: 'Қайын ата',       ru: 'Свёкор / тесть',     side: 'kayin', hint: 'Отец мужа (жены)' },
  kayin_ene:    { code: 'kayin_ene',    kk: 'Ене, қайын ене',  ru: 'Свекровь / тёща',    side: 'kayin', hint: 'Мать мужа (жены)' },
  kayinaga:     { code: 'kayinaga',     kk: 'Қайынаға',        ru: 'Деверь / шурин',     side: 'kayin', hint: 'Старший брат мужа (жены)' },
  kayin_ini:    { code: 'kayin_ini',    kk: 'Қайыніні',        ru: 'Деверь (младший)',   side: 'kayin', hint: 'Младший брат мужа' },
  kayin_sinli:  { code: 'kayin_sinli',  kk: 'Қайынсіңлі',      ru: 'Золовка (младшая)',  side: 'kayin', hint: 'Младшая сестра мужа' },
  kayin_apke:   { code: 'kayin_apke',   kk: 'Қайын әпке',      ru: 'Золовка (старшая)',  side: 'kayin', hint: 'Старшая сестра мужа' },
  kayin_bike:   { code: 'kayin_bike',   kk: 'Қайын бике',      ru: 'Свояченица (старшая)', side: 'kayin', hint: 'Старшая сестра жены' },
  baldyz:       { code: 'baldyz',       kk: 'Балдыз',          ru: 'Шурин / свояченица (мл.)', side: 'kayin', hint: 'Младшие братья и сёстры жены' },
  kuda:         { code: 'kuda',         kk: 'Құда',            ru: 'Сват',               side: 'kayin', hint: 'Отец мужа/жены вашего ребёнка' },
  kudagi:       { code: 'kudagi',       kk: 'Құдағи',          ru: 'Сватья',             side: 'kayin', hint: 'Мать мужа/жены вашего ребёнка' },

  tuys:         { code: 'tuys',         kk: 'Туыс',            ru: 'Родственник',        side: 'other', hint: 'Родство есть, но точного термина в справочнике нет' },
};
