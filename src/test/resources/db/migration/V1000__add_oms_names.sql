CREATE TABLE IF NOT EXISTS osm_names
(
    osm_id    bigint,
    osm_type  character(1),
    all_names jsonb,
    PRIMARY KEY (osm_id, osm_type)
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
         "name:chy": "Maevého éno","name:ckb":"ئاڵمانیا","name:co":"Germania","name:crh":"Almaniya","name:cs":"Německo","name:csb":"Miemieckô","name:cu":"Нѣмьци","name:cv":"Германи","name:cy":"Yr Almaen","name:da":"Tyskland","name:de":"Deutschland","name:diq":"Almanya","name:dsb":"Nimska","name:dv":"ޖަރުމަނުވިލާތް","name:dz":"ཇཱར་མ་ནི","name:ee":"Germany","name:el":"Γερμανία","name:eml":"Germâgna","name:en":"Germany","name:eo":"Germanio","name:es":"Alemania","name:et":"Saksamaa","name:eu":"Alemania","name:ext":"Alemaña","name:fa":"آلمان","name:ff":"Almaanya","name:fi":"Saksa","name:fo":"Týskland","name:fr":"Allemagne","name:frp":"Alemagne","name:frr":"Tjüschlönj","name:fur":"Gjermanie","name:fy":"Dútslân","name:ga":"An Ghearmáin","name:gag":"Germaniya","name:gan":"德國","name:gd":"A Ghearmailt","name:gl":"Alemaña","name:glk":"آلمان","name:gn":"Alemaña","name:gsw":"Ditschland","name:gu":"જર્મની","name:gv":"Yn Ghermaan","name:ha":"Jamus","name:hak":"Tet-koet","name:haw":"Kelemānia","name:he":"גרמניה","name:hi":"जर्मनी","name:hif":"Germany","name:hr":"Njemačka","name:hsb":"Němska","name:ht":"Almay","name:hu":"Németország","name:hy":"Գերմանիա","name:ia":"Germania","name:id":"Jerman","name:ie":"Germania","name:ig":"Jémanị","name:ilo":"Alemania","name:io":"Germania","name:is":"Þýskaland","name:it":"Germania","name:iu":"ᔮᒪᓂ","name:ja":"ドイツ","name:jbo":"dotygu e","name:jv":"Jerman","name:ka":"გერმანია","name:kaa":"Germaniya","name:kab":"Lalman","name:kbd":"Джэрмэн","name:kg":"Alemanyi","name:ki":"Germany","name:kk":"Германия Федеративтік Республикасы","name:kl":"Tyskit Nunaat","name:km":"អាល្លឺម៉ង់","name:kn":"ಜರ್ಮನಿ","name:ko":"독일","name:koi":"Немечму","name:krc":"Германия","name:ks":"جرمٔنی","name:ksh":"Dütschland","name:ku":"Almanya","name:kv":"Германия","name:kw":"Almayn","name:ky":"Германия","name:la":"Germania","name:lad":"Almania","name:lb":"Däitschland","name:lez":"Германия","name:lg":"Girimane","name:li":"Duutsjlandj","name:lij":"Germania","name:lmo":"Germania","name:ln":"Alémani","name:lo":"ປະເທດເຢັຽລະມັນ","name:lrc":"آلمان","name:lt":"Vokietija","name:ltg":"Vuoceja","name:lv":"Vācija","name:lzh":"德國","name:map-bms":"Jerman","name:mdf":"Германие мастор","name:mg":"Alemaina","name:mhr":"Немыч Эл","name:mi":"Tiamana","name:min":"Jerman","name:mk":"Германија","name:ml":"ജർമ്മനി","name:mn":"Герман","name:mo":"Ӂермания","name:mr":"जर्मनी","name:ms":"Jerman","name:mt":"Ġermanja","name:mwl":"Almanha","name:my":"ဂျာမနီနိုင်ငံ","name:myv":"Германия Мастор","name:mzn":"آلمان","name:na":"Djermani","name:nah":"Teutontlālpan","name:nan":"Tek-kok","name:nap":"Germania","name:nds":"Düütschland","name:nds-nl":"Duutslaand","name:ne":"जर्मनी","name:NEW":"जर्मनी","name:nl":"Duitsland","name:nn":"Tyskland","name:NO":"Tyskland","name:nov":"Germania","name:nrm":"Allemangne","name:nv":"Béésh Bichʼahii Bikéyah","name:oc":"Alemanha","name:OJ":"Agongosiwaki","name:OR":"ଜର୍ମାନୀ","name:os":"Герман","name:pa":"ਜਰਮਨੀ","name:pa-Arab":"جرمن","name:pag":"Alemanya","name:pam":"Alemania","name:pap":"Alemania","name:pcd":"Alemanne","name:pdc":"Deitschland","name:pfl":"Daitschlond","name:pih":"Doichland","name:pl":"Niemcy","name:pms":"Gërmania","name:pnb":"جرمن","name:pnt":"Γερμανία","name:ps":"آلمان","name:pt":"Alemanha","name:qu":"Alimanya","name:rm":"Germania","name:rmy":"Jermaniya","name:rn":"Ubudagi","name:ro":"Germania","name:roa-tara":"Germanie","name:ru":"Германия","name:rue":"Нїмецько","name:rw":"Ubudage","name:sa":"जर्मनी","name:sah":"Германия","name:sc":"Germània","name:scn":"Girmania","name:sco":"Germany","name:se":"Duiska","name:sh":"Nemačka","name:si":"ජර්මනිය","name:sk":"Nemecko","name:sl":"Nemčija","name:sm":"Siamani","name:smn":"Saksa","name:sms":"Saksslajânnam","name:so":"Jarmalka","name:sq":"Gjermania","name:sr":"Немачка","name:sr-Latn":"Nemačka","name:srn":"Doysrikondre","name:ss":"IJalimane","name:stq":"Düütsklound","name:su":"Jérman","name:sv":"Tyskland","name:sw":"Ujerumani","name:szl":"Niymce","name:ta":"செருமனி","name:te":"జర్మనీ","name:tet":"Alemaña","name:tg":"Олмон","name:th":"ประเทศเยอรมนี","name:ti":"ጀርመን","name:tk":"Germaniýa","name:tl":"Alemanya","name:tok":"ma Tosi","name:tpi":"Siamani","name:tr":"Almanya","name:ts":"Jarimani","name:tt":"Алмания","name:tum":"Germany","name:tw":"Gyaaman","name:ty":"Heremani","name:tzl":"Tzaratütsch","name:udm":"Германия","name:ug":"گېرمانىيە","name:uk":"Німеччина","name:ur":"جرمنی","name:uz":"Olmoniya","name:vec":"Germania","name:vep":"Saksanma","name:vi":"Đức","name:vls":"Duutsland","name:vo":"Deutän","name:vro":"Saksamaa","name:wa":"Almagne","name:war":"Alemanya","name:win":"Taaǧiri Mąą","name:wo":"Almaañ","name:xal":"Ниицәтә Немшин Орн","name:xh":"IJamani","name:xmf":"გერმანია","name:yi":"דייטשלאנד","name:yo":"Jẹ́mánì","name:yue":"德國","name:za":"Dwzgoz","name:zea":"Duutsland","name:zh":"德国;德國","name:zh-Hans":"德国","name:zh-Hant":"德國","name:zu":"IJalimani"}'),
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
         "name:tr": "Neustadt Holsteinnın","name:tt":"Нойштадт Гольштейн","name:uk":"Нойштадт у Гольштейн","name:uz":"Holsteinda Neustadt","name:vi":"Neustadt ở Holstein","name:vo":"Neustadt IN Holstein","name:war":"Neustadt ha Holstein","name:zh":"霍尔斯泰因地区诺伊斯塔特"}'),
(382434, 'R','{"name":"Sierksdorf"}'),
(382435, 'R','{"name":"Malente"}'),
(382441, 'R','{"name":"Bosau"}'),
(382442, 'R','{"name":"Ahrensbök"}'),
(382443, 'R','{"name":"Scharbeutz"}'),
(382444, 'R','{"name":"Timmendorfer Strand"}'),
(382445, 'R','{"name":"Ratekau"}'),
(382446, 'R','{"name":"Bad Schwartau","name:prefix":"Stadt","name:ru":"Бад-Швартау"}'),
(382447, 'R','{"name":"Stockelsdorf"}'),
(382448, 'R','{"name":"Fehmarn","name:prefix":"Stadt"}'),
(401824, 'R','{"name":"Heider Umland","name:prefix":"Amt"}'),
(403810, 'R','{"name":"Mitteldithmarschen","name:prefix":"Amt"}'),
(404298, 'R','{"name":"Glasau"}'),
(404324, 'R','{"name":"Travenhorst"}'),
(404614, 'R','{"name":"Altengamme"}'),
(404615, 'R','{"name":"Curslack"}'),
(404618, 'R','{"name":"Bergedorf"}'),
(404943, 'R','{"name":"Lohbrügge"}'),
(405626, 'R','{"name":"Stocksee"}'),
(405627, 'R','{"name":"Schmalensee"}'),
(405629, 'R','{"name":"Bornhöved"}'),
(405630, 'R','{"name":"Gönnebek"}'),
(405631, 'R','{"name":"Trappenkamp"}'),
(406332, 'R','{"name":"Tarbek"}'),
(406356, 'R','{"name":"Tensfeld"}'),
(406901, 'R','{"name":"Nehms"}'),
(410284, 'R','{"name":"Heide","name:nds":"Heid","name:prefix":"Stadt"}'),
(411624, 'R','{"name":"Damsdorf"}'),
(412078, 'R','{"name":"Wöhrden"}'),
(412881, 'R','{"name":"Nordhastedt"}'),
(412906, 'R','{"name":"Hemmingstedt"}'),
(412962, 'R','{"name":"Lieth"}'),
(412963, 'R','{"name":"Lohe-Rickelshof"}'),
(413110, 'R','{"name":"Wesseln"}'),
(413291, 'R','{"name":"Ostrohe"}'),
(413440, 'R','{"name":"Weddingstedt"}'),
(415685, 'R','{"name":"Norderwöhrden"}'),
(415686, 'R','{"name":"Stelle-Wittenwurth"}'),
(415687, 'R','{"name":"Neuenkirchen"}'),
(415727, 'R','{"name":"Nordermeldorf"}'),
(416356, 'R','{"name":"Epenwöhrden"}'),
(416357, 'R','{"name":"Meldorf","name:nds":"Meldörp","name:prefix":"Stadt"}'),
(417591, 'R','{"name":"Busenwurth"}'),
(417592, 'R','{"name":"Elpersbüttel"}'),
(418085, 'R','{"name":"Barlt"}'),
(418220, 'R','{"name":"Gudendorf"}'),
(418222, 'R','{"name":"Windbergen"}'),
(418230, 'R','{"name":"Wolmersdorf"}'),
(418260, 'R','{"name":"Krumstedt"}'),
(419144, 'R','{"name":"Trittau"}'),
(420611, 'R','{"name":"Nindorf"}'),
(420612, 'R','{"name":"Bargenstedt"}'),
(422634, 'R','{"name":"Norderstedt","name:prefix":"Stadt","name:uk":"Нордерштедт"}'),
(422677, 'R','{"name":"Wakendorf II"}'),
(422678, 'R','{"name":"Kayhude"}'),
(422706, 'R','{"name":"Nahe"}'),
(422730, 'R','{"name":"Sülfeld"}'),
(422731, 'R','{"name":"Fredesdorf"}'),
(422732, 'R','{"name":"Groß Niendorf"}'),
(422747, 'R','{"name":"Leezen"}'),
(422748, 'R','{"name":"Kükels"}'),
(422749, 'R','{"name":"Struvenhütten"}'),
(422750, 'R','{"name":"Sievershütten"}'),
(422751, 'R','{"name":"Hüttblek"}'),
(422763, 'R','{"name":"Schwissel"}'),
(422764, 'R','{"name":"Bebensee"}'),
(422765, 'R','{"name":"Dreggers"}'),
(422814, 'R','{"name":"Wakendorf I"}'),
(422815, 'R','{"name":"Bahrenhof"}'),
(422816, 'R','{"name":"Bühnsdorf"}'),
(422841, 'R','{"name":"Neuengörs"}'),
(422842, 'R','{"name":"Traventhal"}'),
(422843, 'R','{"name":"Högersdorf"}'),
(422844, 'R','{"name":"Fahrenkrug"}'),
(422845, 'R','{"name":"Wittenborn"}'),
(422846, 'R','{"name":"Mözen"}'),
(422847, 'R','{"name":"Todesfelde"}'),
(422848, 'R','{"name":"Stuvenborn"}'),
(422854, 'R','{"name":"Oering"}'),
(422855, 'R','{"name":"Seth"}'),
(422875, 'R','{"name":"Wahlstedt","name:prefix":"Stadt"}'),
(422876, 'R','{"name":"Negernbötel"}'),
(422877, 'R','{"name":"Schackendorf"}'),
(422878, 'R','{"name":"Daldorf"}'),
(422879, 'R','{"name":"Bark"}'),
(422880, 'R','{"name":"Hartenholm"}'),
(422932, 'R','{"name":"Sarzbüttel"}'),
(422934, 'R','{"name":"Odderade"}'),
(422974, 'R','{"name":"Schafstedt"}'),
(423032, 'R','{"name":"Albersdorf"}'),
(423041, 'R','{"name":"Tensbüttel-Röst"}'),
(423046, 'R','{"name":"Arkebek"}'),
(423149, 'R','{"name":"Groß Kummerfeld"}'),
(423150, 'R','{"name":"Latendorf"}'),
(423151, 'R','{"name":"Rickling"}'),
(423152, 'R','{"name":"Heidmühlen"}'),
(423179, 'R','{"name":"Bimöhlen"}'),
(423180, 'R','{"name":"Schmalfeld"}'),
(423181, 'R','{"name":"Hasenmoor"}'),
(423182, 'R','{"name":"Lentföhrden"}'),
(423183, 'R','{"name":"Nützen"}'),
(423184, 'R','{"name":"Kaltenkirchen","name:prefix":"Stadt","name:uk":"Кальтенкірхен"}'),
(423185, 'R','{"name":"Weddelbrook"}'),
(423186, 'R','{"name":"Föhrden-Barl"}'),
(423187, 'R','{"name":"Hagen"}'),
(423188, 'R','{"name":"Fuhlendorf"}'),
(423189, 'R','{"name":"Borstel"}'),
(423190, 'R','{"name":"Hardebek"}'),
(423191, 'R','{"name":"Armstedt"}'),
(423222, 'R','{"name":"Wiemersdorf"}'),
(423223, 'R','{"name":"Bad Bramstedt","name:prefix":"Stadt","name:uk":"Бад-Брамштедт"}'),
(423224, 'R','{"name":"Hitzhusen"}'),
(423225, 'R','{"name":"Großenaspe"}'),
(423226, 'R','{"name":"Boostedt"}'),
(423230, 'R','{"name":"Amt Bad Bramstedt-Land"}'),
(423231, 'R','{"name":"Mönkloh"}'),
(423232, 'R','{"name":"Heidmoor"}'),
(430468, 'R','{"name":"Kattendorf"}'),
(430469, 'R','{"name":"Oersdorf"}'),
(430471, 'R','{"name":"Winsen"}'),
(430480, 'R','{"name":"Kisdorf","name:prefix":"Amt"}'),
(442742, 'R','{"name":"Leezen","name:prefix":"Amt"}'),
(442744, 'R','{"name":"Itzstedt","name:prefix":"Amt"}'),
(442762, 'R','{"name":"Henstedt-Ulzburg","name:ru":"Хенштедт-Ульцбург"}'),
(442763, 'R','{"name":"Ellerau","name:ru":"Эллерау"}'),
(442764, 'R','{"name":"Alveslohe"}'),
(442765, 'R','{"name":"Auenland Südholstein","name:prefix":"Amt"}'),
(442874, 'R','{"name":"Boostedt-Rickling","name:prefix":"Amt"}'),
(442882, 'R','{"name":"Bornhöved","name:prefix":"Amt"}'),
(442911, 'R','{"name":"Trave-Land","name:prefix":"Amt"}'),
(442912, 'R','{"name":"Bad Segeberg","name:nds":"Bad Seebarg","name:prefix":"Stadt"}'),
(442913, 'R','{"name":"Klein Gladebrügge"}'),
(442914, 'R','{"name":"Weede"}'),
(442915, 'R','{"name":"Geschendorf"}'),
(442916, 'R','{"name":"Strukdorf"}'),
(442917, 'R','{"name":"Stipsdorf"}'),
(442918, 'R','{"name":"Pronstorf"}'),
(442919, 'R','{"name":"Krems II"}'),
(442926, 'R','{"name":"Groß Rönnau"}'),
(442927, 'R','{"name":"Blunk"}'),
(442929, 'R','{"name":"Rohlstorf"}'),
(442930, 'R','{"name":"Wensin"}'),
(442931, 'R','{"name":"Schieren"}'),
(442932, 'R','{"name":"Klein Rönnau"}'),
(443081, 'R','{"name":"Schrum"}'),
(443085, 'R','{"name":"Immenstedt"}'),
(443086, 'R','{"name":"Osterrade"}'),
(443103, 'R','{"name":"Wennbüttel"}'),
(443121, 'R','{"name":"Bunsoh"}'),
(443122, 'R','{"name":"Offenbüttel"}'),
(443183, 'R','{"name":"Hetlingen"}'),
(443184, 'R','{"name":"Haseldorf"}'),
(443185, 'R','{"name":"Heist"}'),
(443186, 'R','{"name":"Haselau"}'),
(443192, 'R','{"name":"Seestermühe"}'),
(443193, 'R','{"name":"Neuendeich"}'),
(443194, 'R','{"name":"Moorrege"}'),
(443283, 'R','{"name":"Groß Nordende"}'),
(443285, 'R','{"name":"Heidgraben"}'),
(443298, 'R','{"name":"Seester"}'),
(443299, 'R','{"name":"Raa-Besenbek"}'),
(443302, 'R','{"name":"Elmshorn","name:prefix":"Stadt","name:uk":"Ельмсгорн"}'),
(443339, 'R','{"name":"Tornesch","name:prefix":"Stadt"}'),
(443358, 'R','{"name":"Prisdorf"}'),
(443359, 'R','{"name":"Kummerfeld"}'),
(443360, 'R','{"name":"Ellerhoop"}'),
(443439, 'R','{"name":"Seeth-Ekholt"}'),
(443484, 'R','{"name":"Appen"}'),
(443485, 'R','{"name":"Schenefeld","name:prefix":"Stadt"}'),
(443486, 'R','{"name":"Halstenbek","name:nds":"Halstenbeek"}'),
(443487, 'R','{"name":"Pinneberg","name:frr":"Pinebärj","name:nds":"Pinnbarg","name:prefix":"Stadt","name:uk":"Піннеберґ"}'),
(443703, 'R','{"name":"Ellerbek"}'),
(443704, 'R','{"name":"Bönningstedt","name:nds":"Bönningsteed"}'),
(443705, 'R','{"name":"Hasloh","name:nds":"Hasloh"}'),
(443706, 'R','{"name":"Tangstedt"}'),
(443731, 'R','{"name":"Borstel-Hohenraden"}'),
(443732, 'R','{"name":"Pinnau","name:prefix":"Amt"}'),
(443748, 'R','{"name":"Amt Geest und Marsch Südholstein","name:prefix":"Amt"}'),
(443749, 'R','{"name":"Quickborn","name:prefix":"Stadt","name:ru":"Квикборн","name:uk":"Квікборн"}'),
(443755, 'R','{"name":"Rellingen"}'),
(443947, 'R','{"name":"Klein Offenseth-Sparrieshoop","name:nds":"Lütt Offenseet-Sparrshoop"}'),
(443948, 'R','{"name":"Bokholt-Hanredder","name:nds":"Bookholt-Hanredder"}'),
(443949, 'R','{"name":"Kölln-Reisiek"}'),
(443950, 'R','{"name":"Klein Nordende"}'),
(443951, 'R','{"name":"Elmshorn-Land","name:prefix":"Amt"}'),
(443953, 'R','{"name":"Bullenkuhlen"}'),
(444135, 'R','{"name":"Groß Offenseth-Aspern"}'),
(444140, 'R','{"name":"Barmstedt","name:prefix":"Stadt"}'),
(444152, 'R','{"name":"Bilsen"}'),
(444153, 'R','{"name":"Bevern"}'),
(444154, 'R','{"name":"Hemdingen"}'),
(444155, 'R','{"name":"Langeln"}'),
(444156, 'R','{"name":"Heede"}'),
(444180, 'R','{"name":"Lutzhorn"}'),
(444181, 'R','{"name":"Brande-Hörnerkirchen"}'),
(444182, 'R','{"name":"Westerhorn"}'),
(444183, 'R','{"name":"Osterhorn"}'),
(444184, 'R','{"name":"Bokel"}'),
(444185, 'R','{"name":"Hörnerkirchen","name:prefix":"Amt"}'),
(444189, 'R','{"name":"Rantzau","name:prefix":"Amt"}'),
(444231, 'R','{"name":"Neversdorf"}'),
(444300, 'R','{"name":"Tangstedt"}'),
(444323, 'R','{"name":"Itzstedt"}'),
(444768, 'R','{"name":"Drochtersen","name:nds":"Drochters"}'),
(444799, 'R','{"name":"Stade","name:nds":"Stood","name:prefix":"Hansestadt","name:uk":"Штаде"}'),
(444923, 'R','{"name":"Hollern-Twielenfleth","name:de":"Hollern-Twielenfleth","name:nds":"Hullern-Twielenfleth"}'),
(444924, 'R','{"name":"Steinkirchen","name:nds":"Steenkark"}'),
(444930, 'R','{"name":"Samtgemeinde Lühe"}'),
(445505, 'R','{"name":"Hasenkrug"}'),
(445788, 'R','{"name":"Warwerort"}'),
(446464, 'R','{"name":"Wischhafen","name:nds":"Wischhoben"}'),
(446465, 'R','{"name":"Freiburg (Elbe)","name:prefix":"Flecken"}'),
(446466, 'R','{"name":"Krummendeich","name:nds":"Krummendiek"}'),
(446467, 'R','{"name":"Balje"}'),
(446508, 'R','{"name":"Büsum-Wesselburen","name:prefix":"Amt"}'),
(447159, 'R','{"name":"Büsumer Deichhausen"}'),
(447160, 'R','{"name":"Büsum","name:ru":"Бюзум"}'),
(447161, 'R','{"name":"Friedrichsgabekoog"}'),
(447162, 'R','{"name":"Oesterdeichstrich"}'),
(447163, 'R','{"name":"Westerdeichstrich"}'),
(447164, 'R','{"name":"Wesselburener Deichhausen"}'),
(447165, 'R','{"name":"Reinsbüttel"}'),
(447166, 'R','{"name":"Hedwigenkoog"}'),
(447188, 'R','{"name":"Altenmoor"}'),
(447189, 'R','{"name":"Neuendorf bei Elmshorn"}'),
(447190, 'R','{"name":"Kollmar"}'),
(447191, 'R','{"name":"Herzhorn"}'),
(447192, 'R','{"name":"Engelbrechtsche Wildnis"}'),
(447193, 'R','{"name":"Kiebitzreihe"}'),
(447194, 'R','{"name":"Horst","name:suffix":"(Holstein)"}'),
(447195, 'R','{"name":"Hohenfelde"}'),
(447196, 'R','{"name":"Sommerland"}'),
(447197, 'R','{"name":"Borsfleth"}'),
(447198, 'R','{"name":"Krempdorf"}'),
(447199, 'R','{"name":"Blomesche Wildnis"}'),
(447200, 'R','{"name":"Horst-Herzhorn","name:prefix":"Amt"}'),
(447206, 'R','{"name":"Glückstadt","name:prefix":"Stadt"}'),
(447255, 'R','{"name":"Süderau"}'),
(447312, 'R','{"name":"Rethwisch"}'),
(447313, 'R','{"name":"Neuenbrook"}'),
(447314, 'R','{"name":"Krempe","name:prefix":"Stadt"}'),
(447315, 'R','{"name":"Elskop"}'),
(447344, 'R','{"name":"Bahrenfleth"}'),
(447345, 'R','{"name":"Dägeling"}'),
(447346, 'R','{"name":"Grevenkop"}'),
(447347, 'R','{"name":"Krempermoor"}'),
(447348, 'R','{"name":"Kremperheide"}'),
(447349, 'R','{"name":"Krempermarsch","name:prefix":"Amt"}'),
(447770, 'R','{"name":"Wewelsfleth"}'),
(447771, 'R','{"name":"Brokdorf"}'),
(447772, 'R','{"name":"Beidenfleth"}'),
(447773, 'R','{"name":"Hodorf"}'),
(447962, 'R','{"name":"Hellschen-Heringsand-Unterschaar"}'),
(447963, 'R','{"name":"Strübbel"}'),
(447964, 'R','{"name":"Süderdeich"}'),
(447965, 'R','{"name":"Wesselburenerkoog"}'),
(447966, 'R','{"name":"Wesselburen","name:prefix":"Stadt"}'),
(447967, 'R','{"name":"Oesterwurth"}'),
(447968, 'R','{"name":"Hillgroven"}'),
(447969, 'R','{"name":"Norddeich"}'),
(447970, 'R','{"name":"Schülp"}'),
(448011, 'R','{"name":"Sankt Margarethen"}'),
(448012, 'R','{"name":"Büttel"}'),
(448013, 'R','{"name":"Kudensee"}'),
(448014, 'R','{"name":"Landscheide"}'),
(448015, 'R','{"name":"Ecklak"}'),
(448016, 'R','{"name":"Aebtissinwisch"}'),
(448018, 'R','{"name":"Neuendorf-Sachsenbande"}'),
(448019, 'R','{"name":"Nortorf"}'),
(448020, 'R','{"name":"Wilster","name:prefix":"Stadt"}'),
(448021, 'R','{"name":"Dammfleth"}'),
(448544, 'R','{"name":"Vaalermoor","name:nds":"Valermoor"}'),
(448545, 'R','{"name":"Krummendiek"}'),
(448556, 'R','{"name":"Moorhusen"}'),
(448557, 'R','{"name":"Kleve"}'),
(448558, 'R','{"name":"Nutteln","name:nds":"Nutteln"}'),
(448559, 'R','{"name":"Oldendorf"}'),
(448603, 'R','{"name":"Huje"}'),
(448604, 'R','{"name":"Mehlbek"}'),
(448605, 'R','{"name":"Kaaks"}'),
(448606, 'R','{"name":"Ottenbüttel"}'),
(448608, 'R','{"name":"Vaale","name:nds":"Vaal"}'),
(448707, 'R','{"name":"Wacken"}'),
(448708, 'R','{"name":"Heiligenstedtenerkamp"}'),
(448709, 'R','{"name":"Gribbohm"}'),
(448710, 'R','{"name":"Bekmünde"}'),
(448711, 'R','{"name":"Landrecht"}'),
(448712, 'R','{"name":"Bekdorf"}'),
(448713, 'R','{"name":"Stördorf"}'),
(448714, 'R','{"name":"Amt Wilstermarsch"}'),
(448761, 'R','{"name":"Holstenniendorf"}'),
(448762, 'R','{"name":"Besdorf"}'),
(448763, 'R','{"name":"Bokhorst"}'),
(448764, 'R','{"name":"Bokelrehm"}'),
(448765, 'R','{"name":"Agethorst"}'),
(448766, 'R','{"name":"Kaisborstel"}'),
(448767, 'R','{"name":"Hadenfeld"}'),
(448768, 'R','{"name":"Siezbüttel"}'),
(448769, 'R','{"name":"Nienbüttel"}'),
(448770, 'R','{"name":"Aasbüttel"}'),
(448771, 'R','{"name":"Warringholz"}'),
(448772, 'R','{"name":"Schenefeld"}'),
(448787, 'R','{"name":"Amt Schenefeld"}'),
(450062, 'R','{"name":"Silzen"}'),
(450063, 'R','{"name":"Peissen"}'),
(450064, 'R','{"name":"Reher"}'),
(450065, 'R','{"name":"Poyenberg"}'),
(450066, 'R','{"name":"Christinenthal"}'),
(450067, 'R','{"name":"Rade"}'),
(450068, 'R','{"name":"Hennstedt"}'),
(450069, 'R','{"name":"Sarlhusen"}'),
(450070, 'R','{"name":"Wiedenborstel"}'),
(450071, 'R','{"name":"Willenscharen"}'),
(450072, 'R','{"name":"Oldenborstel"}'),
(450073, 'R','{"name":"Puls"}'),
(450074, 'R','{"name":"Looft"}'),
(450075, 'R','{"name":"Pöschendorf"}'),
(450076, 'R','{"name":"Drage"}'),
(450214, 'R','{"name":"Lockstedt"}'),
(450215, 'R','{"name":"Oeschebüttel"}'),
(450216, 'R','{"name":"Hohenaspe"}'),
(450217, 'R','{"name":"Schlotfeld"}'),
(450218, 'R','{"name":"Hohenlockstedt"}'),
(450219, 'R','{"name":"Fitzbek"}'),
(450220, 'R','{"name":"Störkathen"}'),
(450221, 'R','{"name":"Brokstedt"}'),
(450222, 'R','{"name":"Quarnstedt"}'),
(450223, 'R','{"name":"Rosdorf"}'),
(450224, 'R','{"name":"Kellinghusen","name:prefix":"Stadt"}'),
(450225, 'R','{"name":"Mühlenbarbek"}'),
(450226, 'R','{"name":"Lohbarbek"}'),
(450227, 'R','{"name":"Winseldorf"}'),
(450228, 'R','{"name":"Amt Itzehoe-Land"}'),
(450512, 'R','{"name":"Breitenburg"}'),
(450513, 'R','{"name":"Wittenbergen"}'),
(450514, 'R','{"name":"Auufer"}'),
(450515, 'R','{"name":"Wulfsmoor"}'),
(450516, 'R','{"name":"Hingstheide"}'),
(450517, 'R','{"name":"Wrist"}'),
(450518, 'R','{"name":"Oelixdorf"}'),
(450519, 'R','{"name":"Münsterdorf"}'),
(450520, 'R','{"name":"Lägerdorf"}'),
(450521, 'R','{"name":"Kronsmoor"}'),
(450522, 'R','{"name":"Westermoor"}'),
(450523, 'R','{"name":"Breitenberg"}'),
(450524, 'R','{"name":"Moordiek"}'),
(450533, 'R','{"name":"Amt Kellinghusen"}'),
(450534, 'R','{"name":"Breitenburg","name:prefix":"Amt"}'),
(450589, 'R','{"name":"Westerrade"}'),
(450590, 'R','{"name":"Holm"}'),
(452243, 'R','{"name":"Heiligenstedten"}'),
(453328, 'R','{"name":"Kollmoor"}'),
(453723, 'R','{"name":"Bargteheide","name:nds":"Bartheil","name:prefix":"Stadt"}'),
(453724, 'R','{"name":"Jersbek"}'),
(453725, 'R','{"name":"Bargfeld-Stegen"}'),
(453726, 'R','{"name":"Nienwohld"}'),
(453727, 'R','{"name":"Elmenhorst"}'),
(453743, 'R','{"name":"Tremsbüttel"}'),
(453744, 'R','{"name":"Hammoor"}'),
(453745, 'R','{"name":"Lasbek"}'),
(453746, 'R','{"name":"Ahrensburg","name:de":"Ahrensburg","name:nds":"Ahrensborg","name:prefix":"Stadt","name:ru":"Аренсбург"}'),
(453747, 'R','{"name":"Delingsdorf"}'),
(453748, 'R','{"name":"Todendorf"}'),
(453749, 'R','{"name":"Bargteheide-Land","name:prefix":"Amt"}'),
(454193, 'R','{"name":"Siek"}'),
(454194, 'R','{"name":"Hoisdorf"}'),
(454195, 'R','{"name":"Großhansdorf","name:de":"Großhansdorf","name:nds":"Groothansdörp"}'),
(454196, 'R','{"name":"Grönwohld"}'),
(454197, 'R','{"name":"Koberg"}'),
(454198, 'R','{"name":"Köthel (Stormarn)"}'),
(454199, 'R','{"name":"Hohenfelde"}'),
(454200, 'R','{"name":"Lütjensee"}'),
(454243, 'R','{"name":"Hamfelde (Stormarn)"}'),
(454244, 'R','{"name":"Brunsbek"}'),
(454245, 'R','{"name":"Braak"}'),
(454246, 'R','{"name":"Stapelfeld"}'),
(454247, 'R','{"name":"Siek","name:prefix":"Amt"}'),
(454248, 'R','{"name":"Glinde","name:nds":"Glinn","name:prefix":"Stadt"}'),
(454249, 'R','{"name":"Witzhave"}'),
(454250, 'R','{"name":"Rausdorf"}'),
(454251, 'R','{"name":"Großensee"}'),
(454252, 'R','{"name":"Grande"}'),
(454253, 'R','{"name":"Trittau","name:prefix":"Amt"}'),
(455447, 'R','{"name":"Steinburg"}'),
(532316, 'R','{"name":"Grabau"}'),
(532317, 'R','{"name":"Rethwisch"}'),
(532318, 'R','{"name":"Wesenberg"}'),
(532319, 'R','{"name":"Meddewade"}'),
(532320, 'R','{"name":"Neritz"}'),
(532321, 'R','{"name":"Rümpel"}'),
(532322, 'R','{"name":"Travenbrück"}'),
(532323, 'R','{"name":"Westerau"}'),
(532324, 'R','{"name":"Barnitz"}'),
(532325, 'R','{"name":"Bad Oldesloe","name:nds":"Bad Oschloe","name:prefix":"Stadt","name:uk":"Бад-Ольдесло"}'),
(532326, 'R','{"name":"Pölitz"}'),
(532327, 'R','{"name":"Klein Wesenberg"}'),
(532392, 'R','{"name":"Zarpen"}'),
(532393, 'R','{"name":"Heidekamp"}'),
(532394, 'R','{"name":"Mönkhagen"}'),
(532395, 'R','{"name":"Rehhorst"}'),
(532396, 'R','{"name":"Feldhorst"}'),
(532397, 'R','{"name":"Hamberge"}'),
(532398, 'R','{"name":"Badendorf"}'),
(532399, 'R','{"name":"Bad Oldesloe-Land","name:prefix":"Amt"}'),
(532400, 'R','{"name":"Heilshoop"}'),
(532401, 'R','{"name":"Reinfeld","name:prefix":"Stadt","name:suffix":"(Holstein)"}'),
(532402, 'R','{"name":"Nordstormarn","name:prefix":"Amt"}'),
(548489, 'R','{"name":"Felm"}'),
(548490, 'R','{"name":"Osdorf"}'),
(548491, 'R','{"name":"Gettorf"}'),
(548492, 'R','{"name":"Tüttendorf"}'),
(548493, 'R','{"name":"Schinkel"}'),
(548494, 'R','{"name":"Lindau"}'),
(548495, 'R','{"name":"Neudorf-Bornstein"}'),
(548499, 'R','{"name":"Quarnbek"}'),
(548500, 'R','{"name":"Krummwisch"}'),
(548501, 'R','{"name":"Felde"}'),
(548502, 'R','{"name":"Achterwehr"}'),
(548503, 'R','{"name":"Bredenbek"}'),
(548504, 'R','{"name":"Westensee"}'),
(548507, 'R','{"name":"Rodenbek"}'),
(548508, 'R','{"name":"Schierensee"}'),
(548509, 'R','{"name":"Rumohr"}'),
(548510, 'R','{"name":"Blumenthal"}'),
(548511, 'R','{"name":"Böhnhusen"}'),
(548512, 'R','{"name":"Techelsdorf"}'),
(548515, 'R','{"name":"Brügge"}'),
(548516, 'R','{"name":"Reesdorf"}'),
(548517, 'R','{"name":"Schmalstede"}'),
(548518, 'R','{"name":"Grevenkrug"}'),
(548519, 'R','{"name":"Sören"}'),
(548520, 'R','{"name":"Hoffeld"}'),
(548546, 'R','{"name":"Emkendorf"}'),
(548547, 'R','{"name":"Groß Vollstedt"}'),
(548548, 'R','{"name":"Warder"}'),
(548549, 'R','{"name":"Langwedel"}'),
(548550, 'R','{"name":"Dätgen"}'),
(548551, 'R','{"name":"Borgdorf-Seedorf"}'),
(548552, 'R','{"name":"Eisendorf"}'),
(548553, 'R','{"name":"Ellerdorf"}'),
(548554, 'R','{"name":"Bokel"}'),
(548555, 'R','{"name":"Brammer"}'),
(548556, 'R','{"name":"Bargstedt"}'),
(548557, 'R','{"name":"Oldenhütten"}'),
(548558, 'R','{"name":"Nortorf","name:prefix":"Stadt"}'),
(548559, 'R','{"name":"Gnutz"}'),
(548560, 'R','{"name":"Timmaspe"}'),
(548563, 'R','{"name":"Bovenau"}'),
(548564, 'R','{"name":"Rade","name:suffix":"b. Rendsburg"}'),
(548565, 'R','{"name":"Schacht-Audorf"}'),
(548566, 'R','{"name":"Ostenfeld","name:suffix":"(Rendsburg)"}'),
(548567, 'R','{"name":"Haßmoor"}'),
(548568, 'R','{"name":"Schülldorf"}'),
(548569, 'R','{"name":"Osterrönfeld"}'),
(548570, 'R','{"name":"Rendsburg","name:da":"Rendsborg","name:frr":"Ransburj","name:nds":"Rendsborg","name:prefix":"Stadt","name:uk":"Рендсбурґ"}'),
(548571, 'R','{"name":"Büdelsdorf","name:prefix":"Stadt"}'),
(548572, 'R','{"name":"Rickert"}'),
(548573, 'R','{"name":"Alt Duvenstedt"}'),
(548574, 'R','{"name":"Fockbek"}'),
(548575, 'R','{"name":"Nübbel"}'),
(548577, 'R','{"name":"Westerrönfeld"}'),
(548578, 'R','{"name":"Schülp bei Rendsburg"}'),
(548579, 'R','{"name":"Jevenstedt"}'),
(548580, 'R','{"name":"Hörsten"}'),
(548581, 'R','{"name":"Hamweddel"}'),
(548582, 'R','{"name":"Haale"}'),
(548583, 'R','{"name":"Embühren"}'),
(548584, 'R','{"name":"Brinjahe"}'),
(548585, 'R','{"name":"Stafstedt"}'),
(548586, 'R','{"name":"Luhnstedt"}'),
(548589, 'R','{"name":"Heinkenborstel"}'),
(548590, 'R','{"name":"Nindorf"}'),
(548591, 'R','{"name":"Mörel"}'),
(548592, 'R','{"name":"Rade b. Hohenwestedt"}'),
(548593, 'R','{"name":"Tappendorf"}'),
(548594, 'R','{"name":"Remmels"}'),
(548595, 'R','{"name":"Hohenwestedt"}'),
(548596, 'R','{"name":"Nienborstel"}'),
(548597, 'R','{"name":"Todenbüttel"}'),
(548602, 'R','{"name":"Hanerau-Hademarschen"}'),
(548603, 'R','{"name":"Lütjenwestedt"}'),
(548604, 'R','{"name":"Tackesdorf"}'),
(548605, 'R','{"name":"Breiholz"}'),
(548606, 'R','{"name":"Sophienhamm"}'),
(548609, 'R','{"name":"Borgstedt"}'),
(548610, 'R','{"name":"Neu Duvenstedt"}'),
(548611, 'R','{"name":"Holtsee"}'),
(548612, 'R','{"name":"Ahlefeld-Bistensee"}'),
(548613, 'R','{"name":"Damendorf"}'),
(548614, 'R','{"name":"Ascheffel"}'),
(548615, 'R','{"name":"Hütten"}'),
(548616, 'R','{"name":"Osterby"}'),
(548620, 'R','{"name":"Loose"}'),
(548621, 'R','{"name":"Holzdorf","name:da":"Holttorp","name:de":"Holzdorf","name:nds":"Holzdörp"}'),
(550773, 'R','{"name":"Groß Wittensee"}'),
(550774, 'R','{"name":"Haby"}'),
(550775, 'R','{"name":"Bünsdorf"}'),
(550776, 'R','{"name":"Holzbunge"}'),
(550777, 'R','{"name":"Klein Wittensee"}'),
(550778, 'R','{"name":"Sehestedt"}'),
(552387, 'R','{"name":"Goosefeld"}'),
(552388, 'R','{"name":"Windeby"}'),
(552389, 'R','{"name":"Gammelby"}'),
(553468, 'R','{"name":"Flintbek"}'),
(553471, 'R','{"name":"Schönhorst"}'),
(553474, 'R','{"name":"Bissee"}'),
(553475, 'R','{"name":"Groß Buchwald"}'),
(553511, 'R','{"name":"Negenharrie"}'),
(553512, 'R','{"name":"Wattenbek"}'),
(553513, 'R','{"name":"Bordesholm"}'),
(553520, 'R','{"name":"Mühbrook"}'),
(553521, 'R','{"name":"Schönbek"}'),
(553522, 'R','{"name":"LOOP"}'),
(553542, 'R','{"name":"Krogaspe"}'),
(553600, 'R','{"name":"Padenstedt"}'),
(553601, 'R','{"name":"Arpsdorf"}'),
(553602, 'R','{"name":"Ehndorf"}'),
(554514, 'R','{"name":"Brodersby"}'),
(554520, 'R','{"name":"Karby"}'),
(554521, 'R','{"name":"Winnemark"}'),
(554526, 'R','{"name":"Dörphof"}'),
(554528, 'R','{"name":"Damp"}'),
(554539, 'R','{"name":"Waabs"}'),
(554540, 'R','{"name":"Barkelsby"}'),
(554562, 'R','{"name":"Altenhof"}'),
(554564, 'R','{"name":"Noer"}'),
(554637, 'R','{"name":"Schwedeneck"}'),
(554638, 'R','{"name":"Strande"}'),
(554639, 'R','{"name":"Dänischenhagen"}'),
(554747, 'R','{"name":"Altenholz"}'),
(554748, 'R','{"name":"Neuwittenbek"}'),
(554749, 'R','{"name":"Melsdorf"}'),
(554770, 'R','{"name":"Kronshagen"}'),
(554771, 'R','{"name":"Mielkendorf"}'),
(554812, 'R','{"name":"Ottendorf"}'),
(556088, 'R','{"name":"Aukrug"}'),
(556090, 'R','{"name":"Meezen"}'),
(556093, 'R','{"name":"Grauel"}'),
(556094, 'R','{"name":"Jahrsdorf"}'),
(556096, 'R','{"name":"Wapelfeld"}'),
(556097, 'R','{"name":"Osterstedt"}'),
(556099, 'R','{"name":"Beringstedt"}'),
(556100, 'R','{"name":"Seefeld"}'),
(556106, 'R','{"name":"Gokels"}'),
(556107, 'R','{"name":"Thaden"}'),
(556117, 'R','{"name":"Bendorf"}'),
(556121, 'R','{"name":"Bornholt"}'),
(556122, 'R','{"name":"Beldorf"}'),
(556139, 'R','{"name":"Steenfeld"}'),
(556140, 'R','{"name":"Oldenbüttel"}'),
(556575, 'R','{"name":"Prinzenmoor"}'),
(556576, 'R','{"name":"Hamdorf"}'),
(556602, 'R','{"name":"Elsdorf-Westermühlen"}'),
(556603, 'R','{"name":"Bargstall"}'),
(556604, 'R','{"name":"Friedrichsgraben"}'),
(556627, 'R','{"name":"Hohn"}'),
(556628, 'R','{"name":"Friedrichsholm"}'),
(556652, 'R','{"name":"Christiansholm"}'),
(556653, 'R','{"name":"Königshügel"}'),
(556654, 'R','{"name":"Lohe-Föhrden"}'),
(556667, 'R','{"name":"Owschlag"}'),
(557139, 'R','{"name":"Brekendorf"}'),
(557140, 'R','{"name":"Hummelfeld"}'),
(557141, 'R','{"name":"Thumby"}'),
(557142, 'R','{"name":"Rieseby","name:da":"Risby","name:nds":"Riesby"}'),
(557143, 'R','{"name":"Güby"}'),
(557144, 'R','{"name":"Fleckeby"}'),
(557145, 'R','{"name":"Kosel","name:da":"Koslev","name:de":"Kosel"}'),
(557582, 'R','{"name":"Molfsee"}'),
(569751, 'R','{"name":"Probstei","name:prefix":"Amt"}'),
(569759, 'R','{"name":"Schrevenborn","name:prefix":"Amt"}'),
(569760, 'R','{"name":"Selent/Schlesen","name:prefix":"Amt"}'),
(569761, 'R','{"name":"Lütjenburg","name:prefix":"Amt"}'),
(569763, 'R','{"name":"Preetz-Land","name:prefix":"Amt"}'),
(569767, 'R','{"name":"Großer Plöner See","name:prefix":"Amt"}'),
(569772, 'R','{"name":"Bokhorst-Wankendorf","name:prefix":"Amt"}'),
(904512, 'R','{"name":"Schleswig","name:ar":"اشلسويغ","name:azb":"اشلسویق","name:be":"Шле́звіг","name:ca":"Slesvig","name:ce":"Шлезвиг","name:ceb":"Schleswig","name:cs":"Šlesvik","name:da":"Slesvig","name:de":"Schleswig","name:el":"Σλέσβιχ","name:en":"Schleswig","name:eo":"Schleswig","name:es":"Schleswig","name:et":"Schleswig","name:eu":"Schleswig","name:fa":"شلسویگ","name:fi":"Schleswig","name:fr":"Schleswig","name:frr":"Slaswik","name:fy":"Sleeswyk","name:hu":"Sleswig","name:id":"Schleswig","name:IS":"Slésvík","name:it":"Schleswig","name:ja":"シュレースヴィヒ","name:ko":"슐레스비히","name:ku":"Schleswig","name:la":"Sliasvig","name:lld":"Schleswig","name:lv":"Šlēsviga","name:mk":"Шлезвиг","name:ms":"Schleswig","name:nds":"Sleeswig","name:nds-nl":"Sleeswiek","name:nl":"Sleeswijk","name:nn":"Schleswig","name:NO":"Schleswig","name:os":"Шле́звиг","name:pl":"Szlezwik","name:prefix":"Stadt","name:pt":"Eslésvico","name:ro":"Schleswig","name:ru":"Шле́звиг","name:sco":"Schleswig","name:sh":"Šlezvig","name:sq":"Sliasvig","name:sr":"Шлезвиг","name:sv":"Schleswig","name:sw":"Schleswig","name:tr":"Schleswig","name:tt":"Шлезвиг","name:uk":"Шлезвіґ","name:uz":"Schleswig","name:vi":"Schleswig","name:vo":"Schleswig","name:war":"Schleswig","name:zh":"石勒苏益格"}'),
(935099, 'R','{"name":"Marne-Nordsee","name:prefix":"Amt"}'),
(935133, 'R','{"name":"Burg-Sankt Michaelisdonn","name:prefix":"Amt"}'),
(935134, 'R','{"name":"Brunsbüttel","name:de":"Brunsbüttel","name:nds":"Bruunsbüddel","name:prefix":"Stadt"}'),
(935243, 'R','{"name":"Friedrichskoog","name:ru":"Фридрихског"}'),
(935256, 'R','{"name":"Kaiser-Wilhelm-Koog"}'),
(935261, 'R','{"name":"Neufelderkoog","name:nds":"Niefelderkoog"}'),
(935302, 'R','{"name":"Kronprinzenkoog"}'),
(935336, 'R','{"name":"Neufeld","name:nds":"Niefeld"}'),
(935360, 'R','{"name":"Ramhusen"}'),
(935386, 'R','{"name":"Volsemenhusen"}'),
(935396, 'R','{"name":"Trennewurth"}'),
(935697, 'R','{"name":"Helse"}'),
(935702, 'R','{"name":"Marnerdeich"}'),
(935724, 'R','{"name":"Marne","name:prefix":"Stadt"}'),
(935734, 'R','{"name":"Diekhusen-Fahrstedt"}'),
(935736, 'R','{"name":"Schmedeswurth"}'),
(938017, 'R','{"name":"Dingen"}'),
(938057, 'R','{"name":"Eddelak","name:nds":"Eddelak"}'),
(938073, 'R','{"name":"Averlak"}'),
(938081, 'R','{"name":"Sankt Michaelisdonn"}'),
(939487, 'R','{"name":"Frestedt"}'),
(939489, 'R','{"name":"Süderhastedt"}'),
(939497, 'R','{"name":"Eggstedt"}'),
(939498, 'R','{"name":"Hochdonn"}'),
(939795, 'R','{"name":"Großenrade"}'),
(943896, 'R','{"name":"Burg (Dithmarschen)"}'),
(943920, 'R','{"name":"Brickeln"}'),
(943923, 'R','{"name":"Quickborn"}'),
(943942, 'R','{"name":"Buchholz","name:nds":"Bookholt"}'),
(943943, 'R','{"name":"Kuden","name:nds":"Kuden"}'),
(946202, 'R','{"name":"Karolinenkoog"}'),
(949289, 'R','{"name":"Hemme","name:frr":"Heme"}'),
(949290, 'R','{"name":"Groven","name:ru":"Грофен"}'),
(949859, 'R','{"name":"Lehe"}'),
(949910, 'R','{"name":"Sankt Annen"}'),
(949911, 'R','{"name":"Schlichting"}'),
(952206, 'R','{"name":"Lunden","name:nds":"Lunnen"}'),
(952215, 'R','{"name":"Krempel"}'),
(952216, 'R','{"name":"Rehm-Flehde-Bargen"}'),
(952228, 'R','{"name":"Kleve","name:nds":"Kleev"}'),
(952291, 'R','{"name":"Hennstedt"}'),
(952358, 'R','{"name":"Wiemerstedt"}'),
(952359, 'R','{"name":"Fedderingen","name:nds":"Fellern"}'),
(952365, 'R','{"name":"Bergewöhrden"}'),
(955356, 'R','{"name":"Hollingstedt"}'),
(957877, 'R','{"name":"Wasbek"}'),
(958232, 'R','{"name":"Delve"}'),
(962280, 'R','{"name":"Aukrug"}'),
(962281, 'R','{"name":"Hägen"}'),
(962282, 'R','{"name":"Süderheistedt"}'),
(962288, 'R','{"name":"Norderheistedt"}'),
(962312, 'R','{"name":"Barkenholm"}'),
(964139, 'R','{"name":"Rederstall"}'),
(964190, 'R','{"name":"Gaushorn"}'),
(964208, 'R','{"name":"Welmbüttel"}'),
(964215, 'R','{"name":"Westerborstel"}'),
(964249, 'R','{"name":"Wallen"}'),
(964253, 'R','{"name":"Glüsing"}'),
(964256, 'R','{"name":"Linden"}'),
(964289, 'R','{"name":"Pahlen"}'),
(964294, 'R','{"name":"Schalkholz"}'),
(965558, 'R','{"name":"Hövede"}'),
(965579, 'R','{"name":"Dörpling"}'),
(966681, 'R','{"name":"Tielenhemme"}'),
(966738, 'R','{"name":"Dellstedt"}'),
(966749, 'R','{"name":"Wrohm"}'),
(966769, 'R','{"name":"Süderdorf"}'),
(966777, 'R','{"name":"Tellingstedt"}'),
(966778, 'R','{"name":"Tellingstedt"}'),
(968220, 'R','{"name":"Ravensberg"}'),
(969228, 'R','{"name":"Blücherplatz"}'),
(969563, 'R','{"name":"Schreventeich"}'),
(969718, 'R','{"name":"Neumühlen-Dietrichsdorf"}'),
(970762, 'R','{"name":"Eider","name:prefix":"Amt Kirchspielslandgemeinden"}'),
(975405, 'R','{"name":"Brunswik"}'),
(975434, 'R','{"name":"Düsternbrook"}'),
(1000196, 'R','{"name":"Wik"}'),
(1099844, 'R','{"name":"Groß Grönau"}'),
(1106363, 'R','{"name":"Tönning","name:prefix":"Stadt"}'),
(1107719, 'R','{"name":"Vollerwiek"}'),
(1107767, 'R','{"name":"Welt"}'),
(1141531, 'R','{"name":"Wenningstedt-Braderup (Sylt)","name:frr":"Woningstair-Brēderep"}'),
(1141532, 'R','{"name":"Hörnum (Sylt)","name:frr":"Hörnem"}'),
(1141533, 'R','{"name":"LIST auf Sylt","name:frr":"LIST"}'),
(1145084, 'R','{"name":"Quern","name:da":"Kværn"}'),
(1145085, 'R','{"name":"Gelting"}'),
(1145086, 'R','{"name":"Ahneby"}'),
(1145087, 'R','{"name":"Steinberg"}'),
(1145088, 'R','{"name":"Kronsgaard"}'),
(1145089, 'R','{"name":"Stangheck"}'),
(1145090, 'R','{"name":"Steinbergkirche"}'),
(1145091, 'R','{"name":"Niesgrau"}'),
(1145092, 'R','{"name":"Esgrus"}'),
(1145093, 'R','{"name":"Rabenholz"}'),
(1145094, 'R','{"name":"Pommerby"}'),
(1145095, 'R','{"name":"Nieby","name:da":"Nyby"}'),
(1145096, 'R','{"name":"Sterup"}'),
(1145348, 'R','{"name":"Hasselberg"}'),
(1145349, 'R','{"name":"Maasholm"}'),
(1145350, 'R','{"name":"Kappeln","name:ar":"كابلن","name:azb":"کاپلن","name:ce":"Каппельн","name:ceb":"Kappeln","name:da":"Kappel","name:de":"Kappeln","name:en":"Kappeln","name:eo":"Kappeln","name:es":"Kappeln","name:eu":"Kappeln","name:fa":"کاپلن","name:fi":"Kappeln","name:fr":"Kappeln","name:frr":"Kappeln","name:fy":"Kappeln","name:hu":"Kappeln","name:it":"Kappeln","name:ja":"カッペルン","name:kk":"Каппельн","name:ku":"Kappeln","name:ky":"Каппельн","name:mk":"Капелн","name:ms":"Kappeln","name:nl":"Kappeln","name:pl":"Kappeln","name:prefix":"Stadt","name:pt":"Kappeln","name:ro":"Kappeln","name:ru":"Каппельн","name:sco":"Kappeln","name:sh":"Kapeln","name:sr":"Капелн","name:sv":"Kappeln","name:sw":"Kappeln","name:tr":"Kappeln","name:tt":"Каппельн","name:tum":"Kappeln","name:uk":"Каппельн","name:uz":"Kappeln","name:vi":"Kappeln","name:war":"Kappeln","name:zh":"卡珀尔恩"}'),
(1145351, 'R','{"name":"Rabel"}'),
(1145352, 'R','{"name":"Stoltebüll"}'),
(1145353, 'R','{"name":"Arnis","name:prefix":"Stadt"}'),
(1147133, 'R','{"name":"Kampen (Sylt)","name:frr":"Kaamp"}'),
(1147134, 'R','{"name":"Sylt","name:frr":"Söl"}'),
(1147203, 'R','{"name":"Oersberg"}'),
(1147204, 'R','{"name":"Rabenkirchen-Faulück"}'),
(1147205, 'R','{"name":"Grödersby"}'),
(1149227, 'R','{"name":"Nottfeld"}'),
(1149228, 'R','{"name":"Loit"}'),
(1149229, 'R','{"name":"Ulsnis"}'),
(1149230, 'R','{"name":"Rügge","name:da":"Rygge"}'),
(1149231, 'R','{"name":"Norderbrarup"}'),
(1149232, 'R','{"name":"Böel"}'),
(1149233, 'R','{"name":"Scheggerott"}'),
(1149234, 'R','{"name":"Boren"}'),
(1149235, 'R','{"name":"Dollrottfeld"}'),
(1149236, 'R','{"name":"Kiesby"}'),
(1149237, 'R','{"name":"Mohrkirch","name:da":"Mårkær"}'),
(1149238, 'R','{"name":"Wagersrott"}'),
(1149239, 'R','{"name":"Brebel","name:da":"Bredbøl"}'),
(1149240, 'R','{"name":"Süderbrarup","name:da":"Sønder Brarup"}'),
(1149241, 'R','{"name":"Saustrup"}'),
(1149242, 'R','{"name":"Steinfeld","name:da":"Stenfelt"}'),
(1149245, 'R','{"name":"Schnarup-Thumby","name:da":"Snarup-Tumby"}'),
(1149246, 'R','{"name":"Sörup","name:da":"Sørup","name:de":"Sörup"}'),
(1149247, 'R','{"name":"Havetoftloit"}'),
(1149248, 'R','{"name":"Rüde"}'),
(1149274, 'R','{"name":"Westerholz"}'),
(1149275, 'R','{"name":"Maasbüll","name:da":"Masbøl","name:de":"Maasbüll"}'),
(1149276, 'R','{"name":"Großsolt"}'),
(1149277, 'R','{"name":"Hürup","name:da":"Hyrup","name:de":"Hürup"}'),
(1149278, 'R','{"name":"Dollerup"}'),
(1149279, 'R','{"name":"Freienwill"}'),
(1149280, 'R','{"name":"Munkbrarup"}'),
(1149281, 'R','{"name":"Grundhof"}'),
(1149282, 'R','{"name":"Ringsberg"}'),
(1149283, 'R','{"name":"Wees"}'),
(1149284, 'R','{"name":"Ausacker","name:da":"Oksager"}'),
(1149285, 'R','{"name":"Husby"}'),
(1149286, 'R','{"name":"Langballig"}'),
(1149287, 'R','{"name":"Tastrup","name:da":"Tostrup","name:de":"Tastrup"}'),
(1149288, 'R','{"name":"Glücksburg","name:da":"Lyksborg","name:de":"Glücksburg (Ostsee)","name:frr":"Loksborj","name:nds":"Glücksborg","name:prefix":"Stadt","name:suffix":"(Ostsee)"}'),
(1149293, 'R','{"name":"Oeversee"}'),
(1149294, 'R','{"name":"Sieverstedt","name:da":"Siversted","name:de":"Sieverstedt"}'),
(1149295, 'R','{"name":"Tarp"}'),
(1149296, 'R','{"name":"Harrislee","name:da":"Harreslev"}'),
(1149297, 'R','{"name":"Handewitt","name:da":"Hanved","name:de":"Handewitt","name:frr":"Hanewit"}'),
(1149333, 'R','{"name":"Taarstedt"}'),
(1149334, 'R','{"name":"Schaalby"}'),
(1149335, 'R','{"name":"Stolk","name:da":"Stollik"}'),
(1149336, 'R','{"name":"Klappholz"}'),
(1149337, 'R','{"name":"Süderfahrenstedt","name:da":"Sønder Farensted"}'),
(1149338, 'R','{"name":"Brodersby"}'),
(1149339, 'R','{"name":"Goltoft"}'),
(1149340, 'R','{"name":"Twedt"}'),
(1149341, 'R','{"name":"Havetoft"}'),
(1149342, 'R','{"name":"Uelsby"}'),
(1149343, 'R','{"name":"Struxdorf","name:da":"Strukstrup"}'),
(1149344, 'R','{"name":"Tolk"}'),
(1149458, 'R','{"name":"Neuberend"}'),
(1149459, 'R','{"name":"Nübel"}'),
(1149460, 'R','{"name":"Idstedt"}'),
(1156023, 'R','{"name":"Medelby"}'),
(1156024, 'R','{"name":"Hörup"}'),
(1156026, 'R','{"name":"Weesby"}'),
(1156027, 'R','{"name":"Schafflund"}'),
(1156028, 'R','{"name":"Osterby"}'),
(1156029, 'R','{"name":"Meyn"}'),
(1156030, 'R','{"name":"Jardelund"}'),
(1156031, 'R','{"name":"Lindewitt"}'),
(1156032, 'R','{"name":"Nordhackstedt"}'),
(1156033, 'R','{"name":"Böxlund"}'),
(1156034, 'R','{"name":"Holt"}'),
(1156035, 'R','{"name":"Wallsbüll"}'),
(1156036, 'R','{"name":"Großenwiehe"}'),
(1156124, 'R','{"name":"Jerrishoe"}'),
(1156125, 'R','{"name":"Süderhackstedt"}'),
(1156126, 'R','{"name":"Wanderup"}'),
(1156127, 'R','{"name":"Janneby"}'),
(1156128, 'R','{"name":"Langstedt"}'),
(1156129, 'R','{"name":"Eggebek"}'),
(1156130, 'R','{"name":"Sollerup"}'),
(1156131, 'R','{"name":"Jörl"}'),
(1156149, 'R','{"name":"Bollingstedt"}'),
(1156150, 'R','{"name":"Schafflund","name:prefix":"Amt"}'),
(1156151, 'R','{"name":"Treia"}'),
(1156152, 'R','{"name":"Hüsby"}'),
(1156153, 'R','{"name":"Eggebek","name:prefix":"Amt"}'),
(1156154, 'R','{"name":"Ellingstedt"}'),
(1156155, 'R','{"name":"Hürup","name:prefix":"Amt"}'),
(1156156, 'R','{"name":"Oeversee","name:prefix":"Amt"}'),
(1156157, 'R','{"name":"Hollingstedt"}'),
(1156158, 'R','{"name":"Silberstedt"}'),
(1156159, 'R','{"name":"Langballig","name:prefix":"Amt"}'),
(1156160, 'R','{"name":"Arensharde","name:prefix":"Amt"}'),
(1156161, 'R','{"name":"Lürschau"}'),
(1156162, 'R','{"name":"Schuby"}'),
(1156163, 'R','{"name":"Jübek"}'),
(1157529, 'R','{"name":"Lottorf"}'),
(1157530, 'R','{"name":"Geltorf"}'),
(1157531, 'R','{"name":"Selk"}'),
(1157532, 'R','{"name":"Busdorf"}'),
(1157533, 'R','{"name":"Jagel"}'),
(1157534, 'R','{"name":"Fahrdorf"}'),
(1157535, 'R','{"name":"Borgwedel"}'),
(1157536, 'R','{"name":"Dannewerk"}'),
(1157539, 'R','{"name":"Groß Rheide"}'),
(1157540, 'R','{"name":"Haddeby","name:prefix":"Amt"}'),
(1157541, 'R','{"name":"Klein Rheide"}'),
(1157542, 'R','{"name":"Alt Bennebek"}'),
(1157656, 'R','{"name":"Dörpstedt"}'),
(1157657, 'R','{"name":"Wohlde"}'),
(1157658, 'R','{"name":"Börm"}'),
(1157659, 'R','{"name":"Klein Bennebek"}'),
(1157799, 'R','{"name":"Kropp-Stapelholm","name:prefix":"Amt"}'),
(1157800, 'R','{"name":"Bergenhusen"}'),
(1157801, 'R','{"name":"Tetenhusen"}'),
(1157802, 'R','{"name":"Meggerdorf"}'),
(1157803, 'R','{"name":"Süderstapel"}'),
(1157804, 'R','{"name":"Erfde"}'),
(1157805, 'R','{"name":"Norderstapel","name:da":"Nørre Stabel","name:de":"Norderstapel"}'),
(1157806, 'R','{"name":"Tielen"}'),
(1157846, 'R','{"name":"Geltinger Bucht","name:prefix":"Amt"}'),
(1157847, 'R','{"name":"Kappeln-Land","name:prefix":"Amt"}'),
(1157848, 'R','{"name":"Süderbrarup","name:prefix":"Amt"}'),
(1157849, 'R','{"name":"Südangeln","name:prefix":"Amt"}'),
(1157850, 'R','{"name":"Mittelangeln","name:prefix":"Amt"}'),
(1157962, 'R','{"name":"Helgoland","name:de":"Helgoland","name:frr":"deät Lun"}'),
(1175544, 'R','{"name":"Böklund","name:da":"Bøglund","name:de":"Böklund"}'),
(1185946, 'R','{"name":"Ekenis"}'),
(1185964, 'R','{"name":"Satrup"}'),
(1187305, 'R','{"name":"Schülp bei Nortorf"}'),
(1187306, 'R','{"name":"Kropp","name:da":"Krop"}'),
(1319978, 'R','{"name":"Region Syddanmark","name:bg":"Южна Дания","name:br":"Danmark ar Su","name:ca":"Dinamarca Meridional","name:ce":"Къилбера Дани","name:cs":"Syddanmark","name:da":"Region Syddanmark","name:de":"Region Süddänemark","name:en":"Region OF Southern Denmark","name:eo":"Regiono Suda Danio","name:es":"Dinamarca Meridional","name:et":"Lõuna-Taani piirkond","name:eu":"Hegoaldeko Danimarka","name:fa":"استان سیددانمارک","name:fi":"Etelä-Tanskan alue","name:fr":"Danemark-du-Sud","name:frr":"Regiuun Syddanmark","name:fy":"Súd-Denemark","name:hr":"Južna Danska","name:hu":"Dél-Dánia régió","name:hy":"Հարավային Դանիա տարածաշրջան","name:it":"Danimarca meridionale","name:ja":"南デンマーク地域","name:ka":"სამხრეთ დანიის რეგიონი","name:kk":"Оңтүстік Дания","name:ko":"남덴마크 지역","name:la":"Dania Meridiana","name:lt":"Pietų Danijos regionas","name:lv":"Dienviddānijas reģions","name:mk":"Јужна Данска","name:ms":"Wilayah Syddanmark","name:nds":"Region Süüddäänmark","name:nl":"Zuid-Denemarken","name:oc":"Danemarc Meridional","name:os":"Хуссар Дани","name:pl":"Dania Południowa","name:pt":"Dinamarca DO Sul","name:ro":"Regiunea Syddanmark","name:ru":"Южная Дания","name:sco":"Region o Soothren Denmark","name:se":"Syddanmark regiuvdna","name:sk":"Južné Dánsko","name:sr":"Јужна Данска","name:uk":"Південна Данія","name:vi":"Nam Đan Mạch","name:zh":"南丹麦大区"}'),
(1388559, 'R','{"name":"Artlenburg","name:nds":"Addelborg","name:prefix":"Flecken"}'),
(1395342, 'R','{"name":"Tating"}'),
(1395343, 'R','{"name":"Grothusenkoog","name:ru":"Гротузенког"}'),
(1395344, 'R','{"name":"Tümlauer Koog"}'),
(1395345, 'R','{"name":"Sankt Peter-Ording"}'),
(1395346, 'R','{"name":"Westerhever"}'),
(1395415, 'R','{"name":"Poppenbüll"}'),
(1395416, 'R','{"name":"Osterhever"}'),
(1397061, 'R','{"name":"Oldenswort"}'),
(1397062, 'R','{"name":"Norderfriedrichskoog"}'),
(1397063, 'R','{"name":"Tetenbüll"}'),
(1402693, 'R','{"name":"Kotzenbüll","name:ru":"Котценбюлль"}'),
(1402694, 'R','{"name":"Kirchspiel Garding"}'),
(1402695, 'R','{"name":"Katharinenheerd"}'),
(1402696, 'R','{"name":"Garding","name:frr":"Gaarding","name:prefix":"Stadt"}'),
(1402820, 'R','{"name":"Simonsberg"}'),
(1402821, 'R','{"name":"Uelvesbüll"}'),
(1402987, 'R','{"name":"Friedrichstadt","name:da":"Frederiksstad","name:frr":"Freedaistää","name:nl":"Frederikstad aan de Eider","name:prefix":"Stadt","name:ru":"Фридрихштадт (Германия)"}'),
(1402988, 'R','{"name":"Drage"}'),
(1402989, 'R','{"name":"Seeth"}'),
(1402990, 'R','{"name":"Witzwort"}'),
(1403981, 'R','{"name":"Süderhöft"}'),
(1403982, 'R','{"name":"Hude"}'),
(1403983, 'R','{"name":"Fresendelf"}'),
(1404024, 'R','{"name":"Schwabstedt"}'),
(1404153, 'R','{"name":"Winnert"}'),
(1404154, 'R','{"name":"Ostenfeld (Husum)"}'),
(1404155, 'R','{"name":"Wittbek"}'),
(1405166, 'R','{"name":"Ramstedt"}'),
(1405167, 'R','{"name":"Oldersbek"}'),
(1405168, 'R','{"name":"Wisch"}'),
(1405169, 'R','{"name":"Koldenbüttel","name:da":"Koldenbyttel","name:frr":"Koolnbütel","name:nds":"Kombüddel"}'),
(1405170, 'R','{"name":"Südermarsch"}'),
(1405171, 'R','{"name":"Rantrum"}'),
(1405172, 'R','{"name":"Mildstedt"}'),
(1405430, 'R','{"name":"Hattstedt"}'),
(1405431, 'R','{"name":"Wobbenbüll"}'),
(1405432, 'R','{"name":"Husum","name:an":"Husum","name:ar":"هوسوم","name:arz":"هوسوم","name:be":"Хузум","name:bg":"Хузум","name:bs":"Husum","name:ca":"Husum","name:ce":"Хузум","name:ceb":"Husum","name:cs":"Husum","name:da":"Husum","name:de":"Husum","name:en":"Husum","name:eo":"Husum","name:es":"Husum","name:eu":"Husum","name:fa":"هوسوم","name:fi":"Husum","name:fr":"Husum","name:frr":"Hüsem","name:fy":"Hüsem","name:glk":"هۊسۊم","name:hu":"Husum","name:it":"Husum","name:kk":"Хузум","name:ku":"Husum","name:ky":"Хузум","name:lt":"Huzumas","name:mk":"Хузум","name:ms":"Husum","name:nds":"Husum","name:nl":"Husum","name:nn":"Husum","name:NO":"Husum","name:os":"Хузум","name:pl":"Husum","name:prefix":"Stadt","name:pt":"Husum","name:ro":"Husum","name:ru":"Хузум","name:sh":"Husum","name:sr":"Хусум","name:sv":"Husum","name:sw":"Husum","name:tr":"Husum","name:tt":"Хузум","name:tum":"Husum","name:uk":"Гузум","name:uz":"Husum","name:vi":"Husum","name:vo":"Husum","name:war":"Husum","name:zh":"胡苏姆"}'),
(1405506, 'R','{"name":"Arlewatt"}'),
(1405507, 'R','{"name":"Bohmstedt"}'),
(1405508, 'R','{"name":"Horstedt"}'),
(1405509, 'R','{"name":"Elisabeth-Sophien-Koog","name:frr":"Eliisabeth-Sofiien-Kuuch"}'),
(1406816, 'R','{"name":"Ahrenshöft","name:frr":"Oornshaud"}'),
(1406817, 'R','{"name":"Olderup"}'),
(1406818, 'R','{"name":"Wester-Ohrstedt"}'),
(1406819, 'R','{"name":"Schwesing"}'),
(1406825, 'R','{"name":"Oster-Ohrstedt"}'),
(1406976, 'R','{"name":"Immenstedt"}'),
(1406977, 'R','{"name":"Ahrenviölfeld"}'),
(1406978, 'R','{"name":"Viöl"}'),
(1406979, 'R','{"name":"Ahrenviöl"}'),
(1406980, 'R','{"name":"Behrendorf"}'),
(1406981, 'R','{"name":"Bondelum","name:frr":"Bonlem"}'),
(1415323, 'R','{"name":"Sollwitt"}'),
(1415324, 'R','{"name":"Löwenstedt"}'),
(1415325, 'R','{"name":"Haselund"}'),
(1415326, 'R','{"name":"Norstedt"}'),
(1415598, 'R','{"name":"Kolkerheide"}'),
(1415599, 'R','{"name":"Struckum"}'),
(1415601, 'R','{"name":"Hattstedtermarsch","name:frr":"Haatstinger Määrsch"}'),
(1415602, 'R','{"name":"Drelsdorf"}'),
(1415663, 'R','{"name":"Goldelund"}'),
(1415664, 'R','{"name":"Lütjenholm"}'),
(1415665, 'R','{"name":"Goldebek"}'),
(1415666, 'R','{"name":"Joldelund"}'),
(1416728, 'R','{"name":"Bordelum"}'),
(1416729, 'R','{"name":"Sönnebüll"}'),
(1416730, 'R','{"name":"Vollstedt"}'),
(1416732, 'R','{"name":"Bredstedt","name:de":"Bredstedt","name:frr":"Bräist","name:prefix":"Stadt"}'),
(1416733, 'R','{"name":"Högel"}'),
(1416813, 'R','{"name":"Langenhorn","name:da":"Langhorn","name:frr":"e Hoorne"}'),
(1416814, 'R','{"name":"Bargum"}'),
(1416815, 'R','{"name":"Reußenköge"}'),
(1416816, 'R','{"name":"Ockholm","name:da":"Okholm","name:de":"Ockholm","name:frr":"e Hoolme"}'),
(1416962, 'R','{"name":"Enge-Sande"}'),
(1416963, 'R','{"name":"Stadum"}'),
(1416964, 'R','{"name":"Sprakebüll"}'),
(1416965, 'R','{"name":"Stedesand"}'),
(1417210, 'R','{"name":"Risum-Lindholm"}'),
(1417211, 'R','{"name":"Lexgaard"}'),
(1417212, 'R','{"name":"Ladelund"}'),
(1417213, 'R','{"name":"Leck"}'),
(1417214, 'R','{"name":"Achtrup","name:frr":"Åktoorp"}'),
(1417215, 'R','{"name":"Bramstedtlund","name:frr":"Braamstäälönj"}'),
(1417216, 'R','{"name":"Tinningstedt"}'),
(1418483, 'R','{"name":"Ellhöft","name:frr":"Älhood"}'),
(1418484, 'R','{"name":"Karlum"}'),
(1418485, 'R','{"name":"Süderlügum"}'),
(1418486, 'R','{"name":"Westre"}'),
(1418654, 'R','{"name":"Aventoft","name:frr":"Oowentoft"}'),
(1418655, 'R','{"name":"Rodenäs","name:da":"Rødenæs"}'),
(1418656, 'R','{"name":"Klanxbüll","name:da":"Klangsbøl","name:de":"Klanxbüll","name:frr":"Klangsbel"}'),
(1418657, 'R','{"name":"Friedrich-Wilhelm-Lübke-Koog","name:frr":"Friedrich-Wilhelm-Lübke-Kuuch"}'),
(1418942, 'R','{"name":"Humptrup","name:frr":"Humptoorp"}'),
(1418943, 'R','{"name":"Neukirchen","name:da":"Nykirke","name:de":"Neukirchen","name:frr":"Naischöspel"}'),
(1420391, 'R','{"name":"Uphusum"}'),
(1420392, 'R','{"name":"Bosbüll","name:frr":"Bousbel"}'),
(1420393, 'R','{"name":"Klixbüll"}'),
(1420394, 'R','{"name":"Holm"}'),
(1420395, 'R','{"name":"Braderup"}'),
(1420450, 'R','{"name":"Dagebüll","name:da":"Dagebøl","name:de":"Dagebüll","name:frr":"Doogebel"}'),
(1420451, 'R','{"name":"Galmsbüll","name:frr":"Galmsbel"}'),
(1420452, 'R','{"name":"Emmelsbüll-Horsbüll","name:frr":"Ämesbel"}'),
(1420553, 'R','{"name":"Langeneß","name:frr":"de Nees"}'),
(1420554, 'R','{"name":"Hallig Hooge"}'),
(1420555, 'R','{"name":"Nordstrand"}'),
(1420556, 'R','{"name":"Pellworm","name:frr":"Pelweerm"}'),
(1420557, 'R','{"name":"Gröde","name:frr":"de Grööe"}'),
(1428482, 'R','{"name":"Niebüll","name:ar":"نيبول","name:azb":"نیبول","name:ca":"Niebüll","name:ce":"Нибуьлль","name:ceb":"Niebüll","name:da":"Nibøl","name:de":"Niebüll","name:el":"Νίμπουλ","name:en":"Niebüll","name:eo":"Niebüll","name:es":"Niebüll","name:eu":"Niebüll","name:fa":"نیبول","name:fr":"Niebüll","name:frr":"Naibel","name:fy":"Niebüll","name:hu":"Niebüll","name:it":"Niebüll","name:kk":"Нибюлль","name:ku":"Niebüll","name:ky":"Нибюлль","name:lld":"Niebüll","name:mk":"Нибил","name:ms":"Niebüll","name:nl":"Niebüll","name:nn":"Niebüll","name:NO":"Niebüll","name:pl":"Niebüll","name:prefix":"Stadt","name:pt":"Niebüll","name:ro":"Niebüll","name:ru":"Нибюлль","name:sh":"Nibil","name:sr":"Нибил","name:sv":"Niebüll","name:tr":"Niebüll","name:tt":"Нибюлль","name:tum":"Niebüll","name:uk":"Нібюль","name:uz":"Niebüll","name:vi":"Niebüll","name:war":"Niebüll","name:zh":"尼比尔"}'),
(1428584, 'R','{"name":"Dunsum","name:frr":"Dunsem"}'),
(1428585, 'R','{"name":"Wittdün auf Amrum"}'),
(1428586, 'R','{"name":"Midlum","name:frr":"Madlem"}'),
(1428587, 'R','{"name":"Süderende","name:frr":"Söleraanj"}'),
(1428588, 'R','{"name":"Alkersum","name:frr":"Aalkersem"}'),
(1428589, 'R','{"name":"Föhr-Amrum","name:prefix":"Amt"}'),
(1428590, 'R','{"name":"Wyk auf Föhr","name:frr":"A Wik","name:prefix":"Stadt"}'),
(1428591, 'R','{"name":"Nieblum","name:frr":"Njiblem"}'),
(1428592, 'R','{"name":"Norddorf auf Amrum","name:de":"Norddorf auf Amrum","name:frr":"Noorsaarep üüb Oomram"}'),
(1428593, 'R','{"name":"Wrixum","name:frr":"Wraksem"}'),
(1428594, 'R','{"name":"Borgsum","name:frr":"Borigsem"}'),
(1428595, 'R','{"name":"Oevenum","name:frr":"Ööwenem"}'),
(1428596, 'R','{"name":"Utersum","name:frr":"Ödersem"}'),
(1428597, 'R','{"name":"Nebel","name:de":"Nebel","name:frr":"Neebel"}'),
(1428598, 'R','{"name":"Witsum","name:frr":"Wiisem"}'),
(1428599, 'R','{"name":"Oldsum","name:frr":"Olersem"}'),
(1428696, 'R','{"name":"Landschaft Sylt"}'),
(1444121, 'R','{"name":"Flintbek","name:prefix":"Amt"}'),
(1444880, 'R','{"name":"Schlagsdorf"}'),
(1444883, 'R','{"name":"Dechow"}'),
(1444890, 'R','{"name":"Kneese"}'),
(1444894, 'R','{"name":"Gadebusch","name:prefix":"Amt"}'),
(1444907, 'R','{"name":"Roggendorf"}'),
(1444915, 'R','{"name":"Utecht"}'),
(1444916, 'R','{"name":"Rehna","name:prefix":"Amt"}'),
(1445657, 'R','{"name":"Amt Nortorfer Land"}'),
(1445659, 'R','{"name":"Hüttener Berge","name:prefix":"Amt"}'),
(1445660, 'R','{"name":"Bordesholm","name:prefix":"Amt"}'),
(1445661, 'R','{"name":"Molfsee","name:prefix":"Amt"}'),
(1445662, 'R','{"name":"Amt Achterwehr"}'),
(1445742, 'R','{"name":"Dänischer Wohld","name:prefix":"Amt"}'),
(1445743, 'R','{"name":"Amt Jevenstedt"}'),
(1445744, 'R','{"name":"Schlei-Ostsee","name:prefix":"Amt"}'),
(1445745, 'R','{"name":"Fockbek","name:prefix":"Amt"}'),
(1445746, 'R','{"name":"Dänischenhagen","name:prefix":"Amt"}'),
(1445747, 'R','{"name":"Hohner Harde","name:prefix":"Amt"}'),
(1451516, 'R','{"name":"Zarrentin","name:prefix":"Amt"}'),
(1451524, 'R','{"name":"Lüttow-Valluhn"}'),
(1451534, 'R','{"name":"Boizenburg-Land","name:prefix":"Amt"}'),
(1451542, 'R','{"name":"Greven"}'),
(1451545, 'R','{"name":"Zarrentin am Schaalsee","name:prefix":"Stadt"}'),
(1451561, 'R','{"name":"Nostorf"}'),
(1451586, 'R','{"name":"Schwanheide"}'),
(1451600, 'R','{"name":"Gallin"}'),
(1451605, 'R','{"name":"Gresse"}'),
(1463073, 'R','{"name":"Siebenbäumen"}'),
(1463074, 'R','{"name":"Schürensöhlen"}'),
(1463075, 'R','{"name":"Krummesse"}'),
(1463076, 'R','{"name":"Grinau"}'),
(1463077, 'R','{"name":"Bliestorf"}'),
(1463078, 'R','{"name":"Groß Schenkenberg"}'),
(1463289, 'R','{"name":"Groß Boden"}'),
(1463290, 'R','{"name":"Steinhorst"}'),
(1463291, 'R','{"name":"Lüchow"}'),
(1463292, 'R','{"name":"Stubben"}'),
(1463293, 'R','{"name":"Sierksrade"}'),
(1463294, 'R','{"name":"Düchelsdorf"}'),
(1463295, 'R','{"name":"Klinkrade"}'),
(1463296, 'R','{"name":"Kastorf"}'),
(1464769, 'R','{"name":"Schiphorst"}'),
(1464771, 'R','{"name":"Schönberg"}'),
(1464774, 'R','{"name":"Sandesneben"}'),
(1464775, 'R','{"name":"Wentorf (Amt Sandesneben)"}'),
(1464848, 'R','{"name":"Kühsen"}'),
(1464849, 'R','{"name":"Niendorf bei Berkenthin"}'),
(1464851, 'R','{"name":"Göldenitz"}'),
(1465815, 'R','{"name":"Duvensee"}'),
(1465816, 'R','{"name":"Linau"}'),
(1465817, 'R','{"name":"Walksfelde"}'),
(1465972, 'R','{"name":"Ritzerau"}'),
(1465973, 'R','{"name":"Niendorf/Stecknitz"}'),
(1465974, 'R','{"name":"Panten"}'),
(1465975, 'R','{"name":"Köthel (Lauenburg)"}'),
(1465976, 'R','{"name":"Poggensee"}'),
(1465977, 'R','{"name":"Breitenfelde"}'),
(1465978, 'R','{"name":"Nusse"}'),
(1465979, 'R','{"name":"Bälau"}'),
(1466067, 'R','{"name":"Schretstaken"}'),
(1466068, 'R','{"name":"Kuddewörde"}'),
(1466069, 'R','{"name":"Mühlenrade"}'),
(1466070, 'R','{"name":"Kasseburg"}'),
(1466071, 'R','{"name":"Dahmker"}'),
(1466072, 'R','{"name":"Hamfelde (Lauenburg)"}'),
(1466109, 'R','{"name":"Möhnsen"}'),
(1466110, 'R','{"name":"Fuhlenhagen"}'),
(1466111, 'R','{"name":"Talkau"}'),
(1466112, 'R','{"name":"Havekost"}'),
(1466113, 'R','{"name":"Basthorst"}'),
(1466177, 'R','{"name":"Woltersdorf"}'),
(1466178, 'R','{"name":"Hornbek"}'),
(1466179, 'R','{"name":"Güster"}'),
(1466180, 'R','{"name":"Tramm"}'),
(1467345, 'R','{"name":"Kankelau"}'),
(1467346, 'R','{"name":"Alt Mölln"}'),
(1467347, 'R','{"name":"Grove"}'),
(1467348, 'R','{"name":"Elmenhorst"}'),
(1467349, 'R','{"name":"Roseburg"}'),
(1467511, 'R','{"name":"Siebeneichen"}'),
(1467512, 'R','{"name":"Klein Pampau"}'),
(1467513, 'R','{"name":"Sahms"}'),
(1467514, 'R','{"name":"Groß Pampau"}'),
(1467715, 'R','{"name":"Schulendorf"}'),
(1467718, 'R','{"name":"Wangelau"}'),
(1467719, 'R','{"name":"Müssen"}'),
(1467752, 'R','{"name":"Dalldorf"}'),
(1467753, 'R','{"name":"Witzeeze"}'),
(1467754, 'R','{"name":"Basedow"}'),
(1467755, 'R','{"name":"Lütau"}'),
(1469077, 'R','{"name":"Gülzow"}'),
(1469078, 'R','{"name":"Lauenburg/Elbe","name:ar":"لونبورغ","name:az-Arab":"لونبورگ","name:azb":"لونبورگ","name:de":"Lauenburg/Elbe","name:fa":"لونبورگ","name:nds":"Loonborg","name:prefix":"Stadt"}'),
(1469079, 'R','{"name":"Krüzen"}'),
(1469080, 'R','{"name":"Lanze"}'),
(1469081, 'R','{"name":"Kollow"}'),
(1469082, 'R','{"name":"Schnakenbek"}'),
(1469083, 'R','{"name":"Juliusburg"}'),
(1469084, 'R','{"name":"Buchhorst"}'),
(1469085, 'R','{"name":"Schwarzenbek","name:nds":"Swattenbeek","name:prefix":"Stadt"}'),
(1469086, 'R','{"name":"Grabau"}'),
(1469087, 'R','{"name":"Krukow"}'),
(1469171, 'R','{"name":"Labenz"}'),
(1469172, 'R','{"name":"Sirksfelde"}'),
(1469173, 'R','{"name":"Borstorf"}'),
(1469214, 'R','{"name":"Rondeshagen"}'),
(1469215, 'R','{"name":"Klempau"}'),
(1469216, 'R','{"name":"Groß Sarau"}'),
(1469262, 'R','{"name":"Pogeez"}'),
(1469263, 'R','{"name":"Buchholz"}'),
(1469264, 'R','{"name":"Einhaus"}'),
(1469272, 'R','{"name":"Groß Disnack"}'),
(1469273, 'R','{"name":"Berkenthin"}'),
(1470110, 'R','{"name":"Mechow"}'),
(1470111, 'R','{"name":"Römnitz"}'),
(1470112, 'R','{"name":"Bäk"}'),
(1470174, 'R','{"name":"Kulpin"}'),
(1470175, 'R','{"name":"Behlendorf"}'),
(1470176, 'R','{"name":"Harmsdorf"}'),
(1471097, 'R','{"name":"Giesensdorf"}'),
(1471098, 'R','{"name":"Schmilau"}'),
(1471099, 'R','{"name":"Fredeburg"}'),
(1471100, 'R','{"name":"Ratzeburg","name:prefix":"Stadt"}'),
(1471101, 'R','{"name":"Lankau"}'),
(1471102, 'R','{"name":"Albsfelde"}'),
(1471554, 'R','{"name":"Brunsmark"}'),
(1473800, 'R','{"name":"Besenthal"}'),
(1473801, 'R','{"name":"Lehmrade"}'),
(1473802, 'R','{"name":"Göttin"}'),
(1473804, 'R','{"name":"Grambek"}'),
(1473805, 'R','{"name":"Horst"}'),
(1474024, 'R','{"name":"Gudow"}'),
(1475323, 'R','{"name":"Fitzen"}'),
(1476083, 'R','{"name":"Büchen"}'),
(1476084, 'R','{"name":"Langenlehsten"}'),
(1476085, 'R','{"name":"Bröthen"}'),
(1476156, 'R','{"name":"Klein Zecher"}'),
(1476157, 'R','{"name":"Salem"}'),
(1476158, 'R','{"name":"Sterley"}'),
(1476159, 'R','{"name":"Hollenbek"}'),
(1476160, 'R','{"name":"Seedorf"}'),
(1476161, 'R','{"name":"Ziethen"}'),
(1476162, 'R','{"name":"Mustin"}'),
(1476163, 'R','{"name":"Kittlitz"}'),
(1477861, 'R','{"name":"Selmsdorf"}'),
(1478016, 'R','{"name":"Dassow","name:prefix":"Stadt"}'),
(1479256, 'R','{"name":"Schönberger Land","name:prefix":"Amt"}'),
(1479258, 'R','{"name":"Lüdersdorf"}'),
(1529660, 'R','{"name":"Ostholstein-Mitte","name:prefix":"Amt"}'),
(1529661, 'R','{"name":"Oldenburg-Land","name:prefix":"Amt"}'),
(1529662, 'R','{"name":"Lensahn","name:prefix":"Amt"}'),
(1541874, 'R','{"name":"Lauenburgische Seen","name:prefix":"Amt"}'),
(1541875, 'R','{"name":"Lütau","name:prefix":"Amt"}'),
(1541876, 'R','{"name":"Büchen","name:prefix":"Amt"}'),
(1541877, 'R','{"name":"Schwarzenbek-Land","name:prefix":"Amt"}'),
(1541878, 'R','{"name":"Berkenthin","name:prefix":"Amt"}'),
(1541879, 'R','{"name":"Sandesneben-Nusse","name:prefix":"Amt"}'),
(1541880, 'R','{"name":"Breitenfelde","name:prefix":"Amt"}'),
(1567454, 'R','{"name":"Almdorf"}'),
(1571382, 'R','{"name":"Exerzierplatz"}'),
(1571761, 'R','{"name":"Breklum"}'),
(1572474, 'R','{"name":"Rönne","name:lt":"Rionė"}'),
(1573497, 'R','{"name":"Nordsee-Treene","name:prefix":"Amt"}'),
(1573498, 'R','{"name":"Pellworm","name:prefix":"Amt"}'),
(1573499, 'R','{"name":"Eiderstedt","name:ca":"Eiderstedt","name:da":"Ejdersted","name:de":"Eiderstedt","name:en":"Eiderstedt","name:eo":"Eiderstedt","name:fa":"ایدرشتدت","name:fr":"Eiderstedt","name:frr":"Eidersteed","name:it":"Eiderstedt","name:mk":"Ајдерштет","name:ms":"Eiderstedt","name:nan":"Eiderstedt","name:nl":"Eiderstedt","name:pl":"Eiderstedt","name:prefix":"Amt","name:ru":"Айдерштедт"}'),
(1574208, 'R','{"name":"Südtondern","name:prefix":"Amt"}'),
(1574209, 'R','{"name":"Mittleres Nordfriesland","name:prefix":"Amt"}'),
(1574210, 'R','{"name":"Viöl","name:de":"Viöl","name:prefix":"Amt"}'),
(1604264, 'R','{"name":"Mölln","name:prefix":"Stadt"}'),
(1670981, 'R','{"name":"Eiderkanal","name:prefix":"Amt"}'),
(1739380, 'R','{"name":"Nordwestmecklenburg","name:fr":"Mecklembourg-du-Nord-Ouest","name:prefix":"Landkreis","name:ru":"Северозападный Мекленбург","name:suffix:en":"(district)","name:suffix:fr":"(arrondissement)"}'),
(1739381, 'R','{"name":"Ludwigslust-Parchim","name:de":"Ludwigslust-Parchim","name:prefix":"Landkreis"}'),
(1810981, 'R','{"name":"Muxall"}'),
(1928466, 'R','{"name":"Aabenraa Kommune","name:ca":"Municipi D Aabenraa","name:da":"Aabenraa Kommune","name:de":"Kommune Apenrade","name:en":"Aabenraa Municipality","name:et":"Åbenrå vald","name:fi":"Aabenraan kunta","name:frr":"Aabenraa Komuun","name:hr":"Općina Aabenraa","name:hu":"Aabenraa község","name:id":"Munisipalitas Aabenraa","name:nds":"Kommun Openraa","name:no":"Aabenraa kommune","name:pl":"Gmina Aabenraa"}'),
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

