CREATE TABLE IF NOT EXISTS osm_names
(
    osm_id    bigint,
    osm_type  character(1),
    all_names jsonb
);

INSERT INTO osm_names (osm_id, osm_type, all_names)
VALUES (27014, 'R', '{
  "name": "Kreis Schleswig-Flensburg",
  "name:de": "Kreis Schleswig-Flensburg",
  "name:is": "Slésvík-Flensborg",
  "name:nds": "Kreis Sleswig-Flensborg",
  "name:prefix": "Kreis"
}'),
       (27016, 'R', '{
         "name": "Kreis Steinburg",
         "name:de": "Kreis Steinburg",
         "name:nds": "Kreis Steenborg"
       }'),
       (27017, 'R', '{
         "name": "Kreis Rendsburg-Eckernförde",
         "name:de": "Kreis Rendsburg-Eckernförde",
         "name:nds": "Kreis Rendsborg-Eckernföör",
         "name:prefix": "Kreis"
       }'),
       (27019, 'R', '{
         "name": "Kreis Nordfriesland",
         "name:bg": "Северна Фризия",
         "name:ca": "Nordfriestland",
         "name:ceb": "Nordfriesland",
         "name:cs": "Severní Frísko",
         "name:da": "Nordfrisland",
         "name:de": "Kreis Nordfriesland",
         "name:en": "Northern Friesland",
         "name:eo": "Norda Frislando",
         "name:es": "Frisia Septentrional",
         "name:et": "Põhja-Friisimaa",
         "name:eu": "Nordfriesland",
         "name:fa": "نوردفرایسلند",
         "name:fi": "Nordfriesland",
         "name:fr": "Frise-du-Nord",
         "name:frr": "Nuurdfresklun",
         "name:fy": "Noard-Fryslân",
         "name:glk": "نؤردفرایسلند",
         "name:hr": "Sjeverna Frizija",
         "name:id": "Nordfriesland",
         "name:ie": "Nordfriesland",
         "name:it": "Frisia Settentrionale",
         "name:ka": "ჩრდილოეთ ფრიზიის",
         "name:kk": "Солтүстік Фрисландия",
         "name:ku": "Nordfriesland",
         "name:lld": "Nordfriesland",
         "name:mk": "Северна Фризија",
         "name:ms": "Nordfriesland",
         "name:nan": "Nordfriesland",
         "name:nds": "Noordfreesland",
         "name:nds-nl": "Noordfraislaand",
         "name:nl": "Nordfriesland",
         "name:no": "Nordfriesland",
         "name:pl": "Nordfriesland",
         "name:pnb": "نارڈفریزلینڈ",
         "name:prefix": "Kreis",
         "name:pt": "Frísia do Norte",
         "name:ro": "Frislanda de Nord",
         "name:ru": "Се́верная Фри́зия",
         "name:stq": "Nordfriesland",
         "name:sv": "Nordfriesland",
         "name:tr": "Kuzey Frizye",
         "name:uk": "Північна Фризія",
         "name:uz": "Nordfriesland",
         "name:vi": "Nordfriesland",
         "name:war": "Nordfriesland",
         "name:zh": "北弗里斯兰"
       }'),
       (27020, 'R', '{
         "name": "Flensburg",
         "name:ace": "Flensburg",
         "name:af": "Flensburg",
         "name:an": "Flensburg",
         "name:ar": "فلـِــنسبورغ",
         "name:arc": "ܦܠܢܣܒܘܪܓ",
         "name:az": "Flensburq",
         "name:azb": "فلنسبورق",
         "name:be": "Фле́нсбург",
         "name:bg": "Фленсбург",
         "name:bn": "ফ্লেন্সবুর্গ",
         "name:br": "Flensburg",
         "name:ca": "Flensburg",
         "name:ce": "Фленсбург",
         "name:ceb": "Flensburg",
         "name:cs": "Flensburg",
         "name:cy": "Flensburg",
         "name:da": "Flensborg",
         "name:de": "Flensburg",
         "name:el": "Φλένσμπουργκ",
         "name:en": "Flensburg",
         "name:eo": "Flensburg",
         "name:es": "Flensburgo",
         "name:et": "Flensburg",
         "name:eu": "Flensburg",
         "name:fa": "فلنسبورگ",
         "name:fi": "Flensburg",
         "name:fo": "Flensborg",
         "name:fr": "Flensbourg",
         "name:frr": "Flensborag",
         "name:fy": "Flensburch",
         "name:gl": "Flensburgo",
         "name:he": "פְלֶנְסבּוּרג",
         "name:hr": "Flensburg",
         "name:hsb": "Flensburg",
         "name:hu": "Flensburg",
         "name:ia": "Flensburg",
         "name:id": "Flensburg",
         "name:ie": "Flensburg",
         "name:is": "Flensborg",
         "name:it": "Flensburgo",
         "name:ja": "フレンスブルク",
         "name:ka": "ფლენსბურგი",
         "name:kk": "Фленсбург",
         "name:ko": "플렌스부르크",
         "name:ku": "Flensbûrx",
         "name:ky": "Фленсбург",
         "name:la": "Flensburgum",
         "name:lb": "Flensburg",
         "name:lld": "Flensburg",
         "name:lmo": "Flensburgh",
         "name:lt": "Flensburgas",
         "name:lv": "Flensburga",
         "name:mk": "Фленсбург",
         "name:ms": "Flensburg",
         "name:nds": "Flensborg",
         "name:nds-nl": "Flensbörg",
         "name:nl": "Flensburg",
         "name:nn": "Flensburg",
         "name:no": "Flensburg",
         "name:oc": "Flensburg",
         "name:os": "Фле́нсбург",
         "name:pl": "Flensburg",
         "name:pnb": "فلنزبرگ",
         "name:prefix": "Stadt",
         "name:pt": "Flensburgo",
         "name:ro": "Flensburg",
         "name:ru": "Фле́нсбург",
         "name:sco": "Flensburg",
         "name:sh": "Flensburg",
         "name:sq": "Flensburgu",
         "name:sr": "Фленсбург",
         "name:stq": "Flensbuurich",
         "name:sv": "Flensburg",
         "name:sw": "Flensburg",
         "name:th": "เฟล็นส์บวร์ค",
         "name:tr": "Flensburg",
         "name:tt": "Фленсбург",
         "name:tum": "Flensburg",
         "name:uk": "Фленсбург",
         "name:uz": "Flensburg",
         "name:vi": "Flensburg",
         "name:vo": "Flensburg",
         "name:war": "Flensburg",
         "name:wuu": "弗伦斯堡",
         "name:xmf": "ფლენსბურგი",
         "name:yue": "弗倫斯堡",
         "name:zh": "弗伦斯堡"
       }'),
       (27021, 'R', '{
         "name": "Kiel",
         "name:ar": "كيل",
         "name:de": "Kiel",
         "name:el": "Κίελο",
         "name:fa": "کیل",
         "name:he": "קיל",
         "name:hu": "Kiel",
         "name:is": "Kíl",
         "name:ja": "キール",
         "name:ko": "킬",
         "name:la": "Kielia",
         "name:lt": "Kylis",
         "name:lv": "Ķīle",
         "name:nds": "Kiel",
         "name:pl": "Kilonia",
         "name:prefix": "Kreisfreie Stadt",
         "name:ru": "Киль",
         "name:sr": "Кил",
         "name:th": "คีล",
         "name:uk": "Кіль",
         "name:ur": "کیل",
         "name:zh": "基尔",
         "name:zh-Hans": "基尔",
         "name:zh-Hant": "基爾"
       }'),
       (27025, 'R', '{
         "name": "Kreis Ostholstein",
         "name:de": "Kreis Ostholstein",
         "name:nds": "Oostholsteen",
         "name:prefix": "Kreis"
       }'),
       (27026, 'R', '{
         "name": "Kreis Plön",
         "name:de": "Kreis Plön",
         "name:nds": "Kreis Plöön",
         "name:prefix": "Kreis"
       }'),
       (27027, 'R', '{
         "name": "Lübeck",
         "name:af": "Lübeck",
         "name:an": "Lübeck",
         "name:ar": "لوبك",
         "name:arc": "ܠܘܒܩ",
         "name:ast": "Lübeck",
         "name:az": "Lübek",
         "name:azb": "لوبک",
         "name:bar": "Lübeck",
         "name:be": "Любек",
         "name:be-tarask": "Любэк",
         "name:bg": "Любек",
         "name:br": "Lübeck",
         "name:bs": "Lübeck",
         "name:ca": "Lübeck",
         "name:ceb": "Lübeck",
         "name:co": "Lubecca",
         "name:cs": "Lubek",
         "name:csb": "Lubeka",
         "name:cv": "Любек",
         "name:cy": "Lübeck",
         "name:da": "Lybæk",
         "name:de": "Lübeck",
         "name:dsb": "Lübeck",
         "name:el": "Λίμπεκ",
         "name:en": "Lübeck",
         "name:eo": "Lubeko",
         "name:es": "Lübeck",
         "name:et": "Lübeck",
         "name:eu": "Lübeck",
         "name:fa": "لوبک",
         "name:fi": "Lyypekki",
         "name:fr": "Lübeck",
         "name:frr": "Lübeck",
         "name:fy": "Lübeck",
         "name:ga": "Lübeck",
         "name:gd": "Lübeck",
         "name:gl": "Lübeck",
         "name:he": "ליבק",
         "name:hr": "Lübeck",
         "name:hsb": "Lubica",
         "name:hu": "Lübeck",
         "name:hy": "Լյուբեկ",
         "name:id": "Lübeck",
         "name:ie": "Lübeck",
         "name:io": "Lübeck",
         "name:is": "Lýbika",
         "name:it": "Lubecca",
         "name:ja": "リューベック",
         "name:jv": "Lübeck",
         "name:ka": "ლიუბეკი",
         "name:ko": "뤼베크",
         "name:la": "Lubeca",
         "name:lb": "Lübeck",
         "name:lmo": "Lübeca",
         "name:lt": "Liubekas",
         "name:lv": "Lībeka",
         "name:mk": "Либек",
         "name:mr": "ल्युबेक",
         "name:nan": "Lübeck",
         "name:nds": "Lübeek",
         "name:nds-nl": "Lubeek",
         "name:nl": "Lübeck",
         "name:nn": "Lübeck",
         "name:no": "Lübeck",
         "name:oc": "Lübeck",
         "name:os": "Любек",
         "name:pl": "Lubeka",
         "name:pms": "Lubëcca",
         "name:pnb": "لوبک",
         "name:prefix": "Kreisfreie Stadt",
         "name:pt": "Lubeca",
         "name:ro": "Lübeck",
         "name:ru": "Лю́бек",
         "name:sco": "Lübeck",
         "name:sh": "Libek",
         "name:sk": "Lübeck",
         "name:sl": "Lübeck",
         "name:sla": "Liubice",
         "name:sq": "Lybek",
         "name:sr": "Либек",
         "name:stq": "Lübeck",
         "name:sv": "Lübeck",
         "name:sw": "Lübeck",
         "name:szl": "Lübeck",
         "name:th": "ลือเบค",
         "name:tr": "Lübeck",
         "name:tt": "Лүбек",
         "name:tw": "Lübeck",
         "name:uk": "Любек",
         "name:ur": "لوبک",
         "name:uz": "Lyubek",
         "name:vec": "Lubeca",
         "name:vi": "Lübeck",
         "name:vo": "Lübeck",
         "name:war": "Lübeck",
         "name:yue": "呂碧克",
         "name:zh": "吕贝克"
       }'),
       (27028, 'R', '{
         "name": "Kreis Dithmarschen",
         "name:de": "Kreis Dithmarschen",
         "name:nds": "Dithmarschen",
         "name:prefix": "Kreis"
       }'),
       (28322, 'R', '{
         "name": "Mecklenburg-Vorpommern",
         "name:ar": "مكلنبورغ فوربمرن",
         "name:be": "Мекленбург — Пярэдняя Памеранія",
         "name:br": "Mecklenburg-Pomerania ar Chornôg",
         "name:ca": "Mecklemburg – Pomerània Occidental",
         "name:de": "Mecklenburg-Vorpommern",
         "name:el": "Μεκλεμβούργο-Δυτική Πομερανία",
         "name:en": "Mecklenburg-Vorpommern",
         "name:eo": "Meklenburgo-Antaŭpomerio",
         "name:es": "Mecklemburgo-Pomerania Occidental",
         "name:fa": "مکلنبورگ-فورپومرن",
         "name:fi": "Mecklenburg-Etu-Pommeri",
         "name:fr": "Mecklembourg-Poméranie-Occidentale",
         "name:ga": "Mecklenburg-Vorpommern",
         "name:hsb": "Mecklenburgsko-Předpomorska",
         "name:hu": "Mecklenburg–Elő-Pomeránia",
         "name:ia": "Mecklenburg-Vorpommeria",
         "name:io": "Mecklenburg-Westa Pomerania",
         "name:it": "Meclemburgo-Pomerania Anteriore",
         "name:ja": "メクレンブルク=フォアポンメルン州",
         "name:ko": "메클렌부르크포어포메른",
         "name:mk": "Мекленбург-Западна Померанија",
         "name:nds": "Mekelnborg-Vörpommern",
         "name:nl": "Mecklenburg-Voor-Pommeren",
         "name:pl": "Meklemburgia-Pomorze Przednie",
         "name:prefix": "Bundesland",
         "name:pt": "Mecklemburgo-Pomerânia Ocidental",
         "name:ro": "Mecklenburg - Pomerania Inferioară",
         "name:ru": "Мекленбург — Передняя Померания",
         "name:sk": "Meklenbursko-Predpomoransko",
         "name:sl": "Mecklenburg-Predpomorjanska",
         "name:sr": "Мекленбург-Западна Померанија",
         "name:th": "รัฐเมคเลินบวร์ค-ฟอร์พ็อมเมิร์น",
         "name:uk": "Мекленбург — Передня Померанія",
         "name:ur": "مکلنبرگ-ورپورمرن",
         "name:vo": "Mäklänburgän-Vesudapomerän",
         "name:zh": "梅克伦堡—前波美拉尼亚州",
         "name:zh-Hans": "梅克伦堡—前波美拉尼亚州",
         "name:zh-Hant": "梅克倫堡—西波美拉尼亞州"
       }'),
       (28936, 'R', '{
         "name": "Bergedorf",
         "name:nds": "Bardörp",
         "name:prefix": "Stadtbezirk"
       }'),
       (28971, 'R', '{
         "name": "Hamburg-Mitte",
         "name:prefix": "Bezirk"
       }'),
       (30223, 'R', '{
         "name": "Altona",
         "name:prefix": "Stadtbezirk"
       }'),
       (30243, 'R', '{
         "name": "Eimsbüttel",
         "name:prefix": "Stadtbezirk"
       }'),
       (30352, 'R', '{
         "name": "Hamburg-Nord",
         "name:nds": "Hamborg-Noord",
         "name:prefix": "Stadtbezirk"
       }'),
       (30353, 'R', '{
         "name": "Wandsbek",
         "name:prefix": "Stadtbezirk"
       }'),
       (33211, 'R', '{
         "name": "Kisdorf"
       }'),
       (50046, 'R', '{
         "name": "Danmark",
         "name:ace": "Denmark",
         "name:af": "Denemarke",
         "name:als": "Dänemark",
         "name:am": "ዴንማርክ",
         "name:an": "Dinamarca",
         "name:ang": "Denemearc",
         "name:ar": "الدنمارك",
         "name:arc": "ܕܐܢܡܐܪܩ",
         "name:arz": "دنمارك",
         "name:ast": "Dinamarca",
         "name:az": "Danimarka",
         "name:ba": "Дания",
         "name:bar": "Dänemark",
         "name:bat-smg": "Danėjė",
         "name:bcl": "Denmark",
         "name:be": "Данія",
         "name:be-tarask": "Данія",
         "name:bg": "Дания",
         "name:bi": "Denmark",
         "name:bn": "ডেনমার্ক",
         "name:bo": "དན་མྲག",
         "name:bpy": "ডেনমার্ক",
         "name:br": "Danmark",
         "name:bs": "Danska",
         "name:bxr": "Дани",
         "name:ca": "Dinamarca",
         "name:cdo": "Dăng-măk",
         "name:ce": "Дани",
         "name:ceb": "Dinamarka",
         "name:chr": "ᏕᏂᎹᎩ",
         "name:ckb": "دانمارک",
         "name:co": "Danimarca",
         "name:crh": "Danimarka",
         "name:cs": "Dánsko",
         "name:csb": "Dëńskô",
         "name:cu": "Данїꙗ",
         "name:cv": "Дани",
         "name:cy": "Denmarc",
         "name:da": "Danmark",
         "name:de": "Dänemark",
         "name:diq": "Danimarka",
         "name:dsb": "Dańska",
         "name:dv": "ޑެންމާކު",
         "name:dz": "ཌེན་མཱཀ་",
         "name:ee": "Denmark",
         "name:el": "Δανία",
         "name:en": "Denmark",
         "name:eo": "Danio",
         "name:es": "Dinamarca",
         "name:et": "Taani",
         "name:eu": "Danimarka",
         "name:ext": "Dinamarca",
         "name:fa": "دانمارک",
         "name:fi": "Tanska",
         "name:fo": "Danmark",
         "name:fr": "Danemark",
         "name:frp": "Danemârc",
         "name:frr": "Dånmark",
         "name:fur": "Danimarcje",
         "name:fy": "Denemark",
         "name:ga": "An Danmhairg",
         "name:gag": "Daniya",
         "name:gan": "丹麥",
         "name:gd": "An Danmhairg",
         "name:gl": "Dinamarca",
         "name:gn": "Ndinamayka",
         "name:gsw": "Dänemàrik",
         "name:gv": "Yn Danvarg",
         "name:hak": "Tan-ma̍k",
         "name:haw": "Kenemaka",
         "name:he": "דנמרק",
         "name:hi": "डेनमार्क",
         "name:hif": "Denmark",
         "name:hr": "Danska",
         "name:hsb": "Danska",
         "name:ht": "Danmak",
         "name:hu": "Dánia",
         "name:hy": "Դանիա",
         "name:ia": "Danmark",
         "name:id": "Denmark",
         "name:ie": "Dania",
         "name:ilo": "Dinamarka",
         "name:io": "Dania",
         "name:is": "Danmörk",
         "name:it": "Danimarca",
         "name:ja": "デンマーク",
         "name:jbo": "danmark",
         "name:jv": "Denmark",
         "name:ka": "დანია",
         "name:kaa": "Daniya",
         "name:kab": "Danmaṛk",
         "name:kbd": "Даниэ",
         "name:kg": "Danemark",
         "name:ki": "Denmark",
         "name:kk": "Дания",
         "name:kl": "Danmarki",
         "name:kn": "ಡೆನ್ಮಾರ್ಕ್",
         "name:ko": "덴마크",
         "name:koi": "Данмарк",
         "name:krc": "Дания",
         "name:ksh": "Dänemark",
         "name:ku": "Danîmarka",
         "name:kv": "Дания",
         "name:kw": "Danmark",
         "name:ky": "Дания",
         "name:la": "Dania",
         "name:lad": "Danimarka",
         "name:lb": "Dänemark",
         "name:lg": "Denimaaka",
         "name:li": "Denemarke",
         "name:lij": "Danemarca",
         "name:lmo": "Danimarca",
         "name:ln": "Danemark",
         "name:lt": "Danija",
         "name:ltg": "Daneja",
         "name:lv": "Dānija",
         "name:lzh": "丹麥",
         "name:mdf": "Данмастор",
         "name:mg": "Danmarka",
         "name:mhr": "Даний",
         "name:mi": "Tenemāka",
         "name:mk": "Данска",
         "name:ml": "ഡെന്മാർക്ക്",
         "name:mn": "Дани",
         "name:mr": "डेन्मार्क",
         "name:ms": "Denmark",
         "name:mt": "Danimarka",
         "name:my": "ဒိန်းမတ်နိုင်ငံ",
         "name:na": "Denemark",
         "name:nah": "Dinamarca",
         "name:nan": "Tan-kok",
         "name:nap": "Danemarca",
         "name:nds": "Däänmark",
         "name:nds-nl": "Denmaark",
         "name:ne": "डेनमार्क",
         "name:nl": "Denemarken",
         "name:nn": "Danmark",
         "name:no": "Danmark",
         "name:nov": "Dania",
         "name:nrm": "Dannemar",
         "name:nv": "Déinish Dineʼé Bikéyah",
         "name:oc": "Danemarc",
         "name:or": "ଡେନମାର୍କ",
         "name:os": "Дани",
         "name:pag": "Dinamarka",
         "name:pam": "Dinamarka",
         "name:pap": "Dinamarka",
         "name:pcd": "Danemark",
         "name:pdc": "Denemarrick",
         "name:pih": "Denmark",
         "name:pl": "Dania",
         "name:pms": "Danimarca",
         "name:pnb": "ڈنمارک",
         "name:pnt": "Δανία",
         "name:ps": "ډېنمارک",
         "name:pt": "Dinamarca",
         "name:qu": "Dansuyu",
         "name:rm": "Danemarc",
         "name:rmy": "Danemarka",
         "name:rn": "Danemarke",
         "name:ro": "Danemarca",
         "name:roa-rup": "Danimarca",
         "name:roa-tara": "Danemarche",
         "name:ru": "Дания",
         "name:rue": "Даньско",
         "name:rw": "Danimarike",
         "name:sa": "डेनमार्क",
         "name:sah": "Дания",
         "name:sc": "Danimarca",
         "name:scn": "Danimarca",
         "name:sco": "Denmark",
         "name:se": "Dánmárku",
         "name:sh": "Danska",
         "name:si": "ඩෙන්මාර්කය",
         "name:sje": "Danmarrka",
         "name:sk": "Dánsko",
         "name:sl": "Danska",
         "name:smn": "Tanska",
         "name:sms": "Tanska",
         "name:so": "Denmark",
         "name:sq": "Danimarka",
         "name:sr": "Данска",
         "name:ss": "IDenimakhi",
         "name:st": "Denmark",
         "name:stq": "Deenemäärk",
         "name:su": "Dénmark",
         "name:sv": "Danmark",
         "name:sw": "Denmark",
         "name:szl": "Dynymark",
         "name:ta": "டென்மார்க்",
         "name:te": "డెన్మార్క్",
         "name:tet": "Dinamarka",
         "name:tg": "Дания",
         "name:th": "ประเทศเดนมาร์ก",
         "name:tk": "Daniýa",
         "name:tl": "Dinamarka",
         "name:tok": "ma Tansi",
         "name:tpi": "Denmak",
         "name:tr": "Danimarka",
         "name:tt": "Дания",
         "name:tzl": "Danmarc",
         "name:udm": "Дания",
         "name:ug": "دانىيە",
         "name:uk": "Данія",
         "name:ur": "ڈنمارک",
         "name:uz": "Daniya",
         "name:vec": "Danimarca",
         "name:vep": "Danii",
         "name:vi": "Đan Mạch",
         "name:vls": "Denemarkn",
         "name:vo": "Danän",
         "name:vro": "Taani",
         "name:war": "Dinamarka",
         "name:wo": "Danmaark",
         "name:xal": "Данскгин Нутг",
         "name:xmf": "დანია",
         "name:yi": "דענמארק",
         "name:yo": "Dẹ́nmárkì",
         "name:yue": "丹麥",
         "name:zea": "Denemarken",
         "name:zh": "丹麦",
         "name:zh-Hans": "丹麦",
         "name:zh-Hant": "丹麥",
         "name:zu": "IDenimaki"
       }'),
       (51477, 'R', '{
         "name": "Deutschland",
         "name:ab": "Алмантәыла",
         "name:ace": "Jeureuman",
         "name:af": "Duitsland",
         "name:ak": "Germany",
         "name:am": "ጀርመን",
         "name:an": "Alemanya",
         "name:ang": "Þēodscland",
         "name:ar": "ألمانيا",
         "name:arc": "ܓܪܡܢ",
         "name:arz": "المانيا",
         "name:ast": "Alemaña",
         "name:av": "Алмания",
         "name:ay": "Alimaña",
         "name:az": "Almaniya",
         "name:ba": "Германия",
         "name:bar": "Deitschland",
         "name:bat-smg": "Vuokītėjė",
         "name:bcl": "Alemanya",
         "name:be": "Германія",
         "name:be-tarask": "Нямеччына",
         "name:bg": "Германия",
         "name:bi": "Germany",
         "name:bm": "Jermani",
         "name:bn": "জার্মানি",
         "name:bo": "འཇར་མན།",
         "name:bpy": "জার্মানি",
         "name:br": "Alamagn",
         "name:bs": "Njemačka",
         "name:bug": "Jerman",
         "name:bxr": "Германи",
         "name:ca": "Alemanya",
         "name:cbk-zam": "Alemania",
         "name:cdo": "Dáik-guók",
         "name:ce": "Германи",
         "name:ceb": "Alemanya",
         "name:chr": "ᎠᏛᏥ",
         "name:chy": "Maevého éno","NAME:ckb":"ئاڵمانیا","NAME:co":"Germania","NAME:crh":"Almaniya","NAME:cs":"Německo","NAME:csb":"Miemieckô","NAME:cu":"Нѣмьци","NAME:cv":"Германи","NAME:cy":"Yr Almaen","NAME:da":"Tyskland","NAME:de":"Deutschland","NAME:diq":"Almanya","NAME:dsb":"Nimska","NAME:dv":"ޖަރުމަނުވިލާތް","NAME:dz":"ཇཱར་མ་ནི","NAME:ee":"Germany","NAME:el":"Γερμανία","NAME:eml":"Germâgna","NAME:en":"Germany","NAME:eo":"Germanio","NAME:es":"Alemania","NAME:et":"Saksamaa","NAME:eu":"Alemania","NAME:ext":"Alemaña","NAME:fa":"آلمان","NAME:ff":"Almaanya","NAME:fi":"Saksa","NAME:fo":"Týskland","NAME:fr":"Allemagne","NAME:frp":"Alemagne","NAME:frr":"Tjüschlönj","NAME:fur":"Gjermanie","NAME:fy":"Dútslân","NAME:ga":"An Ghearmáin","NAME:gag":"Germaniya","NAME:gan":"德國","NAME:gd":"A Ghearmailt","name:gl":"Alemaña","name:glk":"آلمان","name:gn":"Alemaña","name:gsw":"Ditschland","name:gu":"જર્મની","name:gv":"Yn Ghermaan","name:ha":"Jamus","name:hak":"Tet-koet","name:haw":"Kelemānia","name:he":"גרמניה","name:hi":"जर्मनी","name:hif":"Germany","name:hr":"Njemačka","name:hsb":"Němska","name:ht":"Almay","name:hu":"Németország","name:hy":"Գերմանիա","name:ia":"Germania","name:id":"Jerman","name:ie":"Germania","name:ig":"Jémanị","name:ilo":"Alemania","name:io":"Germania","name:is":"Þýskaland","name:it":"Germania","name:iu":"ᔮᒪᓂ","name:ja":"ドイツ","name:jbo":"dotygu e","NAME:jv":"Jerman","NAME:ka":"გერმანია","NAME:kaa":"Germaniya","NAME:kab":"Lalman","NAME:kbd":"Джэрмэн","NAME:kg":"Alemanyi","NAME:ki":"Germany","NAME:kk":"Германия Федеративтік Республикасы","NAME:kl":"Tyskit Nunaat","NAME:km":"អាល្លឺម៉ង់","NAME:kn":"ಜರ್ಮನಿ","NAME:ko":"독일","NAME:koi":"Немечму","NAME:krc":"Германия","NAME:ks":"جرمٔنی","NAME:ksh":"Dütschland","NAME:ku":"Almanya","NAME:kv":"Германия","NAME:kw":"Almayn","NAME:ky":"Германия","NAME:la":"Germania","NAME:lad":"Almania","NAME:lb":"Däitschland","NAME:lez":"Германия","NAME:lg":"Girimane","NAME:li":"Duutsjlandj","NAME:lij":"Germania","NAME:lmo":"Germania","NAME:ln":"Alémani","NAME:lo":"ປະເທດເຢັຽລະມັນ","NAME:lrc":"آلمان","NAME:lt":"Vokietija","NAME:ltg":"Vuoceja","NAME:lv":"Vācija","NAME:lzh":"德國","NAME:map-bms":"Jerman","NAME:mdf":"Германие мастор","NAME:mg":"Alemaina","NAME:mhr":"Немыч Эл","NAME:mi":"Tiamana","NAME:min":"Jerman","NAME:mk":"Германија","NAME:ml":"ജർമ്മനി","NAME:mn":"Герман","NAME:mo":"Ӂермания","NAME:mr":"जर्मनी","NAME:ms":"Jerman","NAME:mt":"Ġermanja","NAME:mwl":"Almanha","NAME:my":"ဂျာမနီနိုင်ငံ","NAME:myv":"Германия Мастор","NAME:mzn":"آلمان","NAME:na":"Djermani","NAME:nah":"Teutontlālpan","NAME:nan":"Tek-kok","NAME:nap":"Germania","NAME:nds":"Düütschland","NAME:nds-nl":"Duutslaand","NAME:ne":"जर्मनी","NAME:NEW":"जर्मनी","NAME:nl":"Duitsland","NAME:nn":"Tyskland","NAME:NO":"Tyskland","NAME:nov":"Germania","NAME:nrm":"Allemangne","NAME:nv":"Béésh Bichʼahii Bikéyah","NAME:oc":"Alemanha","NAME:OJ":"Agongosiwaki","NAME:OR":"ଜର୍ମାନୀ","NAME:os":"Герман","NAME:pa":"ਜਰਮਨੀ","NAME:pa-Arab":"جرمن","NAME:pag":"Alemanya","NAME:pam":"Alemania","NAME:pap":"Alemania","NAME:pcd":"Alemanne","NAME:pdc":"Deitschland","NAME:pfl":"Daitschlond","NAME:pih":"Doichland","NAME:pl":"Niemcy","NAME:pms":"Gërmania","NAME:pnb":"جرمن","NAME:pnt":"Γερμανία","NAME:ps":"آلمان","NAME:pt":"Alemanha","NAME:qu":"Alimanya","NAME:rm":"Germania","NAME:rmy":"Jermaniya","NAME:rn":"Ubudagi","NAME:ro":"Germania","NAME:roa-tara":"Germanie","NAME:ru":"Германия","NAME:rue":"Нїмецько","NAME:rw":"Ubudage","NAME:sa":"जर्मनी","NAME:sah":"Германия","NAME:sc":"Germània","NAME:scn":"Girmania","NAME:sco":"Germany","NAME:se":"Duiska","NAME:sh":"Nemačka","NAME:si":"ජර්මනිය","NAME:sk":"Nemecko","NAME:sl":"Nemčija","NAME:sm":"Siamani","NAME:smn":"Saksa","NAME:sms":"Saksslajânnam","NAME:so":"Jarmalka","NAME:sq":"Gjermania","NAME:sr":"Немачка","NAME:sr-Latn":"Nemačka","NAME:srn":"Doysrikondre","NAME:ss":"IJalimane","NAME:stq":"Düütsklound","NAME:su":"Jérman","NAME:sv":"Tyskland","NAME:sw":"Ujerumani","NAME:szl":"Niymce","NAME:ta":"செருமனி","NAME:te":"జర్మనీ","NAME:tet":"Alemaña","NAME:tg":"Олмон","NAME:th":"ประเทศเยอรมนี","NAME:ti":"ጀርመን","NAME:tk":"Germaniýa","NAME:tl":"Alemanya","NAME:tok":"ma Tosi","NAME:tpi":"Siamani","NAME:tr":"Almanya","NAME:ts":"Jarimani","NAME:tt":"Алмания","NAME:tum":"Germany","NAME:tw":"Gyaaman","NAME:ty":"Heremani","NAME:tzl":"Tzaratütsch","NAME:udm":"Германия","NAME:ug":"گېرمانىيە","NAME:uk":"Німеччина","NAME:ur":"جرمنی","NAME:uz":"Olmoniya","NAME:vec":"Germania","NAME:vep":"Saksanma","NAME:vi":"Đức","NAME:vls":"Duutsland","NAME:vo":"Deutän","NAME:vro":"Saksamaa","name:wa":"Almagne","name:war":"Alemanya","name:win":"Taaǧiri Mąą","name:wo":"Almaañ","name:xal":"Ниицәтә Немшин Орн","name:xh":"IJamani","name:xmf":"გერმანია","name:yi":"דייטשלאנד","name:yo":"Jẹ́mánì","name:yue":"德國","name:za":"Dwzgoz","name:zea":"Duutsland","name:zh":"德国;德國","name:zh-Hans":"德国","name:zh-Hant":"德國","name:zu":"IJalimani"}'),
       (51529, 'R', '{
         "name": "Schleswig-Holstein",
         "name:ar": "شليسفيغ هولشتاين",
         "name:ca": "Slesvig-Holstein",
         "name:cs": "Šlesvicko-Holštýnsko",
         "name:de": "Schleswig-Holstein",
         "name:el": "Σλέσβιχ-Χόλσταϊν",
         "name:en": "Schleswig-Holstein",
         "name:eo": "Ŝlesvigo-Holstinio",
         "name:fa": "اشلسویگ-هولشتاین",
         "name:frr": "Slaswik-Holstiinj",
         "name:hsb": "Šleswigsko-Holšteinska",
         "name:hu": "Schleswig-Holstein",
         "name:is": "Slésvík-Holtsetaland",
         "name:ja": "シュレースヴィヒ＝ホルシュタイン州",
         "name:ko": "슐레스비히홀슈타인",
         "name:lv": "Šlēsviga-Holšteina",
         "name:mk": "Шлезвиг-Холштајн",
         "name:nds": "Sleeswig-Holsteen",
         "name:nl": "Sleeswijk-Holstein",
         "name:pl": "Szlezwik-Holsztyn",
         "name:prefix": "Bundesland",
         "name:pt": "Eslésvico-Holsácia",
         "name:ro": "Schleswig-Holstein",
         "name:ru": "Шлезвиг-Гольштейн",
         "name:sk": "Šlezvicko-Holštajnsko",
         "name:sr": "Шлезвиг-Холштајн",
         "name:th": "รัฐชเลสวิช-ฮ็อลชไตน์",
         "name:uk": "Шлезвіґ-Гольштайн",
         "name:ur": "شلسویگ-ہولشتائن",
         "name:vo": "Jlesvigän-Holstän",
         "name:zh": "石勒苏益格—荷尔斯泰因州",
         "name:zh-Hans": "石勒苏益格—荷尔斯泰因州",
         "name:zh-Hant": "什勒斯維希—霍爾斯坦邦"
       }'),
       (53584, 'R', '{
         "name": "Uetersen",
         "name:prefix": "Stadt"
       }'),
       (62408, 'R', '{
         "name": "Kreis Pinneberg",
         "name:nds": "Kreis Pinnbarg"
       }'),
       (62482, 'R', '{
         "name": "Landkreis Stade",
         "name:fr": "Stade (arrondissement)",
         "name:ru": "Штаде",
         "name:uk": "Штаде"
       }'),
       (62528, 'R', '{
         "name": "Neumünster",
         "name:de": "Neumünster",
         "name:prefix": "Kreisfreie Stadt",
         "name:uk": "Ноймюнстер"
       }'),
       (62546, 'R', '{
         "name": "Kreis Stormarn",
         "name:de": "Kreis Stormarn",
         "name:nds": "Kreis Stormarn",
         "name:prefix": "Kreis"
       }'),
       (62703, 'R', '{
         "name": "Kreis Herzogtum Lauenburg",
         "name:de": "Kreis Herzogtum Lauenburg",
         "name:nds": "Kreis Hertogdom Loonborg",
         "name:prefix": "Kreis",
         "name:ru": "Герцогство Лауэнбург"
       }'),
       (62733, 'R', '{
         "name": "Kreis Segeberg",
         "name:nds": "Kreis Sebarg"
       }'),
       (62771, 'R', '{
         "name": "Niedersachsen",
         "name:ar": "سكسونيا السفلى",
         "name:ast": "Baxa Saxonia",
         "name:be": "Ніжняя Саксонія",
         "name:br": "Saks-Izel",
         "name:ca": "Baixa Saxònia",
         "name:ckb": "ساکسۆنیای خواروو",
         "name:cs": "Dolní Sasko",
         "name:de": "Niedersachsen",
         "name:el": "Κάτω Σαξονία",
         "name:en": "Lower Saxony",
         "name:eo": "Malsupra Saksio",
         "name:es": "Baja Sajonia",
         "name:fa": "نیدرزاکسن",
         "name:fi": "Ala-Saksi",
         "name:fr": "Basse-Saxe",
         "name:fy": "Nedersaksen",
         "name:hr": "Donja Saska",
         "name:hsb": "Delnja Sakska",
         "name:hu": "Alsó-Szászország",
         "name:ia": "Basse Saxonia",
         "name:io": "Infra-Saxonia",
         "name:it": "Bassa Sassonia",
         "name:ja": "ニーダーザクセン州",
         "name:ko": "니더작센",
         "name:ku": "Saksonya Jêrîn",
         "name:la": "Saxonia Inferior",
         "name:lt": "Žemutinė Saksonija",
         "name:lv": "Lejassaksija",
         "name:mk": "Долна Саксонија",
         "name:nl": "Nedersaksen",
         "name:pl": "Dolna Saksonia",
         "name:prefix": "Bundesland",
         "name:pt": "Baixa Saxônia",
         "name:ro": "Saxonia Inferioară",
         "name:ru": "Нижняя Саксония",
         "name:short": "NI",
         "name:sk": "Dolné Sasko",
         "name:sl": "Spodnja Saška",
         "name:sr": "Доња Саксонија",
         "name:th": "รัฐนีเดอร์ซัคเซิน",
         "name:uk": "Нижня Саксонія",
         "name:ur": "نیدرزاکسن",
         "name:vo": "Dona-Saxän",
         "name:zh": "下萨克森州",
         "name:zh-Hans": "下萨克森州",
         "name:zh-Hant": "下薩克森州"
       }'),
       (62782, 'R', '{
         "name": "Hamburg",
         "name:ar": "هامبورغ",
         "name:da": "Hamborg",
         "name:de": "Hamburg",
         "name:el": "Αμβούργο",
         "name:en": "Hamburg",
         "name:eo": "Hamburgo",
         "name:fa": "هامبورگ",
         "name:fr": "Hambourg",
         "name:frr": "Hamborj",
         "name:ga": "Hamburg",
         "name:gl": "Hamburgo",
         "name:hu": "Hamburg",
         "name:ja": "ハンブルク州",
         "name:ko": "함부르크",
         "name:lv": "Hamburga",
         "name:nds": "Hamborg",
         "name:pl": "Hamburg",
         "name:prefix": "Freie und Hansestadt",
         "name:pt": "Hamburgo",
         "name:ro": "Hamburg",
         "name:ru": "Гамбург",
         "name:sk": "Hamburg",
         "name:sr": "Хамбург",
         "name:th": "ฮัมบวร์ค",
         "name:uk": "Гамбурґ",
         "name:ur": "ہم برک",
         "name:zh": "汉堡",
         "name:zh-Hans": "汉堡",
         "name:zh-Hant": "漢堡"
       }'),
       (66482, 'R', '{
         "name": "Samtgemeinde Nordkehdingen"
       }'),
       (152790, 'R', '{
         "name": "Itzehoe",
         "name:prefix": "Stadt",
         "name:uk": "Ітцего"
       }'),
       (156381, 'R', '{
         "name": "Seedorf"
       }'),
       (156829, 'R', '{
         "name": "Börnsen"
       }'),
       (158057, 'R', '{
         "name": "Hohe Elbgeest",
         "name:prefix": "Amt"
       }'),
       (158443, 'R', '{
         "name": "Escheburg"
       }'),
       (158674, 'R', '{
         "name": "Kröppelshagen-Fahrendorf"
       }'),
       (158702, 'R', '{
         "name": "Hohenhorn"
       }'),
       (158720, 'R', '{
         "name": "Dassendorf"
       }'),
       (158726, 'R', '{
         "name": "Worth"
       }'),
       (158732, 'R', '{
         "name": "Hamwarde"
       }'),
       (158758, 'R', '{
         "name": "Wiershop"
       }'),
       (165535, 'R', '{
         "name": "Bendfeld"
       }'),
       (165897, 'R', '{
         "name": "Krummbek"
       }'),
       (167439, 'R', '{
         "name": "Schlesen"
       }'),
       (176736, 'R', '{
         "name": "Schönberg (Holstein)"
       }'),
       (176737, 'R', '{
         "name": "Fiefbergen"
       }'),
       (176738, 'R', '{
         "name": "Stakendorf"
       }'),
       (176739, 'R', '{
         "name": "Schwartbuck"
       }'),
       (176740, 'R', '{
         "name": "Köhn"
       }'),
       (176741, 'R', '{
         "name": "Fargau-Pratjau"
       }'),
       (180707, 'R', '{
         "name": "Niendorf"
       }'),
       (180916, 'R', '{
         "name": "Schnelsen"
       }'),
       (180922, 'R', '{
         "name": "Eidelstedt"
       }'),
       (181761, 'R', '{
         "name": "Höhndorf"
       }'),
       (181765, 'R', '{
         "name": "Fahren"
       }'),
       (182310, 'R', '{
         "name": "Lurup"
       }'),
       (183853, 'R', '{
         "name": "Osdorf"
       }'),
       (183854, 'R', '{
         "name": "Iserbrook"
       }'),
       (183872, 'R', '{
         "name": "Sülldorf"
       }'),
       (183881, 'R', '{
         "name": "Rissen"
       }'),
       (189454, 'R', '{
         "name": "Hohenfelde"
       }'),
       (189455, 'R', '{
         "name": "Tröndel"
       }'),
       (189482, 'R', '{
         "name": "Stoltenberg"
       }'),
       (190839, 'R', '{
         "name": "Eckernförde",
         "name:da": "Egernførde",
         "name:de": "Eckernförde",
         "name:nds": "Eckernföör",
         "name:prefix": "Stadt"
       }'),
       (251069, 'R', '{
         "name": "Passade"
       }'),
       (251070, 'R', '{
         "name": "Krokau"
       }'),
       (251074, 'R', '{
         "name": "Barsbek"
       }'),
       (279222, 'R', '{
         "name": "Grünendeich",
         "name:de": "Grünendeich",
         "name:nds": "Greundiek"
       }'),
       (279236, 'R', '{
         "name": "Tespe"
       }'),
       (285777, 'R', '{
         "name": "Fuhlsbüttel"
       }'),
       (285784, 'R', '{
         "name": "Langenhorn"
       }'),
       (286381, 'R', '{
         "name": "Lemsahl-Mellingstedt"
       }'),
       (286382, 'R', '{
         "name": "Poppenbüttel"
       }'),
       (286401, 'R', '{
         "name": "Hummelsbüttel"
       }'),
       (286521, 'R', '{
         "name": "Duvenstedt"
       }'),
       (286541, 'R', '{
         "name": "Wohldorf-Ohlstedt"
       }'),
       (288257, 'R', '{
         "name": "Wendtorf"
       }'),
       (288258, 'R', '{
         "name": "Stein"
       }'),
       (288259, 'R', '{
         "name": "Lutterbek"
       }'),
       (288260, 'R', '{
         "name": "Prasdorf"
       }'),
       (288261, 'R', '{
         "name": "Laboe"
       }'),
       (288262, 'R', '{
         "name": "Brodersdorf"
       }'),
       (288263, 'R', '{
         "name": "Probsteierhagen"
       }'),
       (288564, 'R', '{
         "name": "Bergstedt"
       }'),
       (288914, 'R', '{
         "name": "Panker"
       }'),
       (288915, 'R', '{
         "name": "Behrensdorf",
         "name:suffix": "(Ostsee)"
       }'),
       (288939, 'R', '{
         "name": "Hohwacht",
         "name:suffix": "(Ostsee)"
       }'),
       (288940, 'R', '{
         "name": "Blekendorf"
       }'),
       (288957, 'R', '{
         "name": "Kletkamp"
       }'),
       (288958, 'R', '{
         "name": "Högsdorf"
       }'),
       (288959, 'R', '{
         "name": "Kirchnüchel"
       }'),
       (288960, 'R', '{
         "name": "Dannau"
       }'),
       (288961, 'R', '{
         "name": "Helmstorf"
       }'),
       (288962, 'R', '{
         "name": "Klamp"
       }'),
       (288963, 'R', '{
         "name": "Lütjenburg",
         "name:prefix": "Stadt"
       }'),
       (288964, 'R', '{
         "name": "Giekau"
       }'),
       (289094, 'R', '{
         "name": "Wisch"
       }'),
       (289174, 'R', '{
         "name": "Dobersdorf"
       }'),
       (289958, 'R', '{
         "name": "Rastorf"
       }'),
       (289959, 'R', '{
         "name": "Martensrade"
       }'),
       (289960, 'R', '{
         "name": "Selent"
       }'),
       (289961, 'R', '{
         "name": "Lammershagen"
       }'),
       (289985, 'R', '{
         "name": "Heikendorf"
       }'),
       (295195, 'R', '{
         "name": "Volksdorf"
       }'),
       (295214, 'R', '{
         "name": "Rahlstedt"
       }'),
       (295444, 'R', '{
         "name": "Jenfeld"
       }'),
       (299456, 'R', '{
         "name": "Mönkeberg"
       }'),
       (299457, 'R', '{
         "name": "Schönkirchen"
       }'),
       (305532, 'R', '{
         "name": "Marschacht"
       }'),
       (308488, 'R', '{
         "name": "Drage",
         "name:suffix": "(Elbe)"
       }'),
       (309177, 'R', '{
         "name": "Rantzau"
       }'),
       (310382, 'R', '{
         "name": "Schwentinental",
         "name:prefix": "Stadt"
       }'),
       (310383, 'R', '{
         "name": "Lehmkuhlen"
       }'),
       (310384, 'R', '{
         "name": "Mucheln"
       }'),
       (310385, 'R', '{
         "name": "Grebin"
       }'),
       (310386, 'R', '{
         "name": "Lebrade"
       }'),
       (310387, 'R', '{
         "name": "Pohnsdorf"
       }'),
       (310388, 'R', '{
         "name": "Schellhorn"
       }'),
       (310389, 'R', '{
         "name": "Preetz",
         "name:prefix": "Stadt"
       }'),
       (310390, 'R', '{
         "name": "Honigsee"
       }'),
       (310391, 'R', '{
         "name": "Boksee"
       }'),
       (310392, 'R', '{
         "name": "Klein Barkau"
       }'),
       (310393, 'R', '{
         "name": "Großbarkau"
       }'),
       (310394, 'R', '{
         "name": "Kirchbarkau"
       }'),
       (310395, 'R', '{
         "name": "Bothkamp"
       }'),
       (310396, 'R', '{
         "name": "Warnau"
       }'),
       (310397, 'R', '{
         "name": "Löptin"
       }'),
       (310398, 'R', '{
         "name": "Kühren"
       }'),
       (310399, 'R', '{
         "name": "Wahlstorf"
       }'),
       (310400, 'R', '{
         "name": "Wittmoldt"
       }'),
       (310401, 'R', '{
         "name": "Rathjensdorf"
       }'),
       (310402, 'R', '{
         "name": "Plön",
         "name:prefix": "Stadt"
       }'),
       (310403, 'R', '{
         "name": "Bösdorf"
       }'),
       (310404, 'R', '{
         "name": "Nehmten"
       }'),
       (310405, 'R', '{
         "name": "Ascheberg",
         "name:suffix": "(Holstein)"
       }'),
       (310406, 'R', '{
         "name": "Dersau"
       }'),
       (310407, 'R', '{
         "name": "Kalübbe"
       }'),
       (310408, 'R', '{
         "name": "Belau"
       }'),
       (310409, 'R', '{
         "name": "Ruhwinkel"
       }'),
       (310410, 'R', '{
         "name": "Rendswühren"
       }'),
       (310411, 'R', '{
         "name": "Bönebüttel"
       }'),
       (310412, 'R', '{
         "name": "Tasdorf"
       }'),
       (310413, 'R', '{
         "name": "Großharrie"
       }'),
       (310414, 'R', '{
         "name": "Schillsdorf"
       }'),
       (310415, 'R', '{
         "name": "Wankendorf"
       }'),
       (310416, 'R', '{
         "name": "Stolpe"
       }'),
       (310417, 'R', '{
         "name": "Barmissen"
       }'),
       (310418, 'R', '{
         "name": "Nettelsee"
       }'),
       (310419, 'R', '{
         "name": "Postfeld"
       }'),
       (310466, 'R', '{
         "name": "Dörnick"
       }'),
       (312038, 'R', '{
         "name": "Ammersbek"
       }'),
       (321153, 'R', '{
         "name": "Wohltorf"
       }'),
       (324694, 'R', '{
         "name": "Aumühle"
       }'),
       (324756, 'R', '{
         "name": "Wentorf bei Hamburg"
       }'),
       (325771, 'R', '{
         "name": "Geesthacht",
         "name:nds": "Geesthacht",
         "name:prefix": "Stadt"
       }'),
       (331249, 'R', '{
         "name": "Brunstorf"
       }'),
       (335683, 'R', '{
         "name": "Reinbek",
         "name:prefix": "Stadt",
         "name:uk": "Райнбек"
       }'),
       (367834, 'R', '{
         "name": "Falkenfeld / Vorwerk"
       }'),
       (367835, 'R', '{
         "name": "Schlutup"
       }'),
       (367836, 'R', '{
         "name": "Eichholz"
       }'),
       (367837, 'R', '{
         "name": "Buntekuh"
       }'),
       (367838, 'R', '{
         "name": "Ivendorf"
       }'),
       (367839, 'R', '{
         "name": "Dornbreite"
       }'),
       (367840, 'R', '{
         "name": "Pöppendorf"
       }'),
       (367841, 'R', '{
         "name": "Karlshof / Israelsdorf / Gothmund"
       }'),
       (367842, 'R', '{
         "name": "Priwall"
       }'),
       (367843, 'R', '{
         "name": "Alt-Travemünde / Rönnau"
       }'),
       (367844, 'R', '{
         "name": "Brodten"
       }'),
       (367845, 'R', '{
         "name": "Teutendorf"
       }'),
       (367846, 'R', '{
         "name": "Herrenwyk"
       }'),
       (367847, 'R', '{
         "name": "Hüxtertor / Mühlentor / Gärtnergasse"
       }'),
       (367848, 'R', '{
         "name": "Burgtor / Stadtpark"
       }'),
       (367849, 'R', '{
         "name": "Dänischburg / Siems / Rangenberg / Wallberg"
       }'),
       (367850, 'R', '{
         "name": "Groß Steinrade / Schönböcken"
       }'),
       (367851, 'R', '{
         "name": "Marli / Brandenbaum"
       }'),
       (367852, 'R', '{
         "name": "Alt-Kücknitz / Dummersdorf / Roter Hahn"
       }'),
       (367853, 'R', '{
         "name": "Holstentor-Nord"
       }'),
       (367854, 'R', '{
         "name": "Sankt Lorenz Süd"
       }'),
       (367855, 'R', '{
         "name": "Innenstadt"
       }'),
       (367856, 'R', '{
         "name": "Blankensee"
       }'),
       (367857, 'R', '{
         "name": "Strecknitz"
       }'),
       (367858, 'R', '{
         "name": "Schiereichenkoppel"
       }'),
       (367859, 'R', '{
         "name": "Alt-Moisling / Genin"
       }'),
       (367860, 'R', '{
         "name": "Niendorf / Moorgarten"
       }'),
       (367861, 'R', '{
         "name": "Reecke"
       }'),
       (367862, 'R', '{
         "name": "Kronsforde"
       }'),
       (367863, 'R', '{
         "name": "Krummesse"
       }'),
       (367864, 'R', '{
         "name": "Wulfsdorf"
       }'),
       (367865, 'R', '{
         "name": "Beidendorf"
       }'),
       (367866, 'R', '{
         "name": "Niederbüssau"
       }'),
       (367867, 'R', '{
         "name": "Vorrade"
       }'),
       (367868, 'R', '{
         "name": "Sankt Jürgen"
       }'),
       (367869, 'R', '{
         "name": "Oberbüssau"
       }'),
       (367870, 'R', '{
         "name": "Moisling"
       }'),
       (367871, 'R', '{
         "name": "Sankt Lorenz Nord"
       }'),
       (367872, 'R', '{
         "name": "Sankt Gertrud"
       }'),
       (367873, 'R', '{
         "name": "Kücknitz"
       }'),
       (367874, 'R', '{
         "name": "Travemünde"
       }'),
       (382407, 'R', '{
         "name": "Süsel"
       }'),
       (382408, 'R', '{
         "name": "Eutin",
         "name:nds": "Eutin",
         "name:prefix": "Stadt"
       }'),
       (382409, 'R', '{
         "name": "Altenkrempe"
       }'),
       (382410, 'R', '{
         "name": "Harmsdorf"
       }'),
       (382411, 'R', '{
         "name": "Lensahn"
       }'),
       (382412, 'R', '{
         "name": "Beschendorf"
       }'),
       (382413, 'R', '{
         "name": "Manhagen"
       }'),
       (382414, 'R', '{
         "name": "Kabelhorst"
       }'),
       (382415, 'R', '{
         "name": "Riepsdorf"
       }'),
       (382416, 'R', '{
         "name": "Damlos"
       }'),
       (382417, 'R', '{
         "name": "Göhl"
       }'),
       (382418, 'R', '{
         "name": "Großenbrode"
       }'),
       (382419, 'R', '{
         "name": "Heiligenhafen",
         "name:prefix": "Stadt"
       }'),
       (382420, 'R', '{
         "name": "Gremersdorf"
       }'),
       (382421, 'R', '{
         "name": "Oldenburg in Holstein",
         "name:prefix": "Stadt"
       }'),
       (382422, 'R', '{
         "name": "Neukirchen"
       }'),
       (382423, 'R', '{
         "name": "Heringsdorf"
       }'),
       (382424, 'R', '{
         "name": "Grube"
       }'),
       (382425, 'R', '{
         "name": "Wangels"
       }'),
       (382426, 'R', '{
         "name": "Dahme"
       }'),
       (382427, 'R', '{
         "name": "Kellenhusen",
         "name:suffix": "(Ostsee)"
       }'),
       (382428, 'R', '{
         "name": "Grömitz"
       }'),
       (382429, 'R', '{
         "name": "Schashagen"
       }'),
       (382430, 'R', '{
         "name": "Schönwalde am Bungsberg"
       }'),
       (382431, 'R', '{
         "name": "Kasseedorf"
       }'),
       (382433, 'R', '{
         "name": "Neustadt in Holstein",
         "name:ar": "نوياشتات اين هل اشتاين",
         "name:azb": "نوی‌اشتات این هل‌اشتاین",
         "name:ce": "Нойштадт в Гольштейн",
         "name:ceb": "Neustadt sa Holstein",
         "name:da": "Neustadt i Østholsten",
         "name:de": "Neustadt in Holstein",
         "name:en": "Neustadt in Holstein",
         "name:eo": "Neustadt en Holstinio",
         "name:es": "Neustadt in Holstein",
         "name:eu": "Neustadt in Holstein",
         "name:fa": "نوی‌اشتات این هل‌اشتاین",
         "name:fi": "Neustadt sisään Holstein",
         "name:fr": "Neustadt en Holstein",
         "name:frr": "Neustadt in Holstein",
         "name:hu": "Neustadt in Holstein",
         "name:it": "Neustadt in Holstein",
         "name:kk": "Нойштадт жылы Гольштейн",
         "name:mk": "Нојштат во Холштајн",
         "name:ms": "Neustadt di Holstein",
         "name:nds": "Niestadt in Holsteen",
         "name:nl": "Neustadt in Holstein",
         "name:no": "Neustadt i Øst-Holstein",
         "name:pl": "Neustadt w Holsztyn",
         "name:prefix": "Stadt",
         "name:pt": "Neustadt na Holstein",
         "name:ru": "Нойштадт-ин-Хольштайн",
         "name:sh": "Nojštat in Holštajn",
         "name:sr": "Нојштат ин Холштајн",
         "name:sv": "Neustadt i Holstein",
         "name:tr": "Neustadt Holsteinnın","NAME:tt":"Нойштадт Гольштейн","NAME:uk":"Нойштадт у Гольштейн","NAME:uz":"Holsteinda Neustadt","NAME:vi":"Neustadt ở Holstein","NAME:vo":"Neustadt IN Holstein","NAME:war":"Neustadt ha Holstein","NAME:zh":"霍尔斯泰因地区诺伊斯塔特"}'),
(382434, 'R','{"NAME":"Sierksdorf"}'),
(382435, 'R','{"NAME":"Malente"}'),
(382441, 'R','{"NAME":"Bosau"}'),
(382442, 'R','{"NAME":"Ahrensbök"}'),
(382443, 'R','{"NAME":"Scharbeutz"}'),
(382444, 'R','{"NAME":"Timmendorfer Strand"}'),
(382445, 'R','{"NAME":"Ratekau"}'),
(382446, 'R','{"NAME":"Bad Schwartau","NAME:prefix":"Stadt","NAME:ru":"Бад-Швартау"}'),
(382447, 'R','{"NAME":"Stockelsdorf"}'),
(382448, 'R','{"NAME":"Fehmarn","NAME:prefix":"Stadt"}'),
(401824, 'R','{"NAME":"Heider Umland","NAME:prefix":"Amt"}'),
(403810, 'R','{"NAME":"Mitteldithmarschen","NAME:prefix":"Amt"}'),
(404298, 'R','{"NAME":"Glasau"}'),
(404324, 'R','{"NAME":"Travenhorst"}'),
(404614, 'R','{"NAME":"Altengamme"}'),
(404615, 'R','{"NAME":"Curslack"}'),
(404618, 'R','{"NAME":"Bergedorf"}'),
(404943, 'R','{"NAME":"Lohbrügge"}'),
(405626, 'R','{"NAME":"Stocksee"}'),
(405627, 'R','{"NAME":"Schmalensee"}'),
(405629, 'R','{"NAME":"Bornhöved"}'),
(405630, 'R','{"NAME":"Gönnebek"}'),
(405631, 'R','{"NAME":"Trappenkamp"}'),
(406332, 'R','{"NAME":"Tarbek"}'),
(406356, 'R','{"NAME":"Tensfeld"}'),
(406901, 'R','{"NAME":"Nehms"}'),
(410284, 'R','{"NAME":"Heide","NAME:nds":"Heid","NAME:prefix":"Stadt"}'),
(411624, 'R','{"NAME":"Damsdorf"}'),
(412078, 'R','{"NAME":"Wöhrden"}'),
(412881, 'R','{"NAME":"Nordhastedt"}'),
(412906, 'R','{"NAME":"Hemmingstedt"}'),
(412962, 'R','{"NAME":"Lieth"}'),
(412963, 'R','{"NAME":"Lohe-Rickelshof"}'),
(413110, 'R','{"NAME":"Wesseln"}'),
(413291, 'R','{"NAME":"Ostrohe"}'),
(413440, 'R','{"NAME":"Weddingstedt"}'),
(415685, 'R','{"NAME":"Norderwöhrden"}'),
(415686, 'R','{"NAME":"Stelle-Wittenwurth"}'),
(415687, 'R','{"NAME":"Neuenkirchen"}'),
(415727, 'R','{"NAME":"Nordermeldorf"}'),
(416356, 'R','{"NAME":"Epenwöhrden"}'),
(416357, 'R','{"NAME":"Meldorf","NAME:nds":"Meldörp","NAME:prefix":"Stadt"}'),
(417591, 'R','{"NAME":"Busenwurth"}'),
(417592, 'R','{"NAME":"Elpersbüttel"}'),
(418085, 'R','{"NAME":"Barlt"}'),
(418220, 'R','{"NAME":"Gudendorf"}'),
(418222, 'R','{"NAME":"Windbergen"}'),
(418230, 'R','{"NAME":"Wolmersdorf"}'),
(418260, 'R','{"NAME":"Krumstedt"}'),
(419144, 'R','{"NAME":"Trittau"}'),
(420611, 'R','{"NAME":"Nindorf"}'),
(420612, 'R','{"NAME":"Bargenstedt"}'),
(422634, 'R','{"NAME":"Norderstedt","NAME:prefix":"Stadt","NAME:uk":"Нордерштедт"}'),
(422677, 'R','{"NAME":"Wakendorf II"}'),
(422678, 'R','{"NAME":"Kayhude"}'),
(422706, 'R','{"NAME":"Nahe"}'),
(422730, 'R','{"NAME":"Sülfeld"}'),
(422731, 'R','{"NAME":"Fredesdorf"}'),
(422732, 'R','{"NAME":"Groß Niendorf"}'),
(422747, 'R','{"NAME":"Leezen"}'),
(422748, 'R','{"NAME":"Kükels"}'),
(422749, 'R','{"NAME":"Struvenhütten"}'),
(422750, 'R','{"NAME":"Sievershütten"}'),
(422751, 'R','{"NAME":"Hüttblek"}'),
(422763, 'R','{"NAME":"Schwissel"}'),
(422764, 'R','{"NAME":"Bebensee"}'),
(422765, 'R','{"NAME":"Dreggers"}'),
(422814, 'R','{"NAME":"Wakendorf I"}'),
(422815, 'R','{"NAME":"Bahrenhof"}'),
(422816, 'R','{"NAME":"Bühnsdorf"}'),
(422841, 'R','{"NAME":"Neuengörs"}'),
(422842, 'R','{"NAME":"Traventhal"}'),
(422843, 'R','{"NAME":"Högersdorf"}'),
(422844, 'R','{"NAME":"Fahrenkrug"}'),
(422845, 'R','{"NAME":"Wittenborn"}'),
(422846, 'R','{"NAME":"Mözen"}'),
(422847, 'R','{"NAME":"Todesfelde"}'),
(422848, 'R','{"NAME":"Stuvenborn"}'),
(422854, 'R','{"NAME":"Oering"}'),
(422855, 'R','{"NAME":"Seth"}'),
(422875, 'R','{"NAME":"Wahlstedt","NAME:prefix":"Stadt"}'),
(422876, 'R','{"NAME":"Negernbötel"}'),
(422877, 'R','{"NAME":"Schackendorf"}'),
(422878, 'R','{"NAME":"Daldorf"}'),
(422879, 'R','{"NAME":"Bark"}'),
(422880, 'R','{"NAME":"Hartenholm"}'),
(422932, 'R','{"NAME":"Sarzbüttel"}'),
(422934, 'R','{"NAME":"Odderade"}'),
(422974, 'R','{"NAME":"Schafstedt"}'),
(423032, 'R','{"NAME":"Albersdorf"}'),
(423041, 'R','{"NAME":"Tensbüttel-Röst"}'),
(423046, 'R','{"NAME":"Arkebek"}'),
(423149, 'R','{"NAME":"Groß Kummerfeld"}'),
(423150, 'R','{"NAME":"Latendorf"}'),
(423151, 'R','{"NAME":"Rickling"}'),
(423152, 'R','{"NAME":"Heidmühlen"}'),
(423179, 'R','{"NAME":"Bimöhlen"}'),
(423180, 'R','{"NAME":"Schmalfeld"}'),
(423181, 'R','{"NAME":"Hasenmoor"}'),
(423182, 'R','{"NAME":"Lentföhrden"}'),
(423183, 'R','{"NAME":"Nützen"}'),
(423184, 'R','{"NAME":"Kaltenkirchen","NAME:prefix":"Stadt","NAME:uk":"Кальтенкірхен"}'),
(423185, 'R','{"NAME":"Weddelbrook"}'),
(423186, 'R','{"NAME":"Föhrden-Barl"}'),
(423187, 'R','{"NAME":"Hagen"}'),
(423188, 'R','{"NAME":"Fuhlendorf"}'),
(423189, 'R','{"NAME":"Borstel"}'),
(423190, 'R','{"NAME":"Hardebek"}'),
(423191, 'R','{"NAME":"Armstedt"}'),
(423222, 'R','{"NAME":"Wiemersdorf"}'),
(423223, 'R','{"NAME":"Bad Bramstedt","NAME:prefix":"Stadt","NAME:uk":"Бад-Брамштедт"}'),
(423224, 'R','{"NAME":"Hitzhusen"}'),
(423225, 'R','{"NAME":"Großenaspe"}'),
(423226, 'R','{"NAME":"Boostedt"}'),
(423230, 'R','{"NAME":"Amt Bad Bramstedt-Land"}'),
(423231, 'R','{"NAME":"Mönkloh"}'),
(423232, 'R','{"NAME":"Heidmoor"}'),
(430468, 'R','{"NAME":"Kattendorf"}'),
(430469, 'R','{"NAME":"Oersdorf"}'),
(430471, 'R','{"NAME":"Winsen"}'),
(430480, 'R','{"NAME":"Kisdorf","NAME:prefix":"Amt"}'),
(442742, 'R','{"NAME":"Leezen","NAME:prefix":"Amt"}'),
(442744, 'R','{"NAME":"Itzstedt","NAME:prefix":"Amt"}'),
(442762, 'R','{"NAME":"Henstedt-Ulzburg","NAME:ru":"Хенштедт-Ульцбург"}'),
(442763, 'R','{"NAME":"Ellerau","NAME:ru":"Эллерау"}'),
(442764, 'R','{"NAME":"Alveslohe"}'),
(442765, 'R','{"NAME":"Auenland Südholstein","NAME:prefix":"Amt"}'),
(442874, 'R','{"NAME":"Boostedt-Rickling","NAME:prefix":"Amt"}'),
(442882, 'R','{"NAME":"Bornhöved","NAME:prefix":"Amt"}'),
(442911, 'R','{"NAME":"Trave-Land","NAME:prefix":"Amt"}'),
(442912, 'R','{"NAME":"Bad Segeberg","NAME:nds":"Bad Seebarg","NAME:prefix":"Stadt"}'),
(442913, 'R','{"NAME":"Klein Gladebrügge"}'),
(442914, 'R','{"NAME":"Weede"}'),
(442915, 'R','{"NAME":"Geschendorf"}'),
(442916, 'R','{"NAME":"Strukdorf"}'),
(442917, 'R','{"NAME":"Stipsdorf"}'),
(442918, 'R','{"NAME":"Pronstorf"}'),
(442919, 'R','{"NAME":"Krems II"}'),
(442926, 'R','{"NAME":"Groß Rönnau"}'),
(442927, 'R','{"NAME":"Blunk"}'),
(442929, 'R','{"NAME":"Rohlstorf"}'),
(442930, 'R','{"NAME":"Wensin"}'),
(442931, 'R','{"NAME":"Schieren"}'),
(442932, 'R','{"NAME":"Klein Rönnau"}'),
(443081, 'R','{"NAME":"Schrum"}'),
(443085, 'R','{"NAME":"Immenstedt"}'),
(443086, 'R','{"NAME":"Osterrade"}'),
(443103, 'R','{"NAME":"Wennbüttel"}'),
(443121, 'R','{"NAME":"Bunsoh"}'),
(443122, 'R','{"NAME":"Offenbüttel"}'),
(443183, 'R','{"NAME":"Hetlingen"}'),
(443184, 'R','{"NAME":"Haseldorf"}'),
(443185, 'R','{"NAME":"Heist"}'),
(443186, 'R','{"NAME":"Haselau"}'),
(443192, 'R','{"NAME":"Seestermühe"}'),
(443193, 'R','{"NAME":"Neuendeich"}'),
(443194, 'R','{"NAME":"Moorrege"}'),
(443283, 'R','{"NAME":"Groß Nordende"}'),
(443285, 'R','{"NAME":"Heidgraben"}'),
(443298, 'R','{"NAME":"Seester"}'),
(443299, 'R','{"NAME":"Raa-Besenbek"}'),
(443302, 'R','{"NAME":"Elmshorn","NAME:prefix":"Stadt","NAME:uk":"Ельмсгорн"}'),
(443339, 'R','{"NAME":"Tornesch","NAME:prefix":"Stadt"}'),
(443358, 'R','{"NAME":"Prisdorf"}'),
(443359, 'R','{"NAME":"Kummerfeld"}'),
(443360, 'R','{"NAME":"Ellerhoop"}'),
(443439, 'R','{"NAME":"Seeth-Ekholt"}'),
(443484, 'R','{"NAME":"Appen"}'),
(443485, 'R','{"NAME":"Schenefeld","NAME:prefix":"Stadt"}'),
(443486, 'R','{"NAME":"Halstenbek","NAME:nds":"Halstenbeek"}'),
(443487, 'R','{"NAME":"Pinneberg","NAME:frr":"Pinebärj","NAME:nds":"Pinnbarg","NAME:prefix":"Stadt","NAME:uk":"Піннеберґ"}'),
(443703, 'R','{"NAME":"Ellerbek"}'),
(443704, 'R','{"NAME":"Bönningstedt","NAME:nds":"Bönningsteed"}'),
(443705, 'R','{"NAME":"Hasloh","NAME:nds":"Hasloh"}'),
(443706, 'R','{"NAME":"Tangstedt"}'),
(443731, 'R','{"NAME":"Borstel-Hohenraden"}'),
(443732, 'R','{"NAME":"Pinnau","NAME:prefix":"Amt"}'),
(443748, 'R','{"NAME":"Amt Geest und Marsch Südholstein","NAME:prefix":"Amt"}'),
(443749, 'R','{"NAME":"Quickborn","NAME:prefix":"Stadt","NAME:ru":"Квикборн","NAME:uk":"Квікборн"}'),
(443755, 'R','{"NAME":"Rellingen"}'),
(443947, 'R','{"NAME":"Klein Offenseth-Sparrieshoop","NAME:nds":"Lütt Offenseet-Sparrshoop"}'),
(443948, 'R','{"NAME":"Bokholt-Hanredder","NAME:nds":"Bookholt-Hanredder"}'),
(443949, 'R','{"NAME":"Kölln-Reisiek"}'),
(443950, 'R','{"NAME":"Klein Nordende"}'),
(443951, 'R','{"NAME":"Elmshorn-Land","NAME:prefix":"Amt"}'),
(443953, 'R','{"NAME":"Bullenkuhlen"}'),
(444135, 'R','{"NAME":"Groß Offenseth-Aspern"}'),
(444140, 'R','{"NAME":"Barmstedt","NAME:prefix":"Stadt"}'),
(444152, 'R','{"NAME":"Bilsen"}'),
(444153, 'R','{"NAME":"Bevern"}'),
(444154, 'R','{"NAME":"Hemdingen"}'),
(444155, 'R','{"NAME":"Langeln"}'),
(444156, 'R','{"NAME":"Heede"}'),
(444180, 'R','{"NAME":"Lutzhorn"}'),
(444181, 'R','{"NAME":"Brande-Hörnerkirchen"}'),
(444182, 'R','{"NAME":"Westerhorn"}'),
(444183, 'R','{"NAME":"Osterhorn"}'),
(444184, 'R','{"NAME":"Bokel"}'),
(444185, 'R','{"NAME":"Hörnerkirchen","NAME:prefix":"Amt"}'),
(444189, 'R','{"NAME":"Rantzau","NAME:prefix":"Amt"}'),
(444231, 'R','{"NAME":"Neversdorf"}'),
(444300, 'R','{"NAME":"Tangstedt"}'),
(444323, 'R','{"NAME":"Itzstedt"}'),
(444768, 'R','{"NAME":"Drochtersen","NAME:nds":"Drochters"}'),
(444799, 'R','{"NAME":"Stade","NAME:nds":"Stood","NAME:prefix":"Hansestadt","NAME:uk":"Штаде"}'),
(444923, 'R','{"NAME":"Hollern-Twielenfleth","NAME:de":"Hollern-Twielenfleth","NAME:nds":"Hullern-Twielenfleth"}'),
(444924, 'R','{"NAME":"Steinkirchen","NAME:nds":"Steenkark"}'),
(444930, 'R','{"NAME":"Samtgemeinde Lühe"}'),
(445505, 'R','{"NAME":"Hasenkrug"}'),
(445788, 'R','{"NAME":"Warwerort"}'),
(446464, 'R','{"NAME":"Wischhafen","NAME:nds":"Wischhoben"}'),
(446465, 'R','{"NAME":"Freiburg (Elbe)","NAME:prefix":"Flecken"}'),
(446466, 'R','{"NAME":"Krummendeich","NAME:nds":"Krummendiek"}'),
(446467, 'R','{"NAME":"Balje"}'),
(446508, 'R','{"NAME":"Büsum-Wesselburen","NAME:prefix":"Amt"}'),
(447159, 'R','{"NAME":"Büsumer Deichhausen"}'),
(447160, 'R','{"NAME":"Büsum","NAME:ru":"Бюзум"}'),
(447161, 'R','{"NAME":"Friedrichsgabekoog"}'),
(447162, 'R','{"NAME":"Oesterdeichstrich"}'),
(447163, 'R','{"NAME":"Westerdeichstrich"}'),
(447164, 'R','{"NAME":"Wesselburener Deichhausen"}'),
(447165, 'R','{"NAME":"Reinsbüttel"}'),
(447166, 'R','{"NAME":"Hedwigenkoog"}'),
(447188, 'R','{"NAME":"Altenmoor"}'),
(447189, 'R','{"NAME":"Neuendorf bei Elmshorn"}'),
(447190, 'R','{"NAME":"Kollmar"}'),
(447191, 'R','{"NAME":"Herzhorn"}'),
(447192, 'R','{"NAME":"Engelbrechtsche Wildnis"}'),
(447193, 'R','{"NAME":"Kiebitzreihe"}'),
(447194, 'R','{"NAME":"Horst","NAME:suffix":"(Holstein)"}'),
(447195, 'R','{"NAME":"Hohenfelde"}'),
(447196, 'R','{"NAME":"Sommerland"}'),
(447197, 'R','{"NAME":"Borsfleth"}'),
(447198, 'R','{"NAME":"Krempdorf"}'),
(447199, 'R','{"NAME":"Blomesche Wildnis"}'),
(447200, 'R','{"NAME":"Horst-Herzhorn","NAME:prefix":"Amt"}'),
(447206, 'R','{"NAME":"Glückstadt","NAME:prefix":"Stadt"}'),
(447255, 'R','{"NAME":"Süderau"}'),
(447312, 'R','{"NAME":"Rethwisch"}'),
(447313, 'R','{"NAME":"Neuenbrook"}'),
(447314, 'R','{"NAME":"Krempe","NAME:prefix":"Stadt"}'),
(447315, 'R','{"NAME":"Elskop"}'),
(447344, 'R','{"NAME":"Bahrenfleth"}'),
(447345, 'R','{"NAME":"Dägeling"}'),
(447346, 'R','{"NAME":"Grevenkop"}'),
(447347, 'R','{"NAME":"Krempermoor"}'),
(447348, 'R','{"NAME":"Kremperheide"}'),
(447349, 'R','{"NAME":"Krempermarsch","NAME:prefix":"Amt"}'),
(447770, 'R','{"NAME":"Wewelsfleth"}'),
(447771, 'R','{"NAME":"Brokdorf"}'),
(447772, 'R','{"NAME":"Beidenfleth"}'),
(447773, 'R','{"NAME":"Hodorf"}'),
(447962, 'R','{"NAME":"Hellschen-Heringsand-Unterschaar"}'),
(447963, 'R','{"NAME":"Strübbel"}'),
(447964, 'R','{"NAME":"Süderdeich"}'),
(447965, 'R','{"NAME":"Wesselburenerkoog"}'),
(447966, 'R','{"NAME":"Wesselburen","NAME:prefix":"Stadt"}'),
(447967, 'R','{"NAME":"Oesterwurth"}'),
(447968, 'R','{"NAME":"Hillgroven"}'),
(447969, 'R','{"NAME":"Norddeich"}'),
(447970, 'R','{"NAME":"Schülp"}'),
(448011, 'R','{"NAME":"Sankt Margarethen"}'),
(448012, 'R','{"NAME":"Büttel"}'),
(448013, 'R','{"NAME":"Kudensee"}'),
(448014, 'R','{"NAME":"Landscheide"}'),
(448015, 'R','{"NAME":"Ecklak"}'),
(448016, 'R','{"NAME":"Aebtissinwisch"}'),
(448018, 'R','{"NAME":"Neuendorf-Sachsenbande"}'),
(448019, 'R','{"NAME":"Nortorf"}'),
(448020, 'R','{"NAME":"Wilster","NAME:prefix":"Stadt"}'),
(448021, 'R','{"NAME":"Dammfleth"}'),
(448544, 'R','{"NAME":"Vaalermoor","NAME:nds":"Valermoor"}'),
(448545, 'R','{"NAME":"Krummendiek"}'),
(448556, 'R','{"NAME":"Moorhusen"}'),
(448557, 'R','{"NAME":"Kleve"}'),
(448558, 'R','{"NAME":"Nutteln","NAME:nds":"Nutteln"}'),
(448559, 'R','{"NAME":"Oldendorf"}'),
(448603, 'R','{"NAME":"Huje"}'),
(448604, 'R','{"NAME":"Mehlbek"}'),
(448605, 'R','{"NAME":"Kaaks"}'),
(448606, 'R','{"NAME":"Ottenbüttel"}'),
(448608, 'R','{"NAME":"Vaale","NAME:nds":"Vaal"}'),
(448707, 'R','{"NAME":"Wacken"}'),
(448708, 'R','{"NAME":"Heiligenstedtenerkamp"}'),
(448709, 'R','{"NAME":"Gribbohm"}'),
(448710, 'R','{"NAME":"Bekmünde"}'),
(448711, 'R','{"NAME":"Landrecht"}'),
(448712, 'R','{"NAME":"Bekdorf"}'),
(448713, 'R','{"NAME":"Stördorf"}'),
(448714, 'R','{"NAME":"Amt Wilstermarsch"}'),
(448761, 'R','{"NAME":"Holstenniendorf"}'),
(448762, 'R','{"NAME":"Besdorf"}'),
(448763, 'R','{"NAME":"Bokhorst"}'),
(448764, 'R','{"NAME":"Bokelrehm"}'),
(448765, 'R','{"NAME":"Agethorst"}'),
(448766, 'R','{"NAME":"Kaisborstel"}'),
(448767, 'R','{"NAME":"Hadenfeld"}'),
(448768, 'R','{"NAME":"Siezbüttel"}'),
(448769, 'R','{"NAME":"Nienbüttel"}'),
(448770, 'R','{"NAME":"Aasbüttel"}'),
(448771, 'R','{"NAME":"Warringholz"}'),
(448772, 'R','{"NAME":"Schenefeld"}'),
(448787, 'R','{"NAME":"Amt Schenefeld"}'),
(450062, 'R','{"NAME":"Silzen"}'),
(450063, 'R','{"NAME":"Peissen"}'),
(450064, 'R','{"NAME":"Reher"}'),
(450065, 'R','{"NAME":"Poyenberg"}'),
(450066, 'R','{"NAME":"Christinenthal"}'),
(450067, 'R','{"NAME":"Rade"}'),
(450068, 'R','{"NAME":"Hennstedt"}'),
(450069, 'R','{"NAME":"Sarlhusen"}'),
(450070, 'R','{"NAME":"Wiedenborstel"}'),
(450071, 'R','{"NAME":"Willenscharen"}'),
(450072, 'R','{"NAME":"Oldenborstel"}'),
(450073, 'R','{"NAME":"Puls"}'),
(450074, 'R','{"NAME":"Looft"}'),
(450075, 'R','{"NAME":"Pöschendorf"}'),
(450076, 'R','{"NAME":"Drage"}'),
(450214, 'R','{"NAME":"Lockstedt"}'),
(450215, 'R','{"NAME":"Oeschebüttel"}'),
(450216, 'R','{"NAME":"Hohenaspe"}'),
(450217, 'R','{"NAME":"Schlotfeld"}'),
(450218, 'R','{"NAME":"Hohenlockstedt"}'),
(450219, 'R','{"NAME":"Fitzbek"}'),
(450220, 'R','{"NAME":"Störkathen"}'),
(450221, 'R','{"NAME":"Brokstedt"}'),
(450222, 'R','{"NAME":"Quarnstedt"}'),
(450223, 'R','{"NAME":"Rosdorf"}'),
(450224, 'R','{"NAME":"Kellinghusen","NAME:prefix":"Stadt"}'),
(450225, 'R','{"NAME":"Mühlenbarbek"}'),
(450226, 'R','{"NAME":"Lohbarbek"}'),
(450227, 'R','{"NAME":"Winseldorf"}'),
(450228, 'R','{"NAME":"Amt Itzehoe-Land"}'),
(450512, 'R','{"NAME":"Breitenburg"}'),
(450513, 'R','{"NAME":"Wittenbergen"}'),
(450514, 'R','{"NAME":"Auufer"}'),
(450515, 'R','{"NAME":"Wulfsmoor"}'),
(450516, 'R','{"NAME":"Hingstheide"}'),
(450517, 'R','{"NAME":"Wrist"}'),
(450518, 'R','{"NAME":"Oelixdorf"}'),
(450519, 'R','{"NAME":"Münsterdorf"}'),
(450520, 'R','{"NAME":"Lägerdorf"}'),
(450521, 'R','{"NAME":"Kronsmoor"}'),
(450522, 'R','{"NAME":"Westermoor"}'),
(450523, 'R','{"NAME":"Breitenberg"}'),
(450524, 'R','{"NAME":"Moordiek"}'),
(450533, 'R','{"NAME":"Amt Kellinghusen"}'),
(450534, 'R','{"NAME":"Breitenburg","NAME:prefix":"Amt"}'),
(450589, 'R','{"NAME":"Westerrade"}'),
(450590, 'R','{"NAME":"Holm"}'),
(452243, 'R','{"NAME":"Heiligenstedten"}'),
(453328, 'R','{"NAME":"Kollmoor"}'),
(453723, 'R','{"NAME":"Bargteheide","NAME:nds":"Bartheil","NAME:prefix":"Stadt"}'),
(453724, 'R','{"NAME":"Jersbek"}'),
(453725, 'R','{"NAME":"Bargfeld-Stegen"}'),
(453726, 'R','{"NAME":"Nienwohld"}'),
(453727, 'R','{"NAME":"Elmenhorst"}'),
(453743, 'R','{"NAME":"Tremsbüttel"}'),
(453744, 'R','{"NAME":"Hammoor"}'),
(453745, 'R','{"NAME":"Lasbek"}'),
(453746, 'R','{"NAME":"Ahrensburg","NAME:de":"Ahrensburg","NAME:nds":"Ahrensborg","NAME:prefix":"Stadt","NAME:ru":"Аренсбург"}'),
(453747, 'R','{"NAME":"Delingsdorf"}'),
(453748, 'R','{"NAME":"Todendorf"}'),
(453749, 'R','{"NAME":"Bargteheide-Land","NAME:prefix":"Amt"}'),
(454193, 'R','{"NAME":"Siek"}'),
(454194, 'R','{"NAME":"Hoisdorf"}'),
(454195, 'R','{"NAME":"Großhansdorf","NAME:de":"Großhansdorf","NAME:nds":"Groothansdörp"}'),
(454196, 'R','{"NAME":"Grönwohld"}'),
(454197, 'R','{"NAME":"Koberg"}'),
(454198, 'R','{"NAME":"Köthel (Stormarn)"}'),
(454199, 'R','{"NAME":"Hohenfelde"}'),
(454200, 'R','{"NAME":"Lütjensee"}'),
(454243, 'R','{"NAME":"Hamfelde (Stormarn)"}'),
(454244, 'R','{"NAME":"Brunsbek"}'),
(454245, 'R','{"NAME":"Braak"}'),
(454246, 'R','{"NAME":"Stapelfeld"}'),
(454247, 'R','{"NAME":"Siek","NAME:prefix":"Amt"}'),
(454248, 'R','{"NAME":"Glinde","NAME:nds":"Glinn","NAME:prefix":"Stadt"}'),
(454249, 'R','{"NAME":"Witzhave"}'),
(454250, 'R','{"NAME":"Rausdorf"}'),
(454251, 'R','{"NAME":"Großensee"}'),
(454252, 'R','{"NAME":"Grande"}'),
(454253, 'R','{"NAME":"Trittau","NAME:prefix":"Amt"}'),
(455447, 'R','{"NAME":"Steinburg"}'),
(532316, 'R','{"NAME":"Grabau"}'),
(532317, 'R','{"NAME":"Rethwisch"}'),
(532318, 'R','{"NAME":"Wesenberg"}'),
(532319, 'R','{"NAME":"Meddewade"}'),
(532320, 'R','{"NAME":"Neritz"}'),
(532321, 'R','{"NAME":"Rümpel"}'),
(532322, 'R','{"NAME":"Travenbrück"}'),
(532323, 'R','{"NAME":"Westerau"}'),
(532324, 'R','{"NAME":"Barnitz"}'),
(532325, 'R','{"NAME":"Bad Oldesloe","NAME:nds":"Bad Oschloe","NAME:prefix":"Stadt","NAME:uk":"Бад-Ольдесло"}'),
(532326, 'R','{"NAME":"Pölitz"}'),
(532327, 'R','{"NAME":"Klein Wesenberg"}'),
(532392, 'R','{"NAME":"Zarpen"}'),
(532393, 'R','{"NAME":"Heidekamp"}'),
(532394, 'R','{"NAME":"Mönkhagen"}'),
(532395, 'R','{"NAME":"Rehhorst"}'),
(532396, 'R','{"NAME":"Feldhorst"}'),
(532397, 'R','{"NAME":"Hamberge"}'),
(532398, 'R','{"NAME":"Badendorf"}'),
(532399, 'R','{"NAME":"Bad Oldesloe-Land","NAME:prefix":"Amt"}'),
(532400, 'R','{"NAME":"Heilshoop"}'),
(532401, 'R','{"NAME":"Reinfeld","NAME:prefix":"Stadt","NAME:suffix":"(Holstein)"}'),
(532402, 'R','{"NAME":"Nordstormarn","NAME:prefix":"Amt"}'),
(548489, 'R','{"NAME":"Felm"}'),
(548490, 'R','{"NAME":"Osdorf"}'),
(548491, 'R','{"NAME":"Gettorf"}'),
(548492, 'R','{"NAME":"Tüttendorf"}'),
(548493, 'R','{"NAME":"Schinkel"}'),
(548494, 'R','{"NAME":"Lindau"}'),
(548495, 'R','{"NAME":"Neudorf-Bornstein"}'),
(548499, 'R','{"NAME":"Quarnbek"}'),
(548500, 'R','{"NAME":"Krummwisch"}'),
(548501, 'R','{"NAME":"Felde"}'),
(548502, 'R','{"NAME":"Achterwehr"}'),
(548503, 'R','{"NAME":"Bredenbek"}'),
(548504, 'R','{"NAME":"Westensee"}'),
(548507, 'R','{"NAME":"Rodenbek"}'),
(548508, 'R','{"NAME":"Schierensee"}'),
(548509, 'R','{"NAME":"Rumohr"}'),
(548510, 'R','{"NAME":"Blumenthal"}'),
(548511, 'R','{"NAME":"Böhnhusen"}'),
(548512, 'R','{"NAME":"Techelsdorf"}'),
(548515, 'R','{"NAME":"Brügge"}'),
(548516, 'R','{"NAME":"Reesdorf"}'),
(548517, 'R','{"NAME":"Schmalstede"}'),
(548518, 'R','{"NAME":"Grevenkrug"}'),
(548519, 'R','{"NAME":"Sören"}'),
(548520, 'R','{"NAME":"Hoffeld"}'),
(548546, 'R','{"NAME":"Emkendorf"}'),
(548547, 'R','{"NAME":"Groß Vollstedt"}'),
(548548, 'R','{"NAME":"Warder"}'),
(548549, 'R','{"NAME":"Langwedel"}'),
(548550, 'R','{"NAME":"Dätgen"}'),
(548551, 'R','{"NAME":"Borgdorf-Seedorf"}'),
(548552, 'R','{"NAME":"Eisendorf"}'),
(548553, 'R','{"NAME":"Ellerdorf"}'),
(548554, 'R','{"NAME":"Bokel"}'),
(548555, 'R','{"NAME":"Brammer"}'),
(548556, 'R','{"NAME":"Bargstedt"}'),
(548557, 'R','{"NAME":"Oldenhütten"}'),
(548558, 'R','{"NAME":"Nortorf","NAME:prefix":"Stadt"}'),
(548559, 'R','{"NAME":"Gnutz"}'),
(548560, 'R','{"NAME":"Timmaspe"}'),
(548563, 'R','{"NAME":"Bovenau"}'),
(548564, 'R','{"NAME":"Rade","NAME:suffix":"b. Rendsburg"}'),
(548565, 'R','{"NAME":"Schacht-Audorf"}'),
(548566, 'R','{"NAME":"Ostenfeld","NAME:suffix":"(Rendsburg)"}'),
(548567, 'R','{"NAME":"Haßmoor"}'),
(548568, 'R','{"NAME":"Schülldorf"}'),
(548569, 'R','{"NAME":"Osterrönfeld"}'),
(548570, 'R','{"NAME":"Rendsburg","NAME:da":"Rendsborg","NAME:frr":"Ransburj","NAME:nds":"Rendsborg","NAME:prefix":"Stadt","NAME:uk":"Рендсбурґ"}'),
(548571, 'R','{"NAME":"Büdelsdorf","NAME:prefix":"Stadt"}'),
(548572, 'R','{"NAME":"Rickert"}'),
(548573, 'R','{"NAME":"Alt Duvenstedt"}'),
(548574, 'R','{"NAME":"Fockbek"}'),
(548575, 'R','{"NAME":"Nübbel"}'),
(548577, 'R','{"NAME":"Westerrönfeld"}'),
(548578, 'R','{"NAME":"Schülp bei Rendsburg"}'),
(548579, 'R','{"NAME":"Jevenstedt"}'),
(548580, 'R','{"NAME":"Hörsten"}'),
(548581, 'R','{"NAME":"Hamweddel"}'),
(548582, 'R','{"NAME":"Haale"}'),
(548583, 'R','{"NAME":"Embühren"}'),
(548584, 'R','{"NAME":"Brinjahe"}'),
(548585, 'R','{"NAME":"Stafstedt"}'),
(548586, 'R','{"NAME":"Luhnstedt"}'),
(548589, 'R','{"NAME":"Heinkenborstel"}'),
(548590, 'R','{"NAME":"Nindorf"}'),
(548591, 'R','{"NAME":"Mörel"}'),
(548592, 'R','{"NAME":"Rade b. Hohenwestedt"}'),
(548593, 'R','{"NAME":"Tappendorf"}'),
(548594, 'R','{"NAME":"Remmels"}'),
(548595, 'R','{"NAME":"Hohenwestedt"}'),
(548596, 'R','{"NAME":"Nienborstel"}'),
(548597, 'R','{"NAME":"Todenbüttel"}'),
(548602, 'R','{"NAME":"Hanerau-Hademarschen"}'),
(548603, 'R','{"NAME":"Lütjenwestedt"}'),
(548604, 'R','{"NAME":"Tackesdorf"}'),
(548605, 'R','{"NAME":"Breiholz"}'),
(548606, 'R','{"NAME":"Sophienhamm"}'),
(548609, 'R','{"NAME":"Borgstedt"}'),
(548610, 'R','{"NAME":"Neu Duvenstedt"}'),
(548611, 'R','{"NAME":"Holtsee"}'),
(548612, 'R','{"NAME":"Ahlefeld-Bistensee"}'),
(548613, 'R','{"NAME":"Damendorf"}'),
(548614, 'R','{"NAME":"Ascheffel"}'),
(548615, 'R','{"NAME":"Hütten"}'),
(548616, 'R','{"NAME":"Osterby"}'),
(548620, 'R','{"NAME":"Loose"}'),
(548621, 'R','{"NAME":"Holzdorf","NAME:da":"Holttorp","NAME:de":"Holzdorf","NAME:nds":"Holzdörp"}'),
(550773, 'R','{"NAME":"Groß Wittensee"}'),
(550774, 'R','{"NAME":"Haby"}'),
(550775, 'R','{"NAME":"Bünsdorf"}'),
(550776, 'R','{"NAME":"Holzbunge"}'),
(550777, 'R','{"NAME":"Klein Wittensee"}'),
(550778, 'R','{"NAME":"Sehestedt"}'),
(552387, 'R','{"NAME":"Goosefeld"}'),
(552388, 'R','{"NAME":"Windeby"}'),
(552389, 'R','{"NAME":"Gammelby"}'),
(553468, 'R','{"NAME":"Flintbek"}'),
(553471, 'R','{"NAME":"Schönhorst"}'),
(553474, 'R','{"NAME":"Bissee"}'),
(553475, 'R','{"NAME":"Groß Buchwald"}'),
(553511, 'R','{"NAME":"Negenharrie"}'),
(553512, 'R','{"NAME":"Wattenbek"}'),
(553513, 'R','{"NAME":"Bordesholm"}'),
(553520, 'R','{"NAME":"Mühbrook"}'),
(553521, 'R','{"NAME":"Schönbek"}'),
(553522, 'R','{"NAME":"LOOP"}'),
(553542, 'R','{"NAME":"Krogaspe"}'),
(553600, 'R','{"NAME":"Padenstedt"}'),
(553601, 'R','{"NAME":"Arpsdorf"}'),
(553602, 'R','{"NAME":"Ehndorf"}'),
(554514, 'R','{"NAME":"Brodersby"}'),
(554520, 'R','{"NAME":"Karby"}'),
(554521, 'R','{"NAME":"Winnemark"}'),
(554526, 'R','{"NAME":"Dörphof"}'),
(554528, 'R','{"NAME":"Damp"}'),
(554539, 'R','{"NAME":"Waabs"}'),
(554540, 'R','{"NAME":"Barkelsby"}'),
(554562, 'R','{"NAME":"Altenhof"}'),
(554564, 'R','{"NAME":"Noer"}'),
(554637, 'R','{"NAME":"Schwedeneck"}'),
(554638, 'R','{"NAME":"Strande"}'),
(554639, 'R','{"NAME":"Dänischenhagen"}'),
(554747, 'R','{"NAME":"Altenholz"}'),
(554748, 'R','{"NAME":"Neuwittenbek"}'),
(554749, 'R','{"NAME":"Melsdorf"}'),
(554770, 'R','{"NAME":"Kronshagen"}'),
(554771, 'R','{"NAME":"Mielkendorf"}'),
(554812, 'R','{"NAME":"Ottendorf"}'),
(556088, 'R','{"NAME":"Aukrug"}'),
(556090, 'R','{"NAME":"Meezen"}'),
(556093, 'R','{"NAME":"Grauel"}'),
(556094, 'R','{"NAME":"Jahrsdorf"}'),
(556096, 'R','{"NAME":"Wapelfeld"}'),
(556097, 'R','{"NAME":"Osterstedt"}'),
(556099, 'R','{"NAME":"Beringstedt"}'),
(556100, 'R','{"NAME":"Seefeld"}'),
(556106, 'R','{"NAME":"Gokels"}'),
(556107, 'R','{"NAME":"Thaden"}'),
(556117, 'R','{"NAME":"Bendorf"}'),
(556121, 'R','{"NAME":"Bornholt"}'),
(556122, 'R','{"NAME":"Beldorf"}'),
(556139, 'R','{"NAME":"Steenfeld"}'),
(556140, 'R','{"NAME":"Oldenbüttel"}'),
(556575, 'R','{"NAME":"Prinzenmoor"}'),
(556576, 'R','{"NAME":"Hamdorf"}'),
(556602, 'R','{"NAME":"Elsdorf-Westermühlen"}'),
(556603, 'R','{"NAME":"Bargstall"}'),
(556604, 'R','{"NAME":"Friedrichsgraben"}'),
(556627, 'R','{"NAME":"Hohn"}'),
(556628, 'R','{"NAME":"Friedrichsholm"}'),
(556652, 'R','{"NAME":"Christiansholm"}'),
(556653, 'R','{"NAME":"Königshügel"}'),
(556654, 'R','{"NAME":"Lohe-Föhrden"}'),
(556667, 'R','{"NAME":"Owschlag"}'),
(557139, 'R','{"NAME":"Brekendorf"}'),
(557140, 'R','{"NAME":"Hummelfeld"}'),
(557141, 'R','{"NAME":"Thumby"}'),
(557142, 'R','{"NAME":"Rieseby","NAME:da":"Risby","NAME:nds":"Riesby"}'),
(557143, 'R','{"NAME":"Güby"}'),
(557144, 'R','{"NAME":"Fleckeby"}'),
(557145, 'R','{"NAME":"Kosel","NAME:da":"Koslev","NAME:de":"Kosel"}'),
(557582, 'R','{"NAME":"Molfsee"}'),
(569751, 'R','{"NAME":"Probstei","NAME:prefix":"Amt"}'),
(569759, 'R','{"NAME":"Schrevenborn","NAME:prefix":"Amt"}'),
(569760, 'R','{"NAME":"Selent/Schlesen","NAME:prefix":"Amt"}'),
(569761, 'R','{"NAME":"Lütjenburg","NAME:prefix":"Amt"}'),
(569763, 'R','{"NAME":"Preetz-Land","NAME:prefix":"Amt"}'),
(569767, 'R','{"NAME":"Großer Plöner See","NAME:prefix":"Amt"}'),
(569772, 'R','{"NAME":"Bokhorst-Wankendorf","NAME:prefix":"Amt"}'),
(904512, 'R','{"NAME":"Schleswig","NAME:ar":"اشلسويغ","NAME:azb":"اشلسویق","NAME:be":"Шле́звіг","NAME:ca":"Slesvig","NAME:ce":"Шлезвиг","NAME:ceb":"Schleswig","NAME:cs":"Šlesvik","NAME:da":"Slesvig","NAME:de":"Schleswig","NAME:el":"Σλέσβιχ","NAME:en":"Schleswig","NAME:eo":"Schleswig","NAME:es":"Schleswig","NAME:et":"Schleswig","NAME:eu":"Schleswig","NAME:fa":"شلسویگ","NAME:fi":"Schleswig","NAME:fr":"Schleswig","NAME:frr":"Slaswik","NAME:fy":"Sleeswyk","NAME:hu":"Sleswig","NAME:id":"Schleswig","NAME:IS":"Slésvík","NAME:it":"Schleswig","NAME:ja":"シュレースヴィヒ","NAME:ko":"슐레스비히","NAME:ku":"Schleswig","NAME:la":"Sliasvig","NAME:lld":"Schleswig","NAME:lv":"Šlēsviga","NAME:mk":"Шлезвиг","NAME:ms":"Schleswig","NAME:nds":"Sleeswig","NAME:nds-nl":"Sleeswiek","NAME:nl":"Sleeswijk","NAME:nn":"Schleswig","NAME:NO":"Schleswig","NAME:os":"Шле́звиг","NAME:pl":"Szlezwik","NAME:prefix":"Stadt","NAME:pt":"Eslésvico","NAME:ro":"Schleswig","NAME:ru":"Шле́звиг","NAME:sco":"Schleswig","NAME:sh":"Šlezvig","NAME:sq":"Sliasvig","NAME:sr":"Шлезвиг","NAME:sv":"Schleswig","NAME:sw":"Schleswig","NAME:tr":"Schleswig","NAME:tt":"Шлезвиг","NAME:uk":"Шлезвіґ","NAME:uz":"Schleswig","NAME:vi":"Schleswig","NAME:vo":"Schleswig","NAME:war":"Schleswig","NAME:zh":"石勒苏益格"}'),
(935099, 'R','{"NAME":"Marne-Nordsee","NAME:prefix":"Amt"}'),
(935133, 'R','{"NAME":"Burg-Sankt Michaelisdonn","NAME:prefix":"Amt"}'),
(935134, 'R','{"NAME":"Brunsbüttel","NAME:de":"Brunsbüttel","NAME:nds":"Bruunsbüddel","NAME:prefix":"Stadt"}'),
(935243, 'R','{"NAME":"Friedrichskoog","NAME:ru":"Фридрихског"}'),
(935256, 'R','{"NAME":"Kaiser-Wilhelm-Koog"}'),
(935261, 'R','{"NAME":"Neufelderkoog","NAME:nds":"Niefelderkoog"}'),
(935302, 'R','{"NAME":"Kronprinzenkoog"}'),
(935336, 'R','{"NAME":"Neufeld","NAME:nds":"Niefeld"}'),
(935360, 'R','{"NAME":"Ramhusen"}'),
(935386, 'R','{"NAME":"Volsemenhusen"}'),
(935396, 'R','{"NAME":"Trennewurth"}'),
(935697, 'R','{"NAME":"Helse"}'),
(935702, 'R','{"NAME":"Marnerdeich"}'),
(935724, 'R','{"NAME":"Marne","NAME:prefix":"Stadt"}'),
(935734, 'R','{"NAME":"Diekhusen-Fahrstedt"}'),
(935736, 'R','{"NAME":"Schmedeswurth"}'),
(938017, 'R','{"NAME":"Dingen"}'),
(938057, 'R','{"NAME":"Eddelak","NAME:nds":"Eddelak"}'),
(938073, 'R','{"NAME":"Averlak"}'),
(938081, 'R','{"NAME":"Sankt Michaelisdonn"}'),
(939487, 'R','{"NAME":"Frestedt"}'),
(939489, 'R','{"NAME":"Süderhastedt"}'),
(939497, 'R','{"NAME":"Eggstedt"}'),
(939498, 'R','{"NAME":"Hochdonn"}'),
(939795, 'R','{"NAME":"Großenrade"}'),
(943896, 'R','{"NAME":"Burg (Dithmarschen)"}'),
(943920, 'R','{"NAME":"Brickeln"}'),
(943923, 'R','{"NAME":"Quickborn"}'),
(943942, 'R','{"NAME":"Buchholz","NAME:nds":"Bookholt"}'),
(943943, 'R','{"NAME":"Kuden","NAME:nds":"Kuden"}'),
(946202, 'R','{"NAME":"Karolinenkoog"}'),
(949289, 'R','{"NAME":"Hemme","NAME:frr":"Heme"}'),
(949290, 'R','{"NAME":"Groven","NAME:ru":"Грофен"}'),
(949859, 'R','{"NAME":"Lehe"}'),
(949910, 'R','{"NAME":"Sankt Annen"}'),
(949911, 'R','{"NAME":"Schlichting"}'),
(952206, 'R','{"NAME":"Lunden","NAME:nds":"Lunnen"}'),
(952215, 'R','{"NAME":"Krempel"}'),
(952216, 'R','{"NAME":"Rehm-Flehde-Bargen"}'),
(952228, 'R','{"NAME":"Kleve","NAME:nds":"Kleev"}'),
(952291, 'R','{"NAME":"Hennstedt"}'),
(952358, 'R','{"NAME":"Wiemerstedt"}'),
(952359, 'R','{"NAME":"Fedderingen","NAME:nds":"Fellern"}'),
(952365, 'R','{"NAME":"Bergewöhrden"}'),
(955356, 'R','{"NAME":"Hollingstedt"}'),
(957877, 'R','{"NAME":"Wasbek"}'),
(958232, 'R','{"NAME":"Delve"}'),
(962280, 'R','{"NAME":"Aukrug"}'),
(962281, 'R','{"NAME":"Hägen"}'),
(962282, 'R','{"NAME":"Süderheistedt"}'),
(962288, 'R','{"NAME":"Norderheistedt"}'),
(962312, 'R','{"NAME":"Barkenholm"}'),
(964139, 'R','{"NAME":"Rederstall"}'),
(964190, 'R','{"NAME":"Gaushorn"}'),
(964208, 'R','{"NAME":"Welmbüttel"}'),
(964215, 'R','{"NAME":"Westerborstel"}'),
(964249, 'R','{"NAME":"Wallen"}'),
(964253, 'R','{"NAME":"Glüsing"}'),
(964256, 'R','{"NAME":"Linden"}'),
(964289, 'R','{"NAME":"Pahlen"}'),
(964294, 'R','{"NAME":"Schalkholz"}'),
(965558, 'R','{"NAME":"Hövede"}'),
(965579, 'R','{"NAME":"Dörpling"}'),
(966681, 'R','{"NAME":"Tielenhemme"}'),
(966738, 'R','{"NAME":"Dellstedt"}'),
(966749, 'R','{"NAME":"Wrohm"}'),
(966769, 'R','{"NAME":"Süderdorf"}'),
(966777, 'R','{"NAME":"Tellingstedt"}'),
(966778, 'R','{"NAME":"Tellingstedt"}'),
(968220, 'R','{"NAME":"Ravensberg"}'),
(969228, 'R','{"NAME":"Blücherplatz"}'),
(969563, 'R','{"NAME":"Schreventeich"}'),
(969718, 'R','{"NAME":"Neumühlen-Dietrichsdorf"}'),
(970762, 'R','{"NAME":"Eider","NAME:prefix":"Amt Kirchspielslandgemeinden"}'),
(975405, 'R','{"NAME":"Brunswik"}'),
(975434, 'R','{"NAME":"Düsternbrook"}'),
(1000196, 'R','{"NAME":"Wik"}'),
(1099844, 'R','{"NAME":"Groß Grönau"}'),
(1106363, 'R','{"NAME":"Tönning","NAME:prefix":"Stadt"}'),
(1107719, 'R','{"NAME":"Vollerwiek"}'),
(1107767, 'R','{"NAME":"Welt"}'),
(1141531, 'R','{"NAME":"Wenningstedt-Braderup (Sylt)","NAME:frr":"Woningstair-Brēderep"}'),
(1141532, 'R','{"NAME":"Hörnum (Sylt)","NAME:frr":"Hörnem"}'),
(1141533, 'R','{"NAME":"LIST auf Sylt","NAME:frr":"LIST"}'),
(1145084, 'R','{"NAME":"Quern","NAME:da":"Kværn"}'),
(1145085, 'R','{"NAME":"Gelting"}'),
(1145086, 'R','{"NAME":"Ahneby"}'),
(1145087, 'R','{"NAME":"Steinberg"}'),
(1145088, 'R','{"NAME":"Kronsgaard"}'),
(1145089, 'R','{"NAME":"Stangheck"}'),
(1145090, 'R','{"NAME":"Steinbergkirche"}'),
(1145091, 'R','{"NAME":"Niesgrau"}'),
(1145092, 'R','{"NAME":"Esgrus"}'),
(1145093, 'R','{"NAME":"Rabenholz"}'),
(1145094, 'R','{"NAME":"Pommerby"}'),
(1145095, 'R','{"NAME":"Nieby","NAME:da":"Nyby"}'),
(1145096, 'R','{"NAME":"Sterup"}'),
(1145348, 'R','{"NAME":"Hasselberg"}'),
(1145349, 'R','{"NAME":"Maasholm"}'),
(1145350, 'R','{"NAME":"Kappeln","NAME:ar":"كابلن","NAME:azb":"کاپلن","NAME:ce":"Каппельн","NAME:ceb":"Kappeln","NAME:da":"Kappel","NAME:de":"Kappeln","NAME:en":"Kappeln","NAME:eo":"Kappeln","NAME:es":"Kappeln","NAME:eu":"Kappeln","NAME:fa":"کاپلن","NAME:fi":"Kappeln","NAME:fr":"Kappeln","NAME:frr":"Kappeln","NAME:fy":"Kappeln","NAME:hu":"Kappeln","NAME:it":"Kappeln","NAME:ja":"カッペルン","NAME:kk":"Каппельн","NAME:ku":"Kappeln","NAME:ky":"Каппельн","NAME:mk":"Капелн","NAME:ms":"Kappeln","NAME:nl":"Kappeln","NAME:pl":"Kappeln","NAME:prefix":"Stadt","NAME:pt":"Kappeln","NAME:ro":"Kappeln","NAME:ru":"Каппельн","NAME:sco":"Kappeln","NAME:sh":"Kapeln","NAME:sr":"Капелн","NAME:sv":"Kappeln","NAME:sw":"Kappeln","NAME:tr":"Kappeln","NAME:tt":"Каппельн","NAME:tum":"Kappeln","NAME:uk":"Каппельн","NAME:uz":"Kappeln","NAME:vi":"Kappeln","NAME:war":"Kappeln","NAME:zh":"卡珀尔恩"}'),
(1145351, 'R','{"NAME":"Rabel"}'),
(1145352, 'R','{"NAME":"Stoltebüll"}'),
(1145353, 'R','{"NAME":"Arnis","NAME:prefix":"Stadt"}'),
(1147133, 'R','{"NAME":"Kampen (Sylt)","NAME:frr":"Kaamp"}'),
(1147134, 'R','{"NAME":"Sylt","NAME:frr":"Söl"}'),
(1147203, 'R','{"NAME":"Oersberg"}'),
(1147204, 'R','{"NAME":"Rabenkirchen-Faulück"}'),
(1147205, 'R','{"NAME":"Grödersby"}'),
(1149227, 'R','{"NAME":"Nottfeld"}'),
(1149228, 'R','{"NAME":"Loit"}'),
(1149229, 'R','{"NAME":"Ulsnis"}'),
(1149230, 'R','{"NAME":"Rügge","NAME:da":"Rygge"}'),
(1149231, 'R','{"NAME":"Norderbrarup"}'),
(1149232, 'R','{"NAME":"Böel"}'),
(1149233, 'R','{"NAME":"Scheggerott"}'),
(1149234, 'R','{"NAME":"Boren"}'),
(1149235, 'R','{"NAME":"Dollrottfeld"}'),
(1149236, 'R','{"NAME":"Kiesby"}'),
(1149237, 'R','{"NAME":"Mohrkirch","NAME:da":"Mårkær"}'),
(1149238, 'R','{"NAME":"Wagersrott"}'),
(1149239, 'R','{"NAME":"Brebel","NAME:da":"Bredbøl"}'),
(1149240, 'R','{"NAME":"Süderbrarup","NAME:da":"Sønder Brarup"}'),
(1149241, 'R','{"NAME":"Saustrup"}'),
(1149242, 'R','{"NAME":"Steinfeld","NAME:da":"Stenfelt"}'),
(1149245, 'R','{"NAME":"Schnarup-Thumby","NAME:da":"Snarup-Tumby"}'),
(1149246, 'R','{"NAME":"Sörup","NAME:da":"Sørup","NAME:de":"Sörup"}'),
(1149247, 'R','{"NAME":"Havetoftloit"}'),
(1149248, 'R','{"NAME":"Rüde"}'),
(1149274, 'R','{"NAME":"Westerholz"}'),
(1149275, 'R','{"NAME":"Maasbüll","NAME:da":"Masbøl","NAME:de":"Maasbüll"}'),
(1149276, 'R','{"NAME":"Großsolt"}'),
(1149277, 'R','{"NAME":"Hürup","NAME:da":"Hyrup","NAME:de":"Hürup"}'),
(1149278, 'R','{"NAME":"Dollerup"}'),
(1149279, 'R','{"NAME":"Freienwill"}'),
(1149280, 'R','{"NAME":"Munkbrarup"}'),
(1149281, 'R','{"NAME":"Grundhof"}'),
(1149282, 'R','{"NAME":"Ringsberg"}'),
(1149283, 'R','{"NAME":"Wees"}'),
(1149284, 'R','{"NAME":"Ausacker","NAME:da":"Oksager"}'),
(1149285, 'R','{"NAME":"Husby"}'),
(1149286, 'R','{"NAME":"Langballig"}'),
(1149287, 'R','{"NAME":"Tastrup","NAME:da":"Tostrup","NAME:de":"Tastrup"}'),
(1149288, 'R','{"NAME":"Glücksburg","NAME:da":"Lyksborg","NAME:de":"Glücksburg (Ostsee)","NAME:frr":"Loksborj","NAME:nds":"Glücksborg","NAME:prefix":"Stadt","NAME:suffix":"(Ostsee)"}'),
(1149293, 'R','{"NAME":"Oeversee"}'),
(1149294, 'R','{"NAME":"Sieverstedt","NAME:da":"Siversted","NAME:de":"Sieverstedt"}'),
(1149295, 'R','{"NAME":"Tarp"}'),
(1149296, 'R','{"NAME":"Harrislee","NAME:da":"Harreslev"}'),
(1149297, 'R','{"NAME":"Handewitt","NAME:da":"Hanved","NAME:de":"Handewitt","NAME:frr":"Hanewit"}'),
(1149333, 'R','{"NAME":"Taarstedt"}'),
(1149334, 'R','{"NAME":"Schaalby"}'),
(1149335, 'R','{"NAME":"Stolk","NAME:da":"Stollik"}'),
(1149336, 'R','{"NAME":"Klappholz"}'),
(1149337, 'R','{"NAME":"Süderfahrenstedt","NAME:da":"Sønder Farensted"}'),
(1149338, 'R','{"NAME":"Brodersby"}'),
(1149339, 'R','{"NAME":"Goltoft"}'),
(1149340, 'R','{"NAME":"Twedt"}'),
(1149341, 'R','{"NAME":"Havetoft"}'),
(1149342, 'R','{"NAME":"Uelsby"}'),
(1149343, 'R','{"NAME":"Struxdorf","NAME:da":"Strukstrup"}'),
(1149344, 'R','{"NAME":"Tolk"}'),
(1149458, 'R','{"NAME":"Neuberend"}'),
(1149459, 'R','{"NAME":"Nübel"}'),
(1149460, 'R','{"NAME":"Idstedt"}'),
(1156023, 'R','{"NAME":"Medelby"}'),
(1156024, 'R','{"NAME":"Hörup"}'),
(1156026, 'R','{"NAME":"Weesby"}'),
(1156027, 'R','{"NAME":"Schafflund"}'),
(1156028, 'R','{"NAME":"Osterby"}'),
(1156029, 'R','{"NAME":"Meyn"}'),
(1156030, 'R','{"NAME":"Jardelund"}'),
(1156031, 'R','{"NAME":"Lindewitt"}'),
(1156032, 'R','{"NAME":"Nordhackstedt"}'),
(1156033, 'R','{"NAME":"Böxlund"}'),
(1156034, 'R','{"NAME":"Holt"}'),
(1156035, 'R','{"NAME":"Wallsbüll"}'),
(1156036, 'R','{"NAME":"Großenwiehe"}'),
(1156124, 'R','{"NAME":"Jerrishoe"}'),
(1156125, 'R','{"NAME":"Süderhackstedt"}'),
(1156126, 'R','{"NAME":"Wanderup"}'),
(1156127, 'R','{"NAME":"Janneby"}'),
(1156128, 'R','{"NAME":"Langstedt"}'),
(1156129, 'R','{"NAME":"Eggebek"}'),
(1156130, 'R','{"NAME":"Sollerup"}'),
(1156131, 'R','{"NAME":"Jörl"}'),
(1156149, 'R','{"NAME":"Bollingstedt"}'),
(1156150, 'R','{"NAME":"Schafflund","NAME:prefix":"Amt"}'),
(1156151, 'R','{"NAME":"Treia"}'),
(1156152, 'R','{"NAME":"Hüsby"}'),
(1156153, 'R','{"NAME":"Eggebek","NAME:prefix":"Amt"}'),
(1156154, 'R','{"NAME":"Ellingstedt"}'),
(1156155, 'R','{"NAME":"Hürup","NAME:prefix":"Amt"}'),
(1156156, 'R','{"NAME":"Oeversee","NAME:prefix":"Amt"}'),
(1156157, 'R','{"NAME":"Hollingstedt"}'),
(1156158, 'R','{"NAME":"Silberstedt"}'),
(1156159, 'R','{"NAME":"Langballig","NAME:prefix":"Amt"}'),
(1156160, 'R','{"NAME":"Arensharde","NAME:prefix":"Amt"}'),
(1156161, 'R','{"NAME":"Lürschau"}'),
(1156162, 'R','{"NAME":"Schuby"}'),
(1156163, 'R','{"NAME":"Jübek"}'),
(1157529, 'R','{"NAME":"Lottorf"}'),
(1157530, 'R','{"NAME":"Geltorf"}'),
(1157531, 'R','{"NAME":"Selk"}'),
(1157532, 'R','{"NAME":"Busdorf"}'),
(1157533, 'R','{"NAME":"Jagel"}'),
(1157534, 'R','{"NAME":"Fahrdorf"}'),
(1157535, 'R','{"NAME":"Borgwedel"}'),
(1157536, 'R','{"NAME":"Dannewerk"}'),
(1157539, 'R','{"NAME":"Groß Rheide"}'),
(1157540, 'R','{"NAME":"Haddeby","NAME:prefix":"Amt"}'),
(1157541, 'R','{"NAME":"Klein Rheide"}'),
(1157542, 'R','{"NAME":"Alt Bennebek"}'),
(1157656, 'R','{"NAME":"Dörpstedt"}'),
(1157657, 'R','{"NAME":"Wohlde"}'),
(1157658, 'R','{"NAME":"Börm"}'),
(1157659, 'R','{"NAME":"Klein Bennebek"}'),
(1157799, 'R','{"NAME":"Kropp-Stapelholm","NAME:prefix":"Amt"}'),
(1157800, 'R','{"NAME":"Bergenhusen"}'),
(1157801, 'R','{"NAME":"Tetenhusen"}'),
(1157802, 'R','{"NAME":"Meggerdorf"}'),
(1157803, 'R','{"NAME":"Süderstapel"}'),
(1157804, 'R','{"NAME":"Erfde"}'),
(1157805, 'R','{"NAME":"Norderstapel","NAME:da":"Nørre Stabel","NAME:de":"Norderstapel"}'),
(1157806, 'R','{"NAME":"Tielen"}'),
(1157846, 'R','{"NAME":"Geltinger Bucht","NAME:prefix":"Amt"}'),
(1157847, 'R','{"NAME":"Kappeln-Land","NAME:prefix":"Amt"}'),
(1157848, 'R','{"NAME":"Süderbrarup","NAME:prefix":"Amt"}'),
(1157849, 'R','{"NAME":"Südangeln","NAME:prefix":"Amt"}'),
(1157850, 'R','{"NAME":"Mittelangeln","NAME:prefix":"Amt"}'),
(1157962, 'R','{"NAME":"Helgoland","NAME:de":"Helgoland","NAME:frr":"deät Lun"}'),
(1175544, 'R','{"NAME":"Böklund","NAME:da":"Bøglund","NAME:de":"Böklund"}'),
(1185946, 'R','{"NAME":"Ekenis"}'),
(1185964, 'R','{"NAME":"Satrup"}'),
(1187305, 'R','{"NAME":"Schülp bei Nortorf"}'),
(1187306, 'R','{"NAME":"Kropp","NAME:da":"Krop"}'),
(1319978, 'R','{"NAME":"Region Syddanmark","NAME:bg":"Южна Дания","NAME:br":"Danmark ar Su","NAME:ca":"Dinamarca Meridional","NAME:ce":"Къилбера Дани","NAME:cs":"Syddanmark","NAME:da":"Region Syddanmark","NAME:de":"Region Süddänemark","NAME:en":"Region OF Southern Denmark","NAME:eo":"Regiono Suda Danio","NAME:es":"Dinamarca Meridional","NAME:et":"Lõuna-Taani piirkond","NAME:eu":"Hegoaldeko Danimarka","NAME:fa":"استان سیددانمارک","NAME:fi":"Etelä-Tanskan alue","NAME:fr":"Danemark-du-Sud","NAME:frr":"Regiuun Syddanmark","NAME:fy":"Súd-Denemark","NAME:hr":"Južna Danska","NAME:hu":"Dél-Dánia régió","NAME:hy":"Հարավային Դանիա տարածաշրջան","NAME:it":"Danimarca meridionale","NAME:ja":"南デンマーク地域","NAME:ka":"სამხრეთ დანიის რეგიონი","NAME:kk":"Оңтүстік Дания","NAME:ko":"남덴마크 지역","NAME:la":"Dania Meridiana","NAME:lt":"Pietų Danijos regionas","NAME:lv":"Dienviddānijas reģions","NAME:mk":"Јужна Данска","NAME:ms":"Wilayah Syddanmark","NAME:nds":"Region Süüddäänmark","NAME:nl":"Zuid-Denemarken","NAME:oc":"Danemarc Meridional","NAME:os":"Хуссар Дани","NAME:pl":"Dania Południowa","NAME:pt":"Dinamarca DO Sul","NAME:ro":"Regiunea Syddanmark","NAME:ru":"Южная Дания","NAME:sco":"Region o Soothren Denmark","NAME:se":"Syddanmark regiuvdna","NAME:sk":"Južné Dánsko","NAME:sr":"Јужна Данска","NAME:uk":"Південна Данія","NAME:vi":"Nam Đan Mạch","NAME:zh":"南丹麦大区"}'),
(1388559, 'R','{"NAME":"Artlenburg","NAME:nds":"Addelborg","NAME:prefix":"Flecken"}'),
(1395342, 'R','{"NAME":"Tating"}'),
(1395343, 'R','{"NAME":"Grothusenkoog","NAME:ru":"Гротузенког"}'),
(1395344, 'R','{"NAME":"Tümlauer Koog"}'),
(1395345, 'R','{"NAME":"Sankt Peter-Ording"}'),
(1395346, 'R','{"NAME":"Westerhever"}'),
(1395415, 'R','{"NAME":"Poppenbüll"}'),
(1395416, 'R','{"NAME":"Osterhever"}'),
(1397061, 'R','{"NAME":"Oldenswort"}'),
(1397062, 'R','{"NAME":"Norderfriedrichskoog"}'),
(1397063, 'R','{"NAME":"Tetenbüll"}'),
(1402693, 'R','{"NAME":"Kotzenbüll","NAME:ru":"Котценбюлль"}'),
(1402694, 'R','{"NAME":"Kirchspiel Garding"}'),
(1402695, 'R','{"NAME":"Katharinenheerd"}'),
(1402696, 'R','{"NAME":"Garding","NAME:frr":"Gaarding","NAME:prefix":"Stadt"}'),
(1402820, 'R','{"NAME":"Simonsberg"}'),
(1402821, 'R','{"NAME":"Uelvesbüll"}'),
(1402987, 'R','{"NAME":"Friedrichstadt","NAME:da":"Frederiksstad","NAME:frr":"Freedaistää","NAME:nl":"Frederikstad aan de Eider","NAME:prefix":"Stadt","NAME:ru":"Фридрихштадт (Германия)"}'),
(1402988, 'R','{"NAME":"Drage"}'),
(1402989, 'R','{"NAME":"Seeth"}'),
(1402990, 'R','{"NAME":"Witzwort"}'),
(1403981, 'R','{"NAME":"Süderhöft"}'),
(1403982, 'R','{"NAME":"Hude"}'),
(1403983, 'R','{"NAME":"Fresendelf"}'),
(1404024, 'R','{"NAME":"Schwabstedt"}'),
(1404153, 'R','{"NAME":"Winnert"}'),
(1404154, 'R','{"NAME":"Ostenfeld (Husum)"}'),
(1404155, 'R','{"NAME":"Wittbek"}'),
(1405166, 'R','{"NAME":"Ramstedt"}'),
(1405167, 'R','{"NAME":"Oldersbek"}'),
(1405168, 'R','{"NAME":"Wisch"}'),
(1405169, 'R','{"NAME":"Koldenbüttel","NAME:da":"Koldenbyttel","NAME:frr":"Koolnbütel","NAME:nds":"Kombüddel"}'),
(1405170, 'R','{"NAME":"Südermarsch"}'),
(1405171, 'R','{"NAME":"Rantrum"}'),
(1405172, 'R','{"NAME":"Mildstedt"}'),
(1405430, 'R','{"NAME":"Hattstedt"}'),
(1405431, 'R','{"NAME":"Wobbenbüll"}'),
(1405432, 'R','{"NAME":"Husum","NAME:an":"Husum","NAME:ar":"هوسوم","NAME:arz":"هوسوم","NAME:be":"Хузум","NAME:bg":"Хузум","NAME:bs":"Husum","NAME:ca":"Husum","NAME:ce":"Хузум","NAME:ceb":"Husum","NAME:cs":"Husum","NAME:da":"Husum","NAME:de":"Husum","NAME:en":"Husum","NAME:eo":"Husum","NAME:es":"Husum","NAME:eu":"Husum","NAME:fa":"هوسوم","NAME:fi":"Husum","NAME:fr":"Husum","NAME:frr":"Hüsem","NAME:fy":"Hüsem","NAME:glk":"هۊسۊم","NAME:hu":"Husum","NAME:it":"Husum","NAME:kk":"Хузум","NAME:ku":"Husum","NAME:ky":"Хузум","NAME:lt":"Huzumas","NAME:mk":"Хузум","NAME:ms":"Husum","NAME:nds":"Husum","NAME:nl":"Husum","NAME:nn":"Husum","NAME:NO":"Husum","NAME:os":"Хузум","NAME:pl":"Husum","NAME:prefix":"Stadt","NAME:pt":"Husum","NAME:ro":"Husum","NAME:ru":"Хузум","NAME:sh":"Husum","NAME:sr":"Хусум","NAME:sv":"Husum","NAME:sw":"Husum","NAME:tr":"Husum","NAME:tt":"Хузум","NAME:tum":"Husum","NAME:uk":"Гузум","NAME:uz":"Husum","NAME:vi":"Husum","NAME:vo":"Husum","NAME:war":"Husum","NAME:zh":"胡苏姆"}'),
(1405506, 'R','{"NAME":"Arlewatt"}'),
(1405507, 'R','{"NAME":"Bohmstedt"}'),
(1405508, 'R','{"NAME":"Horstedt"}'),
(1405509, 'R','{"NAME":"Elisabeth-Sophien-Koog","NAME:frr":"Eliisabeth-Sofiien-Kuuch"}'),
(1406816, 'R','{"NAME":"Ahrenshöft","NAME:frr":"Oornshaud"}'),
(1406817, 'R','{"NAME":"Olderup"}'),
(1406818, 'R','{"NAME":"Wester-Ohrstedt"}'),
(1406819, 'R','{"NAME":"Schwesing"}'),
(1406825, 'R','{"NAME":"Oster-Ohrstedt"}'),
(1406976, 'R','{"NAME":"Immenstedt"}'),
(1406977, 'R','{"NAME":"Ahrenviölfeld"}'),
(1406978, 'R','{"NAME":"Viöl"}'),
(1406979, 'R','{"NAME":"Ahrenviöl"}'),
(1406980, 'R','{"NAME":"Behrendorf"}'),
(1406981, 'R','{"NAME":"Bondelum","NAME:frr":"Bonlem"}'),
(1415323, 'R','{"NAME":"Sollwitt"}'),
(1415324, 'R','{"NAME":"Löwenstedt"}'),
(1415325, 'R','{"NAME":"Haselund"}'),
(1415326, 'R','{"NAME":"Norstedt"}'),
(1415598, 'R','{"NAME":"Kolkerheide"}'),
(1415599, 'R','{"NAME":"Struckum"}'),
(1415601, 'R','{"NAME":"Hattstedtermarsch","NAME:frr":"Haatstinger Määrsch"}'),
(1415602, 'R','{"NAME":"Drelsdorf"}'),
(1415663, 'R','{"NAME":"Goldelund"}'),
(1415664, 'R','{"NAME":"Lütjenholm"}'),
(1415665, 'R','{"NAME":"Goldebek"}'),
(1415666, 'R','{"NAME":"Joldelund"}'),
(1416728, 'R','{"NAME":"Bordelum"}'),
(1416729, 'R','{"NAME":"Sönnebüll"}'),
(1416730, 'R','{"NAME":"Vollstedt"}'),
(1416732, 'R','{"NAME":"Bredstedt","NAME:de":"Bredstedt","NAME:frr":"Bräist","NAME:prefix":"Stadt"}'),
(1416733, 'R','{"NAME":"Högel"}'),
(1416813, 'R','{"NAME":"Langenhorn","NAME:da":"Langhorn","NAME:frr":"e Hoorne"}'),
(1416814, 'R','{"NAME":"Bargum"}'),
(1416815, 'R','{"NAME":"Reußenköge"}'),
(1416816, 'R','{"NAME":"Ockholm","NAME:da":"Okholm","NAME:de":"Ockholm","NAME:frr":"e Hoolme"}'),
(1416962, 'R','{"NAME":"Enge-Sande"}'),
(1416963, 'R','{"NAME":"Stadum"}'),
(1416964, 'R','{"NAME":"Sprakebüll"}'),
(1416965, 'R','{"NAME":"Stedesand"}'),
(1417210, 'R','{"NAME":"Risum-Lindholm"}'),
(1417211, 'R','{"NAME":"Lexgaard"}'),
(1417212, 'R','{"NAME":"Ladelund"}'),
(1417213, 'R','{"NAME":"Leck"}'),
(1417214, 'R','{"NAME":"Achtrup","NAME:frr":"Åktoorp"}'),
(1417215, 'R','{"NAME":"Bramstedtlund","NAME:frr":"Braamstäälönj"}'),
(1417216, 'R','{"NAME":"Tinningstedt"}'),
(1418483, 'R','{"NAME":"Ellhöft","NAME:frr":"Älhood"}'),
(1418484, 'R','{"NAME":"Karlum"}'),
(1418485, 'R','{"NAME":"Süderlügum"}'),
(1418486, 'R','{"NAME":"Westre"}'),
(1418654, 'R','{"NAME":"Aventoft","NAME:frr":"Oowentoft"}'),
(1418655, 'R','{"NAME":"Rodenäs","NAME:da":"Rødenæs"}'),
(1418656, 'R','{"NAME":"Klanxbüll","NAME:da":"Klangsbøl","NAME:de":"Klanxbüll","NAME:frr":"Klangsbel"}'),
(1418657, 'R','{"NAME":"Friedrich-Wilhelm-Lübke-Koog","NAME:frr":"Friedrich-Wilhelm-Lübke-Kuuch"}'),
(1418942, 'R','{"NAME":"Humptrup","NAME:frr":"Humptoorp"}'),
(1418943, 'R','{"NAME":"Neukirchen","NAME:da":"Nykirke","NAME:de":"Neukirchen","NAME:frr":"Naischöspel"}'),
(1420391, 'R','{"NAME":"Uphusum"}'),
(1420392, 'R','{"NAME":"Bosbüll","NAME:frr":"Bousbel"}'),
(1420393, 'R','{"NAME":"Klixbüll"}'),
(1420394, 'R','{"NAME":"Holm"}'),
(1420395, 'R','{"NAME":"Braderup"}'),
(1420450, 'R','{"NAME":"Dagebüll","NAME:da":"Dagebøl","NAME:de":"Dagebüll","NAME:frr":"Doogebel"}'),
(1420451, 'R','{"NAME":"Galmsbüll","NAME:frr":"Galmsbel"}'),
(1420452, 'R','{"NAME":"Emmelsbüll-Horsbüll","NAME:frr":"Ämesbel"}'),
(1420553, 'R','{"NAME":"Langeneß","NAME:frr":"de Nees"}'),
(1420554, 'R','{"NAME":"Hallig Hooge"}'),
(1420555, 'R','{"NAME":"Nordstrand"}'),
(1420556, 'R','{"NAME":"Pellworm","NAME:frr":"Pelweerm"}'),
(1420557, 'R','{"NAME":"Gröde","NAME:frr":"de Grööe"}'),
(1428482, 'R','{"NAME":"Niebüll","NAME:ar":"نيبول","NAME:azb":"نیبول","NAME:ca":"Niebüll","NAME:ce":"Нибуьлль","NAME:ceb":"Niebüll","NAME:da":"Nibøl","NAME:de":"Niebüll","NAME:el":"Νίμπουλ","NAME:en":"Niebüll","NAME:eo":"Niebüll","NAME:es":"Niebüll","NAME:eu":"Niebüll","NAME:fa":"نیبول","NAME:fr":"Niebüll","NAME:frr":"Naibel","NAME:fy":"Niebüll","NAME:hu":"Niebüll","NAME:it":"Niebüll","NAME:kk":"Нибюлль","NAME:ku":"Niebüll","NAME:ky":"Нибюлль","NAME:lld":"Niebüll","NAME:mk":"Нибил","NAME:ms":"Niebüll","NAME:nl":"Niebüll","NAME:nn":"Niebüll","NAME:NO":"Niebüll","NAME:pl":"Niebüll","NAME:prefix":"Stadt","NAME:pt":"Niebüll","NAME:ro":"Niebüll","NAME:ru":"Нибюлль","NAME:sh":"Nibil","NAME:sr":"Нибил","NAME:sv":"Niebüll","NAME:tr":"Niebüll","NAME:tt":"Нибюлль","NAME:tum":"Niebüll","NAME:uk":"Нібюль","NAME:uz":"Niebüll","NAME:vi":"Niebüll","NAME:war":"Niebüll","NAME:zh":"尼比尔"}'),
(1428584, 'R','{"NAME":"Dunsum","NAME:frr":"Dunsem"}'),
(1428585, 'R','{"NAME":"Wittdün auf Amrum"}'),
(1428586, 'R','{"NAME":"Midlum","NAME:frr":"Madlem"}'),
(1428587, 'R','{"NAME":"Süderende","NAME:frr":"Söleraanj"}'),
(1428588, 'R','{"NAME":"Alkersum","NAME:frr":"Aalkersem"}'),
(1428589, 'R','{"NAME":"Föhr-Amrum","NAME:prefix":"Amt"}'),
(1428590, 'R','{"NAME":"Wyk auf Föhr","NAME:frr":"A Wik","NAME:prefix":"Stadt"}'),
(1428591, 'R','{"NAME":"Nieblum","NAME:frr":"Njiblem"}'),
(1428592, 'R','{"NAME":"Norddorf auf Amrum","NAME:de":"Norddorf auf Amrum","NAME:frr":"Noorsaarep üüb Oomram"}'),
(1428593, 'R','{"NAME":"Wrixum","NAME:frr":"Wraksem"}'),
(1428594, 'R','{"NAME":"Borgsum","NAME:frr":"Borigsem"}'),
(1428595, 'R','{"NAME":"Oevenum","NAME:frr":"Ööwenem"}'),
(1428596, 'R','{"NAME":"Utersum","NAME:frr":"Ödersem"}'),
(1428597, 'R','{"NAME":"Nebel","NAME:de":"Nebel","NAME:frr":"Neebel"}'),
(1428598, 'R','{"NAME":"Witsum","NAME:frr":"Wiisem"}'),
(1428599, 'R','{"NAME":"Oldsum","NAME:frr":"Olersem"}'),
(1428696, 'R','{"NAME":"Landschaft Sylt"}'),
(1444121, 'R','{"NAME":"Flintbek","NAME:prefix":"Amt"}'),
(1444880, 'R','{"NAME":"Schlagsdorf"}'),
(1444883, 'R','{"NAME":"Dechow"}'),
(1444890, 'R','{"NAME":"Kneese"}'),
(1444894, 'R','{"NAME":"Gadebusch","NAME:prefix":"Amt"}'),
(1444907, 'R','{"NAME":"Roggendorf"}'),
(1444915, 'R','{"NAME":"Utecht"}'),
(1444916, 'R','{"NAME":"Rehna","NAME:prefix":"Amt"}'),
(1445657, 'R','{"NAME":"Amt Nortorfer Land"}'),
(1445659, 'R','{"NAME":"Hüttener Berge","NAME:prefix":"Amt"}'),
(1445660, 'R','{"NAME":"Bordesholm","NAME:prefix":"Amt"}'),
(1445661, 'R','{"NAME":"Molfsee","NAME:prefix":"Amt"}'),
(1445662, 'R','{"NAME":"Amt Achterwehr"}'),
(1445742, 'R','{"NAME":"Dänischer Wohld","NAME:prefix":"Amt"}'),
(1445743, 'R','{"NAME":"Amt Jevenstedt"}'),
(1445744, 'R','{"NAME":"Schlei-Ostsee","NAME:prefix":"Amt"}'),
(1445745, 'R','{"NAME":"Fockbek","NAME:prefix":"Amt"}'),
(1445746, 'R','{"NAME":"Dänischenhagen","NAME:prefix":"Amt"}'),
(1445747, 'R','{"NAME":"Hohner Harde","NAME:prefix":"Amt"}'),
(1451516, 'R','{"NAME":"Zarrentin","NAME:prefix":"Amt"}'),
(1451524, 'R','{"NAME":"Lüttow-Valluhn"}'),
(1451534, 'R','{"NAME":"Boizenburg-Land","NAME:prefix":"Amt"}'),
(1451542, 'R','{"NAME":"Greven"}'),
(1451545, 'R','{"NAME":"Zarrentin am Schaalsee","NAME:prefix":"Stadt"}'),
(1451561, 'R','{"NAME":"Nostorf"}'),
(1451586, 'R','{"NAME":"Schwanheide"}'),
(1451600, 'R','{"NAME":"Gallin"}'),
(1451605, 'R','{"NAME":"Gresse"}'),
(1463073, 'R','{"NAME":"Siebenbäumen"}'),
(1463074, 'R','{"NAME":"Schürensöhlen"}'),
(1463075, 'R','{"NAME":"Krummesse"}'),
(1463076, 'R','{"NAME":"Grinau"}'),
(1463077, 'R','{"NAME":"Bliestorf"}'),
(1463078, 'R','{"NAME":"Groß Schenkenberg"}'),
(1463289, 'R','{"NAME":"Groß Boden"}'),
(1463290, 'R','{"NAME":"Steinhorst"}'),
(1463291, 'R','{"NAME":"Lüchow"}'),
(1463292, 'R','{"NAME":"Stubben"}'),
(1463293, 'R','{"NAME":"Sierksrade"}'),
(1463294, 'R','{"NAME":"Düchelsdorf"}'),
(1463295, 'R','{"NAME":"Klinkrade"}'),
(1463296, 'R','{"NAME":"Kastorf"}'),
(1464769, 'R','{"NAME":"Schiphorst"}'),
(1464771, 'R','{"NAME":"Schönberg"}'),
(1464774, 'R','{"NAME":"Sandesneben"}'),
(1464775, 'R','{"NAME":"Wentorf (Amt Sandesneben)"}'),
(1464848, 'R','{"NAME":"Kühsen"}'),
(1464849, 'R','{"NAME":"Niendorf bei Berkenthin"}'),
(1464851, 'R','{"NAME":"Göldenitz"}'),
(1465815, 'R','{"NAME":"Duvensee"}'),
(1465816, 'R','{"NAME":"Linau"}'),
(1465817, 'R','{"NAME":"Walksfelde"}'),
(1465972, 'R','{"NAME":"Ritzerau"}'),
(1465973, 'R','{"NAME":"Niendorf/Stecknitz"}'),
(1465974, 'R','{"NAME":"Panten"}'),
(1465975, 'R','{"NAME":"Köthel (Lauenburg)"}'),
(1465976, 'R','{"NAME":"Poggensee"}'),
(1465977, 'R','{"NAME":"Breitenfelde"}'),
(1465978, 'R','{"NAME":"Nusse"}'),
(1465979, 'R','{"NAME":"Bälau"}'),
(1466067, 'R','{"NAME":"Schretstaken"}'),
(1466068, 'R','{"NAME":"Kuddewörde"}'),
(1466069, 'R','{"NAME":"Mühlenrade"}'),
(1466070, 'R','{"NAME":"Kasseburg"}'),
(1466071, 'R','{"NAME":"Dahmker"}'),
(1466072, 'R','{"NAME":"Hamfelde (Lauenburg)"}'),
(1466109, 'R','{"NAME":"Möhnsen"}'),
(1466110, 'R','{"NAME":"Fuhlenhagen"}'),
(1466111, 'R','{"NAME":"Talkau"}'),
(1466112, 'R','{"NAME":"Havekost"}'),
(1466113, 'R','{"NAME":"Basthorst"}'),
(1466177, 'R','{"NAME":"Woltersdorf"}'),
(1466178, 'R','{"NAME":"Hornbek"}'),
(1466179, 'R','{"NAME":"Güster"}'),
(1466180, 'R','{"NAME":"Tramm"}'),
(1467345, 'R','{"NAME":"Kankelau"}'),
(1467346, 'R','{"NAME":"Alt Mölln"}'),
(1467347, 'R','{"NAME":"Grove"}'),
(1467348, 'R','{"NAME":"Elmenhorst"}'),
(1467349, 'R','{"NAME":"Roseburg"}'),
(1467511, 'R','{"NAME":"Siebeneichen"}'),
(1467512, 'R','{"NAME":"Klein Pampau"}'),
(1467513, 'R','{"NAME":"Sahms"}'),
(1467514, 'R','{"NAME":"Groß Pampau"}'),
(1467715, 'R','{"NAME":"Schulendorf"}'),
(1467718, 'R','{"NAME":"Wangelau"}'),
(1467719, 'R','{"NAME":"Müssen"}'),
(1467752, 'R','{"NAME":"Dalldorf"}'),
(1467753, 'R','{"NAME":"Witzeeze"}'),
(1467754, 'R','{"NAME":"Basedow"}'),
(1467755, 'R','{"NAME":"Lütau"}'),
(1469077, 'R','{"NAME":"Gülzow"}'),
(1469078, 'R','{"NAME":"Lauenburg/Elbe","NAME:ar":"لونبورغ","NAME:az-Arab":"لونبورگ","NAME:azb":"لونبورگ","NAME:de":"Lauenburg/Elbe","NAME:fa":"لونبورگ","NAME:nds":"Loonborg","NAME:prefix":"Stadt"}'),
(1469079, 'R','{"NAME":"Krüzen"}'),
(1469080, 'R','{"NAME":"Lanze"}'),
(1469081, 'R','{"NAME":"Kollow"}'),
(1469082, 'R','{"NAME":"Schnakenbek"}'),
(1469083, 'R','{"NAME":"Juliusburg"}'),
(1469084, 'R','{"NAME":"Buchhorst"}'),
(1469085, 'R','{"NAME":"Schwarzenbek","NAME:nds":"Swattenbeek","NAME:prefix":"Stadt"}'),
(1469086, 'R','{"NAME":"Grabau"}'),
(1469087, 'R','{"NAME":"Krukow"}'),
(1469171, 'R','{"NAME":"Labenz"}'),
(1469172, 'R','{"NAME":"Sirksfelde"}'),
(1469173, 'R','{"NAME":"Borstorf"}'),
(1469214, 'R','{"NAME":"Rondeshagen"}'),
(1469215, 'R','{"NAME":"Klempau"}'),
(1469216, 'R','{"NAME":"Groß Sarau"}'),
(1469262, 'R','{"NAME":"Pogeez"}'),
(1469263, 'R','{"NAME":"Buchholz"}'),
(1469264, 'R','{"NAME":"Einhaus"}'),
(1469272, 'R','{"NAME":"Groß Disnack"}'),
(1469273, 'R','{"NAME":"Berkenthin"}'),
(1470110, 'R','{"NAME":"Mechow"}'),
(1470111, 'R','{"NAME":"Römnitz"}'),
(1470112, 'R','{"NAME":"Bäk"}'),
(1470174, 'R','{"NAME":"Kulpin"}'),
(1470175, 'R','{"NAME":"Behlendorf"}'),
(1470176, 'R','{"NAME":"Harmsdorf"}'),
(1471097, 'R','{"NAME":"Giesensdorf"}'),
(1471098, 'R','{"NAME":"Schmilau"}'),
(1471099, 'R','{"NAME":"Fredeburg"}'),
(1471100, 'R','{"NAME":"Ratzeburg","NAME:prefix":"Stadt"}'),
(1471101, 'R','{"NAME":"Lankau"}'),
(1471102, 'R','{"NAME":"Albsfelde"}'),
(1471554, 'R','{"NAME":"Brunsmark"}'),
(1473800, 'R','{"NAME":"Besenthal"}'),
(1473801, 'R','{"NAME":"Lehmrade"}'),
(1473802, 'R','{"NAME":"Göttin"}'),
(1473804, 'R','{"NAME":"Grambek"}'),
(1473805, 'R','{"NAME":"Horst"}'),
(1474024, 'R','{"NAME":"Gudow"}'),
(1475323, 'R','{"NAME":"Fitzen"}'),
(1476083, 'R','{"NAME":"Büchen"}'),
(1476084, 'R','{"NAME":"Langenlehsten"}'),
(1476085, 'R','{"NAME":"Bröthen"}'),
(1476156, 'R','{"NAME":"Klein Zecher"}'),
(1476157, 'R','{"NAME":"Salem"}'),
(1476158, 'R','{"NAME":"Sterley"}'),
(1476159, 'R','{"NAME":"Hollenbek"}'),
(1476160, 'R','{"NAME":"Seedorf"}'),
(1476161, 'R','{"NAME":"Ziethen"}'),
(1476162, 'R','{"NAME":"Mustin"}'),
(1476163, 'R','{"NAME":"Kittlitz"}'),
(1477861, 'R','{"NAME":"Selmsdorf"}'),
(1478016, 'R','{"NAME":"Dassow","NAME:prefix":"Stadt"}'),
(1479256, 'R','{"NAME":"Schönberger Land","NAME:prefix":"Amt"}'),
(1479258, 'R','{"NAME":"Lüdersdorf"}'),
(1529660, 'R','{"NAME":"Ostholstein-Mitte","NAME:prefix":"Amt"}'),
(1529661, 'R','{"NAME":"Oldenburg-Land","NAME:prefix":"Amt"}'),
(1529662, 'R','{"NAME":"Lensahn","NAME:prefix":"Amt"}'),
(1541874, 'R','{"NAME":"Lauenburgische Seen","NAME:prefix":"Amt"}'),
(1541875, 'R','{"NAME":"Lütau","NAME:prefix":"Amt"}'),
(1541876, 'R','{"NAME":"Büchen","NAME:prefix":"Amt"}'),
(1541877, 'R','{"NAME":"Schwarzenbek-Land","NAME:prefix":"Amt"}'),
(1541878, 'R','{"NAME":"Berkenthin","NAME:prefix":"Amt"}'),
(1541879, 'R','{"NAME":"Sandesneben-Nusse","NAME:prefix":"Amt"}'),
(1541880, 'R','{"NAME":"Breitenfelde","NAME:prefix":"Amt"}'),
(1567454, 'R','{"NAME":"Almdorf"}'),
(1571382, 'R','{"NAME":"Exerzierplatz"}'),
(1571761, 'R','{"NAME":"Breklum"}'),
(1572474, 'R','{"NAME":"Rönne","NAME:lt":"Rionė"}'),
(1573497, 'R','{"NAME":"Nordsee-Treene","NAME:prefix":"Amt"}'),
(1573498, 'R','{"NAME":"Pellworm","NAME:prefix":"Amt"}'),
(1573499, 'R','{"NAME":"Eiderstedt","NAME:ca":"Eiderstedt","NAME:da":"Ejdersted","NAME:de":"Eiderstedt","NAME:en":"Eiderstedt","NAME:eo":"Eiderstedt","NAME:fa":"ایدرشتدت","NAME:fr":"Eiderstedt","NAME:frr":"Eidersteed","NAME:it":"Eiderstedt","NAME:mk":"Ајдерштет","NAME:ms":"Eiderstedt","NAME:nan":"Eiderstedt","NAME:nl":"Eiderstedt","NAME:pl":"Eiderstedt","NAME:prefix":"Amt","NAME:ru":"Айдерштедт"}'),
(1574208, 'R','{"NAME":"Südtondern","NAME:prefix":"Amt"}'),
(1574209, 'R','{"NAME":"Mittleres Nordfriesland","NAME:prefix":"Amt"}'),
(1574210, 'R','{"NAME":"Viöl","NAME:de":"Viöl","NAME:prefix":"Amt"}'),
(1604264, 'R','{"NAME":"Mölln","NAME:prefix":"Stadt"}'),
(1670981, 'R','{"NAME":"Eiderkanal","NAME:prefix":"Amt"}'),
(1739380, 'R','{"NAME":"Nordwestmecklenburg","NAME:fr":"Mecklembourg-du-Nord-Ouest","NAME:prefix":"Landkreis","NAME:ru":"Северозападный Мекленбург","NAME:suffix:en":"(district)","NAME:suffix:fr":"(arrondissement)"}'),
(1739381, 'R','{"NAME":"Ludwigslust-Parchim","NAME:de":"Ludwigslust-Parchim","NAME:prefix":"Landkreis"}'),
(1810981, 'R','{"NAME":"Muxall"}'),
(1928466, 'R','{"NAME":"Aabenraa Kommune","NAME:ca":"Municipi D Aabenraa","name:da":"Aabenraa Kommune","name:de":"Kommune Apenrade","name:en":"Aabenraa Municipality","name:et":"Åbenrå vald","name:fi":"Aabenraan kunta","name:frr":"Aabenraa Komuun","name:hr":"Općina Aabenraa","name:hu":"Aabenraa község","name:id":"Munisipalitas Aabenraa","name:nds":"Kommun Openraa","name:no":"Aabenraa kommune","name:pl":"Gmina Aabenraa"}'),
       (1928515, 'R', '{
         "name": "Tønder Kommune",
         "name:ca": "Municipi de Tønder",
         "name:da": "Tønder Kommune",
         "name:de": "Kommune Tondern",
         "name:en": "Tønder Municipality",
         "name:fi": "Tønderin kunta",
         "name:fo": "Tønder kommuna",
         "name:frr": "Tønder Komuun",
         "name:nds": "Kommun Tönder",
         "name:no": "Tønder kommune",
         "name:pl": "Gmina Tønder"
       }'),
       (1948875, 'R', '{
         "name": "Hittbergen"
       }'),
       (1948876, 'R', '{
         "name": "Hohnstorf (Elbe)",
         "name:nds": "Haunstörp"
       }'),
       (1969584, 'R', '{
         "name": "Samtgemeinde Scharnebeck"
       }'),
       (2063684, 'R', '{
         "name": "Billstedt"
       }'),
       (2069504, 'R', '{
         "name": "Oststeinbek"
       }'),
       (2073736, 'R', '{
         "name": "Barsbüttel"
       }'),
       (2083497, 'R', '{
         "name": "Landkreis Harburg",
         "name:fr": "Harbourg (arrondissement)",
         "name:prefix": "Landkreis",
         "name:ru": "Харбург",
         "name:uk": "Гарбурґ"
       }'),
       (2083498, 'R', '{
         "name": "Samtgemeinde Elbmarsch"
       }'),
       (2084746, 'R', '{
         "name": "Landkreis Lüneburg",
         "name:fr": "Lunebourg (arrondissement)",
         "name:prefix": "Landkreis",
         "name:ru": "Люнебург",
         "name:uk": "Люнебурґ"
       }'),
       (2322213, 'R', '{
         "name": "Sarkwitz"
       }'),
       (2413377, 'R', '{
         "name": "Gleschendorf"
       }'),
       (2413378, 'R', '{
         "name": "Gronenberg"
       }'),
       (2413379, 'R', '{
         "name": "Haffkrug"
       }'),
       (2413380, 'R', '{
         "name": "Klingberg"
       }'),
       (2413381, 'R', '{
         "name": "Pönitz"
       }'),
       (2413382, 'R', '{
         "name": "Scharbeutz"
       }'),
       (2413383, 'R', '{
         "name": "Schulendorf"
       }'),
       (2413384, 'R', '{
         "name": "Schürsdorf"
       }'),
       (2413385, 'R', '{
         "name": "Wulfsdorf"
       }'),
       (2420743, 'R', '{
         "name": "Jork",
         "name:de": "Jork",
         "name:nds": "Jörk"
       }'),
       (2724772, 'R', '{
         "name": "Desmerciereskoog"
       }'),
       (2733685, 'R', '{
         "name": "Sophien-Magdalenen-Koog"
       }'),
       (2734054, 'R', '{
         "name": "Reußenkoog"
       }'),
       (2734069, 'R', '{
         "name": "Louisenkoog"
       }'),
       (2734075, 'R', '{
         "name": "Cecilienkoog"
       }'),
       (2790946, 'R', '{
         "name": "Mittelangeln"
       }'),
       (2799876, 'R', '{
         "name": "Steinbergkirche"
       }'),
       (2951326, 'R', '{
         "name": "Amt Mittelholstein"
       }'),
       (3289034, 'R', '{
         "name": "Dieksanderkoog"
       }'),
       (3289035, 'R', '{
         "name": "Friedrichskoog-Spitze"
       }'),
       (3289036, 'R', '{
         "name": "Kaiserin-Auguste-Viktoria-Koog"
       }'),
       (3370039, 'R', '{
         "name": "Wedel",
         "name:ar": "فيدل",
         "name:he": "ודל",
         "name:prefix": "Stadt",
         "name:ru": "Ведель",
         "name:uk": "Ведель",
         "name:zh": "韦德尔",
         "name:zh-Hans": "韦德尔",
         "name:zh-Hant": "韦德尔"
       }'),
       (3515964, 'R', '{
         "name": "Barsfleth"
       }'),
       (3515965, 'R', '{
         "name": "Christianskoog"
       }'),
       (3515966, 'R', '{
         "name": "Thalingburen"
       }'),
       (3518675, 'R', '{
         "name": "Farnewinkel"
       }'),
       (3678366, 'R', '{
         "name": "Küstengewässer einschließlich Anteil am Festlandsockel"
       }'),
       (4148060, 'R', '{
         "name": "Egenbüttel"
       }'),
       (5490048, 'R', '{
         "name": "Dückerswisch"
       }'),
       (5490049, 'R', '{
         "name": "Hohenhörn"
       }'),
       (5490050, 'R', '{
         "name": "Hohenhörn"
       }'),
       (5490575, 'R', '{
         "name": "Brunsbüttel-Nord"
       }'),
       (5490576, 'R', '{
         "name": "Mühlenstraßen"
       }'),
       (5490577, 'R', '{
         "name": "Osterbelmhusen"
       }'),
       (5490578, 'R', '{
         "name": "Ostermoor"
       }'),
       (5490579, 'R', '{
         "name": "Westerbelmhusen"
       }'),
       (5490580, 'R', '{
         "name": "Westerbüttel"
       }'),
       (5490581, 'R', '{
         "name": "Blangenmoor-Lehe"
       }'),
       (5490582, 'R', '{
         "name": "Brunsbüttel-Ort"
       }'),
       (5490583, 'R', '{
         "name": "Brunsbüttel-Süd"
       }'),
       (5609789, 'R', '{
         "name": "Wildenhorst"
       }'),
       (5615879, 'R', '{
         "name": "Beltringharder Koog",
         "name:frr": "Beltringhiirder Kuuch"
       }'),
       (5615880, 'R', '{
         "name": "Hamburger Hallig",
         "name:frr": "Hamborjer Hali"
       }'),
       (5615881, 'R', '{
         "name": "Sönke-Nissen-Koog"
       }'),
       (5902794, 'R', '{
         "name": "Hohenhorst",
         "name:prefix": "Großwohnsiedlung"
       }'),
       (6102796, 'R', '{
         "name": "Offenau"
       }'),
       (6102884, 'R', '{
         "name": "Bokholt-Hanredder"
       }'),
       (6102885, 'R', '{
         "name": "Hanredder"
       }'),
       (6102886, 'R', '{
         "name": "Voßloch"
       }'),
       (6303255, 'R', '{
         "name": "Moorsee"
       }'),
       (7507937, 'R', '{
         "name": "Sereetz"
       }'),
       (7705598, 'R', '{
         "name": "Hopen"
       }'),
       (7705599, 'R', '{
         "name": "Westdorf"
       }'),
       (7705600, 'R', '{
         "name": "Hindorf"
       }'),
       (7831308, 'R', '{
         "name": "Kleinensee"
       }'),
       (7831309, 'R', '{
         "name": "Kreuzkamp"
       }'),
       (7831312, 'R', '{
         "name": "Ovendorf"
       }'),
       (7831313, 'R', '{
         "name": "Grammersdorf"
       }'),
       (7831636, 'R', '{
         "name": "Häven"
       }'),
       (7831637, 'R', '{
         "name": "Warnsdorf"
       }'),
       (7831638, 'R', '{
         "name": "Wilmsdorf"
       }'),
       (7831639, 'R', '{
         "name": "Offendorf"
       }'),
       (7832236, 'R', '{
         "name": "Neuhof"
       }'),
       (7832237, 'R', '{
         "name": "Neu-Ruppersdorf"
       }'),
       (7832238, 'R', '{
         "name": "Alt-Ruppersdorf"
       }'),
       (7832239, 'R', '{
         "name": "Ratekau"
       }'),
       (7832282, 'R', '{
         "name": "Rohlsdorf"
       }'),
       (7832283, 'R', '{
         "name": "Alt-Techau"
       }'),
       (7832284, 'R', '{
         "name": "Neu-Techau"
       }'),
       (7832285, 'R', '{
         "name": "Hobbersdorf"
       }'),
       (7832647, 'R', '{
         "name": "Luschendorf"
       }'),
       (7832648, 'R', '{
         "name": "Pansdorf"
       }'),
       (7835407, 'R', '{
         "name": "Groß Timmendorf"
       }'),
       (7835408, 'R', '{
         "name": "Oeverdiek"
       }'),
       (7835409, 'R', '{
         "name": "Timmendorfer Strand"
       }'),
       (7835410, 'R', '{
         "name": "Hemmelsdorf"
       }'),
       (7835411, 'R', '{
         "name": "Niendorf/Ostsee"
       }'),
       (8004395, 'R', '{
         "name": "Nordhusum"
       }'),
       (8004396, 'R', '{
         "name": "Rödemis",
         "name:frr": "Rööms"
       }'),
       (8004397, 'R', '{
         "name": "Porrenkoog",
         "name:frr": "Porekuuch"
       }'),
       (8004398, 'R', '{
         "name": "Zentrum"
       }'),
       (8004399, 'R', '{
         "name": "Dreimühlen",
         "name:frr": "Träimeelne"
       }'),
       (8004400, 'R', '{
         "name": "Osterhusum",
         "name:frr": "Ååsterhüsem"
       }'),
       (8004401, 'R', '{
         "name": "Kielsburg",
         "name:frr": "Kilsborj"
       }'),
       (8004402, 'R', '{
         "name": "Schauendahl"
       }'),
       (8004403, 'R', '{
         "name": "Lund",
         "name:frr": "de Lün"
       }'),
       (8004404, 'R', '{
         "name": "Hockensbüll",
         "name:frr": "Hukensbel"
       }'),
       (8004405, 'R', '{
         "name": "Schobüll",
         "name:frr": "Schööbel"
       }'),
       (8004406, 'R', '{
         "name": "Halebüll"
       }'),
       (8111524, 'R', '{
         "name": "Brodersby-Goltoft"
       }'),
       (8111540, 'R', '{
         "name": "Stapel"
       }'),
       (8155066, 'R', '{
         "name": "Ketelsbüttel"
       }'),
       (8494373, 'R', '{
         "name": "Westerkoog"
       }'),
       (8836647, 'R', '{
         "name": "Russee"
       }'),
       (9112011, 'R', '{
         "name": "Kongeriget Danmark",
         "name:ar": "مملكة الدنمارك",
         "name:be": "Каралеўства Данія",
         "name:br": "Rouantelezh Danmark",
         "name:ca": "Regne de Dinamarca",
         "name:ckb": "شانشینی دانمارک",
         "name:da": "Kongeriget Danmark",
         "name:de": "Königreich Dänemark",
         "name:en": "Kingdom of Denmark",
         "name:es": "Reino de Dinamarca",
         "name:eu": "Danimarkako Erresuma",
         "name:fi": "Tanskan kuningaskunta",
         "name:fr": "Royaume du Danemark",
         "name:fur": "Ream di Danimarcje",
         "name:gsw": "Kìnnigrëich Dänemàrik",
         "name:hr": "Kraljevina Danska",
         "name:hu": "Dán Királyság",
         "name:is": "Konungsríkið Danmörk",
         "name:it": "Regno di Danimarca",
         "name:ja": "デンマーク王国",
         "name:mk": "Данска",
         "name:nl": "Koninkrijk Denemarken",
         "name:pl": "Królestwo Danii",
         "name:pt": "Reino da Dinamarca",
         "name:ru": "Датское королевство",
         "name:sv": "Konungariket Danmark",
         "name:uk": "Королівство Данія",
         "name:zh": "丹麦王国",
         "name:zh-Hans": "丹麦王国",
         "name:zh-Hant": "丹麥王國"
       }'),
       (9136426, 'R', '{
         "name": "Tensbüttel"
       }'),
       (9136427, 'R', '{
         "name": "Röst"
       }'),
       (9216274, 'R', '{
         "name": "Bennewohld"
       }'),
       (9216275, 'R', '{
         "name": "Süderholm"
       }'),
       (9494201, 'R', '{
         "name": "Mettenhof"
       }'),
       (9494202, 'R', '{
         "name": "Hasseldieksdamm"
       }'),
       (9494340, 'R', '{
         "name": "Suchsdorf"
       }'),
       (9588551, 'R', '{
         "name": "Gemarkung Pötrau"
       }'),
       (9606254, 'R', '{
         "name": "Gemarkung Büchen"
       }'),
       (9606255, 'R', '{
         "name": "Gemarkung Nüssau"
       }'),
       (9752837, 'R', '{
         "name": "Damperhof"
       }'),
       (9752887, 'R', '{
         "name": "Altstadt"
       }'),
       (9752929, 'R', '{
         "name": "Vorstadt"
       }'),
       (9756347, 'R', '{
         "name": "Meimersdorf"
       }'),
       (9756453, 'R', '{
         "name": "Südfriedhof"
       }'),
       (9760515, 'R', '{
         "name": "Hassee"
       }'),
       (9789989, 'R', '{
         "name": "Nordstrandischmoor"
       }'),
       (9946607, 'R', '{
         "name": "Wellsee"
       }'),
       (9946706, 'R', '{
         "name": "Gaarden-Süd/Kronsburg"
       }'),
       (9950654, 'R', '{
         "name": "Holtenau"
       }'),
       (9950655, 'R', '{
         "name": "Schilksee"
       }'),
       (9950656, 'R', '{
         "name": "Pries"
       }'),
       (9950657, 'R', '{
         "name": "Friedrichsort"
       }'),
       (9955137, 'R', '{
         "name": "Elmschenhagen"
       }'),
       (10041352, 'R', '{
         "name": "Neuwerk",
         "name:prefix": "Stadtteil"
       }'),
       (10130771, 'R', '{
         "name": "Böcklersiedlung/Bugenhagen"
       }'),
       (10130878, 'R', '{
         "name": "Faldera"
       }'),
       (10130880, 'R', '{
         "name": "Wittorf"
       }'),
       (10130901, 'R', '{
         "name": "Gadeland"
       }'),
       (10130939, 'R', '{
         "name": "Brachenfeld/Ruthenberg"
       }'),
       (10130940, 'R', '{
         "name": "Tungendorf"
       }'),
       (10131629, 'R', '{
         "name": "Einfeld"
       }'),
       (10131630, 'R', '{
         "name": "Gartenstadt"
       }'),
       (10131631, 'R', '{
         "name": "Innenstadt"
       }'),
       (11102997, 'R', '{
         "name": "Oesterborstel"
       }'),
       (11127017, 'R', '{
         "name": "Trischen"
       }'),
       (11299761, 'R', '{
         "name": "Ellerbek"
       }'),
       (11299762, 'R', '{
         "name": "Gaarden-Ost"
       }'),
       (11299763, 'R', '{
         "name": "Wellingdorf"
       }'),
       (11338919, 'R', '{
         "name": "Westerland",
         "name:de": "Westerland",
         "name:frr": "Weesterlön"
       }'),
       (11358280, 'R', '{
         "name": "Rantum",
         "name:frr": "Raantem"
       }'),
       (11362193, 'R', '{
         "name": "Tinnum",
         "name:frr": "Tinem"
       }'),
       (11634645, 'R', '{
         "name": "Mühlenstraßen/Westerbelmhusen"
       }'),
       (11634646, 'R', '{
         "name": "Westerbüttel/Osterbelmhusen"
       }'),
       (11634647, 'R', '{
         "name": "Brunsbüttel-Süd/Ostermoor"
       }'),
       (13136508, 'R', '{
         "name": "Obermarschacht"
       }'),
       (13136509, 'R', '{
         "name": "Rönne"
       }'),
       (13136510, 'R', '{
         "name": "Niedermarschacht"
       }'),
       (14752833, 'R', '{
         "name": "Schwabstedter Westerkoog"
       }'),
       (15626869, 'R', '{
         "name": "Mürwik",
         "name:da": "Mørvig"
       }'),
       (15626870, 'R', '{
         "name": "Fruerlund",
         "name:da": "Fruerlund"
       }'),
       (15626871, 'R', '{
         "name": "Engelsby",
         "name:da": "Engelsby"
       }'),
       (15626872, 'R', '{
         "name": "Jürgensby",
         "name:da": "Jørgensby"
       }'),
       (15627109, 'R', '{
         "name": "Sandberg",
         "name:da": "Sandbjerg"
       }'),
       (15627110, 'R', '{
         "name": "Tarup",
         "name:da": "Tarup"
       }'),
       (15627111, 'R', '{
         "name": "Südstadt",
         "name:da": "Sydstaden"
       }'),
       (15630273, 'R', '{
         "name": "Altstadt",
         "name:da": "Indre By"
       }'),
       (15630508, 'R', '{
         "name": "Friesischer Berg",
         "name:da": "Friserbjerg"
       }'),
       (15630509, 'R', '{
         "name": "Weiche",
         "name:da": "Sporskifte"
       }'),
       (15634214, 'R', '{
         "name": "Westliche Höhe",
         "name:da": "Vestlige Højde"
       }'),
       (15634215, 'R', '{
         "name": "Neustadt",
         "name:da": "Nystaden"
       }'),
       (15634216, 'R', '{
         "name": "Nordstadt",
         "name:da": "Nordstaden"
       }'),
       (15651152, 'R', '{
         "name": "St. Marien"
       }'),
       (15651153, 'R', '{
         "name": "St. Nikolai"
       }'),
       (15651154, 'R', '{
         "name": "Nordertor"
       }'),
       (15651908, 'R', '{
         "name": "Duburg"
       }'),
       (15651909, 'R', '{
         "name": "Neustadt-Nord"
       }'),
       (15652249, 'R', '{
         "name": "Kreuz"
       }'),
       (15652250, 'R', '{
         "name": "Galwik"
       }'),
       (15652251, 'R', '{
         "name": "Klues"
       }'),
       (15652851, 'R', '{
         "name": "Stadtpark"
       }'),
       (15652852, 'R', '{
         "name": "Marienhölzung"
       }'),
       (15652853, 'R', '{
         "name": "St. Gertrud"
       }'),
       (15652854, 'R', '{
         "name": "Friedhof"
       }'),
       (15652886, 'R', '{
         "name": "Exe"
       }'),
       (15652887, 'R', '{
         "name": "Museumsberg"
       }'),
       (15652888, 'R', '{
         "name": "Friedhofshügel"
       }'),
       (15652898, 'R', '{
         "name": "Sophienhof"
       }'),
       (15652899, 'R', '{
         "name": "Schäferhaus"
       }'),
       (15655409, 'R', '{
         "name": "Martinsberg"
       }'),
       (15655410, 'R', '{
         "name": "Rude"
       }'),
       (15655411, 'R', '{
         "name": "Peelwatt"
       }'),
       (15655762, 'R', '{
         "name": "Achter de Möhl"
       }'),
       (15655763, 'R', '{
         "name": "Adelbylund"
       }'),
       (15655764, 'R', '{
         "name": "Sünderup-West"
       }'),
       (15656896, 'R', '{
         "name": "St. Johannis"
       }'),
       (15656897, 'R', '{
         "name": "St. Jürgen"
       }'),
       (15656898, 'R', '{
         "name": "Jürgensgaard"
       }'),
       (15656899, 'R', '{
         "name": "Sender Flensburg-Jürgensby"
       }'),
       (15656948, 'R', '{
         "name": "Blasberg"
       }'),
       (15656949, 'R', '{
         "name": "Bohlberg"
       }'),
       (15656950, 'R', '{
         "name": "Fruerlundhof"
       }'),
       (15660015, 'R', '{
         "name": "Stützpunkt Flensburg-Mürwik"
       }'),
       (15660016, 'R', '{
         "name": "Osbek"
       }'),
       (15660017, 'R', '{
         "name": "Wasserloos"
       }'),
       (15660018, 'R', '{
         "name": "Friedheim"
       }'),
       (15660019, 'R', '{
         "name": "Solitüde"
       }'),
       (15660040, 'R', '{
         "name": "Engelsby-Süd"
       }'),
       (15660041, 'R', '{
         "name": "Vogelsang"
       }'),
       (18335004, 'R', '{
         "name": "Rendsburg Nord"
       }'),
       (18335005, 'R', '{
         "name": "Kronwerker Moor"
       }'),
       (18335006, 'R', '{
         "name": "Rendsburg West"
       }'),
       (18335007, 'R', '{
         "name": "Rendsburg Nordwest"
       }'),
       (18335008, 'R', '{
         "name": "Duten"
       }'),
       (18335009, 'R', '{
         "name": "Mastbrook"
       }'),
       (18335010, 'R', '{
         "name": "Suhmsheide"
       }'),
       (18335011, 'R', '{
         "name": "Seemühlen"
       }'),
       (18335012, 'R', '{
         "name": "Rotenhof"
       }'),
       (18335013, 'R', '{
         "name": "Kronwerk Nord"
       }'),
       (18335014, 'R', '{
         "name": "Rendsburg Südwest"
       }'),
       (18335015, 'R', '{
         "name": "Mühlenau-Margarethenhof"
       }'),
       (18335016, 'R', '{
         "name": "Altstadt"
       }'),
       (18335017, 'R', '{
         "name": "Neuwerk"
       }'),
       (18335018, 'R', '{
         "name": "Parksiedlung"
       }'),
       (18335019, 'R', '{
         "name": "Königskoppel"
       }'),
       (18335020, 'R', '{
         "name": "Nobiskrug"
       }'),
       (18335021, 'R', '{
         "name": "Schleife"
       }'),
       (18335022, 'R', '{
         "name": "Hochfeld"
       }'),
       (18335023, 'R', '{
         "name": "Kanalgebiet Ost"
       }'),
       (18335024, 'R', '{
         "name": "Neuwerk Süd"
       }'),
       (18335025, 'R', '{
         "name": "Kreishafen"
       }'),
       (18335026, 'R', '{
         "name": "Kanalgebiet West"
       }'),
       (18335027, 'R', '{
         "name": "Hoheluft"
       }'),
       (18335028, 'R', '{
         "name": "Nübbeler Utkiek"
       }'),
       (18335029, 'R', '{
         "name": "Rendsburg Süd"
       }'),
       (18335030, 'R', '{
         "name": "Marienhöh"
       }'),
       (18335031, 'R', '{
         "name": "Stadtmoor"
       }'),
       (18335032, 'R', '{
         "name": "Kronwerk Süd"
       }'),
       (18335033, 'R', '{
         "name": "Rendsburg-Nord"
       }'),
       (18335034, 'R', '{
         "name": "Rendsburg-West"
       }'),
       (18335035, 'R', '{
         "name": "Rendsburg-Mitte"
       }'),
       (18335036, 'R', '{
         "name": "Rendsburg-Ost"
       }'),
       (18335037, 'R', '{
         "name": "Rendsburg-Süd"
       }'),
       (20017662, 'R', '{
         "name": "Stove"
       }'),
       (20232786, 'R', '{
         "name": "Assel"
       }'),
       (20232787, 'R', '{
         "name": "Krautsand"
       }'),
       (20239409, 'R', '{
         "name": "Bützfleth"
       }');

CREATE INDEX IF NOT EXISTS idx_osm_names_id_type
    ON osm_names (osm_id, osm_type);
CREATE INDEX IF NOT EXISTS idx_osm_names_all_names_gin
    ON osm_names USING gin (all_names);

