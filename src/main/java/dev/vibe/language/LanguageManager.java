package dev.vibe.language;

import dev.vibe.Vibe;
import dev.vibe.module.impl.LanguageModule;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small, dependency-free translation catalog for Vibe-owned interface text.
 * Raw module/setting names remain stable in JSON profiles; only their displayed
 * labels pass through this service.
 */
public final class LanguageManager {

    private static final Map<String, Map<String, String>> CATALOG = new HashMap<String, Map<String, String>>();
    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*");
    private static final List<String> LANGUAGES = Collections.unmodifiableList(Arrays.asList(
            "English", "Chinese", "Russian", "Japanese", "Bavarian",
            "Finnish", "Swedish", "Greek", "Spanish", "German", "French", "Enchantment Table",
            "Portuguese", "Ukrainian", "Hindi", "Standard Arabic", "Bengali", "Indonesian", "Urdu",
            "Nigerian Pidgin", "Egyptian Arabic", "Marathi", "Vietnamese", "Telugu", "Swahili", "Hausa",
            "Turkish", "Western Punjabi", "Tagalog", "Tamil", "Iranian Persian", "Korean", "Amharic",
            "Thai", "Javanese", "Italian", "Gujarati", "Dutch", "Nepali", "Czech", "Polish", "Zulu",
            "Romanian", "Aurebesh"));

    static {
        add("Chinese", pairs(
                "Language", "语言", "Choose Vibe's interface language", "选择 Vibe 界面语言",
                "ClickGUI", "点击界面", "MoveFix", "移动修正", "Mode", "模式", "English", "英语", "Chinese", "中文", "Russian", "俄语", "Japanese", "日语", "Bavarian", "巴伐利亚语",
                "Silent", "静默", "Strict", "严格", "Visual", "可视", "Return To Origin Speed", "回正速度",
                "Continue", "继续", "Shader settings", "着色器设置", "Vibe Shader", "Vibe 着色器", "Vibe Discord", "Vibe Discord",
                "WELCOME TO VIBE", "欢迎来到 VIBE", "Choose the local gamertag Vibe will display.", "选择 Vibe 显示的本地游戏名。",
                "Back", "返回", "Save", "保存", "Load", "加载", "Delete", "删除", "Search", "搜索", "Settings", "设置",
                "Enabled", "启用", "Disabled", "禁用", "ON", "开", "OFF", "关", "selected", "已选择", "Combat", "战斗", "Visual", "视觉", "Movement", "移动", "World", "世界", "Meme", "梗图", "Client", "客户端", "Scripts", "脚本", "Killaura", "杀戮光环", "ItemESP", "物品透视", "BlockOverlay", "方块覆盖", "AutoTool", "自动工具", "FastBreak", "快速挖掘", "CustomCrosshair", "自定义准星", "Rotate Back Speed", "回转速度", "Raycast", "射线检测", "Outline", "轮廓", "Fill", "填充", "Static", "静态", "Fade", "渐变", "Rainbow", "彩虹"));
        add("Russian", pairs(
                "Language", "Язык", "Choose Vibe's interface language", "Выберите язык интерфейса Vibe",
                "ClickGUI", "КликGUI", "MoveFix", "Коррекция движения", "Mode", "Режим", "English", "Английский", "Chinese", "Китайский", "Russian", "Русский", "Japanese", "Японский", "Bavarian", "Баварский",
                "Silent", "Тихий", "Strict", "Строгий", "Visual", "Визуальный", "Return To Origin Speed", "Скорость возврата",
                "Continue", "Продолжить", "Shader settings", "Настройки шейдера", "Vibe Shader", "Шейдер Vibe", "Vibe Discord", "Vibe Discord",
                "WELCOME TO VIBE", "ДОБРО ПОЖАЛОВАТЬ В VIBE", "Choose the local gamertag Vibe will display.", "Выберите локальный ник Vibe.",
                "Back", "Назад", "Save", "Сохранить", "Load", "Загрузить", "Delete", "Удалить", "Search", "Поиск", "Settings", "Настройки",
                "Enabled", "Вкл", "Disabled", "Выкл", "ON", "ВКЛ", "OFF", "ВЫКЛ", "selected", "выбрано", "Combat", "Бой", "Visual", "Визуал", "Movement", "Движение", "World", "Мир", "Meme", "Мемы", "Client", "Клиент", "Scripts", "Скрипты", "Killaura", "Киллаура", "ItemESP", "Предметы ESP", "BlockOverlay", "Оверлей блоков", "AutoTool", "Автоинструмент", "FastBreak", "Быстрая добыча", "CustomCrosshair", "Прицел", "Rotate Back Speed", "Скорость возврата", "Raycast", "Рейкаст", "Outline", "Контур", "Fill", "Заливка", "Static", "Статично", "Fade", "Переход", "Rainbow", "Радуга"));
        add("Japanese", pairs(
                "Language", "言語", "Choose Vibe's interface language", "Vibe の表示言語を選択",
                "ClickGUI", "クリックGUI", "MoveFix", "移動補正", "Mode", "モード", "English", "英語", "Chinese", "中国語", "Russian", "ロシア語", "Japanese", "日本語", "Bavarian", "バイエルン語",
                "Silent", "サイレント", "Strict", "厳密", "Visual", "表示", "Return To Origin Speed", "復帰速度",
                "Continue", "続ける", "Shader settings", "シェーダー設定", "Vibe Shader", "Vibe シェーダー", "Vibe Discord", "Vibe Discord",
                "WELCOME TO VIBE", "VIBE へようこそ", "Choose the local gamertag Vibe will display.", "Vibe に表示するローカル名を選択してください。",
                "Back", "戻る", "Save", "保存", "Load", "読み込む", "Delete", "削除", "Search", "検索", "Settings", "設定",
                "Enabled", "有効", "Disabled", "無効", "ON", "オン", "OFF", "オフ", "selected", "選択", "Combat", "戦闘", "Visual", "描画", "Movement", "移動", "World", "ワールド", "Client", "クライアント", "Outline", "輪郭", "Fill", "塗りつぶし", "Static", "固定", "Fade", "フェード", "Rainbow", "虹"));
        add("Bavarian", pairs(
                "Language", "Sproch", "Choose Vibe's interface language", "Such da Vibe-Sproch aus",
                "ClickGUI", "Klick-GUI", "MoveFix", "Geh-Fix", "Mode", "Art", "English", "Englisch", "Chinese", "Chinesisch", "Russian", "Russisch", "Japanese", "Japanisch", "Bavarian", "Boarisch",
                "Silent", "Staad", "Strict", "Stramm", "Visual", "Sichtbar", "Return To Origin Speed", "Zruckgeh-Gschwindigkait",
                "Continue", "Weida", "Shader settings", "Shader-Eistellung", "Vibe Shader", "Vibe Shader", "Vibe Discord", "Vibe Discord",
                "WELCOME TO VIBE", "GRIASS DI BEI VIBE", "Choose the local gamertag Vibe will display.", "Such da an lokalen Gamer-Tag aus.",
                "Back", "Zruck", "Save", "Speichern", "Load", "Ladn", "Delete", "Löschn", "Search", "Suachn", "Settings", "Eistellung",
                "Enabled", "O", "Disabled", "Aus", "ON", "O", "OFF", "Aus", "selected", "ausg'wuids", "Combat", "Kampf", "Visual", "Optik", "Movement", "Bewegung", "World", "Welt", "Client", "Client", "Outline", "Rand", "Fill", "Füllung", "Static", "Starr", "Fade", "Übergang", "Rainbow", "Regenbog'n"));

        // Names appearing in the HUD, newly added modules, and common
        // settings.  Keeping these in the same display catalog means profile
        // keys remain stable while substantially more of the client changes
        // language with the Language module.
        extend("Chinese", pairs(
                "HUD", "界面", "Vibe", "Vibe", "Skeet", "Skeet", "Watermark", "水印", "Coordinates", "坐标", "Clock", "时钟", "Session Info", "会话信息", "Motion Graph", "移动图表", "Armor", "护甲", "Inventory", "物品栏", "CPS Graph", "点击速度图表",
                "GTA7", "GTA7", "Girlfriend", "女友", "NES Emulator", "NES 模拟器", "Test", "测试", "Color", "颜色", "Line Width", "线宽", "Smart", "智能", "Basic", "基础", "Speed", "速度", "FinishFaster", "快速完成", "Break Circle", "挖掘圆环", "Attacks Per Second", "每秒攻击数"));
        extend("Russian", pairs(
                "HUD", "HUD", "Vibe", "Vibe", "Skeet", "Skeet", "Watermark", "Водяной знак", "Coordinates", "Координаты", "Clock", "Часы", "Session Info", "Сеанс", "Motion Graph", "График движения", "Armor", "Броня", "Inventory", "Инвентарь", "CPS Graph", "График CPS",
                "GTA7", "GTA7", "Girlfriend", "Подруга", "NES Emulator", "Эмулятор NES", "Test", "Тест", "Color", "Цвет", "Line Width", "Толщина линии", "Smart", "Умный", "Basic", "Базовый", "Speed", "Скорость", "FinishFaster", "Быстро закончить", "Break Circle", "Круг добычи", "Attacks Per Second", "Атак в секунду"));
        extend("Japanese", pairs(
                "Meme", "ミーム", "Scripts", "スクリプト", "Killaura", "キルオーラ", "ItemESP", "アイテムESP", "HUD", "HUD", "Vibe", "Vibe", "Skeet", "Skeet", "Watermark", "透かし", "Coordinates", "座標", "Clock", "時計", "Session Info", "セッション情報", "Motion Graph", "移動グラフ", "Armor", "防具", "Inventory", "インベントリ", "CPS Graph", "CPS グラフ",
                "GTA7", "GTA7", "Girlfriend", "ガールフレンド", "NES Emulator", "NES エミュレータ", "Test", "テスト", "Color", "色", "Line Width", "線幅", "Smart", "スマート", "Basic", "基本", "Speed", "速度", "FinishFaster", "高速完了", "Break Circle", "採掘サークル", "Attacks Per Second", "毎秒攻撃数", "Rotate Back Speed", "復帰速度", "Raycast", "レイキャスト"));
        extend("Bavarian", pairs(
                "Meme", "Mem", "Scripts", "Skripts", "Killaura", "Kill-Aura", "ItemESP", "Item-ESP", "HUD", "Anzeige", "Vibe", "Vibe", "Skeet", "Skeet", "Watermark", "Wossazeichn", "Coordinates", "Koordinatn", "Clock", "Uhr", "Session Info", "Sitzungsinfo", "Motion Graph", "Bewegungsgraph", "Armor", "Rüstung", "Inventory", "Inventar", "CPS Graph", "Klick-Graph",
                "GTA7", "GTA7", "Girlfriend", "Freindin", "NES Emulator", "NES-Emulator", "Test", "Test", "Color", "Farb", "Line Width", "Linienbreitn", "Smart", "Gscheid", "Basic", "Grund", "Speed", "Gschwindigkeit", "FinishFaster", "Schnölla fertig", "Break Circle", "Abbau-Kreis", "Attacks Per Second", "Angriff pro Sekund"));
        loadInterfaceCatalog();
        registerExpandedLanguages();
        registerModernModuleLabels();
        loadCompleteCatalog();
    }

    private LanguageManager() { }

    /** Ordered list shared by the first-run and main-menu selectors. */
    public static List<String> languages() { return LANGUAGES; }

    /** Accept the original wording of the language request when importing older identities. */
    public static String normalizeLanguage(String value) {
        if (value == null) return "English";
        String clean = value.trim();
        if ("Finish".equalsIgnoreCase(clean)) clean = "Finnish";
        if ("Sweden".equalsIgnoreCase(clean)) clean = "Swedish";
        if ("Tagalog (Filipino)".equalsIgnoreCase(clean) || "Filipino".equalsIgnoreCase(clean)) clean = "Tagalog";
        if ("Minecraft Enchantment Table".equalsIgnoreCase(clean) || "Minecraft Entchantment Table".equalsIgnoreCase(clean)) clean = "Enchantment Table";
        if ("Aurebesh from StarWars".equalsIgnoreCase(clean) || "Aurebesh from Star Wars".equalsIgnoreCase(clean)) clean = "Aurebesh";
        for (String language : LANGUAGES) if (language.equalsIgnoreCase(clean)) return language;
        return "English";
    }

    public static boolean isSupported(String value) {
        return !"English".equals(normalizeLanguage(value)) || "English".equalsIgnoreCase(value == null ? "" : value.trim());
    }

    /** True when a built-in string has a direct catalog entry for the selected language. */
    public static boolean hasTranslation(String text, String language) {
        String selected = normalizeLanguage(language);
        if ("English".equals(selected)) return true;
        Map<String, String> translations = CATALOG.get(selected);
        return translations != null && text != null && translations.containsKey(text.toLowerCase(Locale.ROOT));
    }

    /** Native labels keep the chooser clear even before the new language is active. */
    public static String displayName(String language) {
        String value = normalizeLanguage(language);
        if ("Chinese".equals(value)) return "中文";
        if ("Russian".equals(value)) return "Русский";
        if ("Japanese".equals(value)) return "日本語";
        if ("Bavarian".equals(value)) return "Boarisch";
        if ("Finnish".equals(value)) return "Suomi";
        if ("Swedish".equals(value)) return "Svenska";
        if ("Greek".equals(value)) return "Ελληνικά";
        if ("Spanish".equals(value)) return "Español";
        if ("German".equals(value)) return "Deutsch";
        if ("French".equals(value)) return "Français";
        if ("Enchantment Table".equals(value)) return "Enchantment Table";
        if ("Portuguese".equals(value)) return "Português";
        if ("Ukrainian".equals(value)) return "Українська";
        if ("Hindi".equals(value)) return "हिन्दी";
        if ("Standard Arabic".equals(value)) return "العربية الفصحى";
        if ("Bengali".equals(value)) return "বাংলা";
        if ("Indonesian".equals(value)) return "Bahasa Indonesia";
        if ("Urdu".equals(value)) return "اردو";
        if ("Nigerian Pidgin".equals(value)) return "Naija Pidgin";
        if ("Egyptian Arabic".equals(value)) return "العربية المصرية";
        if ("Marathi".equals(value)) return "मराठी";
        if ("Vietnamese".equals(value)) return "Tiếng Việt";
        if ("Telugu".equals(value)) return "తెలుగు";
        if ("Swahili".equals(value)) return "Kiswahili";
        if ("Hausa".equals(value)) return "Hausa";
        if ("Turkish".equals(value)) return "Türkçe";
        if ("Western Punjabi".equals(value)) return "ਪੰਜਾਬੀ";
        if ("Tagalog".equals(value)) return "Filipino";
        if ("Tamil".equals(value)) return "தமிழ்";
        if ("Iranian Persian".equals(value)) return "فارسی";
        if ("Korean".equals(value)) return "한국어";
        if ("Amharic".equals(value)) return "አማርኛ";
        if ("Thai".equals(value)) return "ไทย";
        if ("Javanese".equals(value)) return "Basa Jawa";
        if ("Italian".equals(value)) return "Italiano";
        if ("Gujarati".equals(value)) return "ગુજરાતી";
        if ("Dutch".equals(value)) return "Nederlands";
        if ("Nepali".equals(value)) return "नेपाली";
        if ("Czech".equals(value)) return "Čeština";
        if ("Polish".equals(value)) return "Polski";
        if ("Zulu".equals(value)) return "isiZulu";
        if ("Romanian".equals(value)) return "Română";
        if ("Aurebesh".equals(value)) return "Aurebesh";
        return value;
    }

    public static String translate(String text) {
        return translate(text, selectedLanguage());
    }

    public static String translate(String text, String language) {
        if (text == null || text.isEmpty()) return text;
        String selected = normalizeLanguage(language);
        if ("English".equals(selected)) return text;
        Map<String, String> translations = CATALOG.get(selected);
        if (translations == null) return text;
        String translated = translations.get(text.toLowerCase(Locale.ROOT));
        return translated == null ? translateCompound(text, translations) : translated;
    }

    /**
     * Formats a localized Vibe-owned interface string.  It deliberately takes
     * the English source text as its key, so changing the selected language
     * never changes profile, module, or setting identifiers.
     */
    public static String format(String text, Object... arguments) {
        return String.format(Locale.ROOT, translate(text), arguments);
    }

    /**
     * Settings frequently compose a label from terms such as "Outline",
     * "Color", and "Speed".  A full phrase in the catalog always wins; this
     * word-level fallback translates combinations of known terms. If any word
     * is unknown, preserve the entire text so custom names are not partially
     * translated.
     */
    private static String translateCompound(String text, Map<String, String> translations) {
        Matcher matcher = WORD.matcher(text);
        StringBuffer result = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            String replacement = translations.get(matcher.group().toLowerCase(Locale.ROOT));
            if (replacement == null) return text;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            found = true;
        }
        if (!found) return text;
        matcher.appendTail(result);
        return result.toString();
    }

    /** Every selectable language has a real base UI catalog instead of silently falling back to English. */
    private static void registerExpandedLanguages() {
        localized("Finnish", "Kieli", "Valitse Viben käyttöliittymän kieli", "Jatka", "Asetukset", "Tallenna", "Lataa", "Poista", "Haku", "Käytössä", "Pois käytöstä", "PÄÄLLÄ", "POIS", "valittu");
        localized("Swedish", "Språk", "Välj Vibes gränssnittsspråk", "Fortsätt", "Inställningar", "Spara", "Ladda", "Radera", "Sök", "Aktiverad", "Inaktiverad", "PÅ", "AV", "vald");
        localized("Greek", "Γλώσσα", "Επιλέξτε γλώσσα διεπαφής Vibe", "Συνέχεια", "Ρυθμίσεις", "Αποθήκευση", "Φόρτωση", "Διαγραφή", "Αναζήτηση", "Ενεργοποιημένο", "Απενεργοποιημένο", "ΕΝΕΡΓΟ", "ΑΝΕΝΕΡΓΟ", "επιλεγμένο");
        localized("Spanish", "Idioma", "Elige el idioma de la interfaz de Vibe", "Continuar", "Configuración", "Guardar", "Cargar", "Eliminar", "Buscar", "Activado", "Desactivado", "ACTIVADO", "DESACTIVADO", "seleccionado");
        localized("German", "Sprache", "Wähle die Vibe-Oberflächensprache", "Weiter", "Einstellungen", "Speichern", "Laden", "Löschen", "Suchen", "Aktiviert", "Deaktiviert", "AN", "AUS", "ausgewählt");
        localized("French", "Langue", "Choisissez la langue de l’interface Vibe", "Continuer", "Paramètres", "Enregistrer", "Charger", "Supprimer", "Rechercher", "Activé", "Désactivé", "ACTIVÉ", "DÉSACTIVÉ", "sélectionné");
        localized("Enchantment Table", "ᓭ!¡ᒷᔑꖌ", "ᓵ⍑𝙹𝙹ᓭᒷ ∴╎ʖᒷ ╎リℸ ̣ ᒷ∷⎓ᔑᓵᒷ ꖎᔑリ⊣⚍ᔑ⊣ᒷ", "ᓵ𝙹リℸ ̣ ╎リ⚍ᒷ", "ᓭᒷℸ ̣ ℸ ̣ ╎リ⊣ᓭ", "ᓭᔑ⍊ᒷ", "ꖎ𝙹ᔑ↸", "↸ᒷꖎᒷℸ ̣ ᒷ", "ᓭᒷᔑ∷ᓵ⍑", "ᒷリᔑʖꖎᒷ↸", "↸╎ᓭᔑʖꖎᒷ↸", "𝙹リ", "𝙹⎓⎓", "ᓭᒷꖎᒷᓵℸ ̣ ᒷ↸");
        localized("Portuguese", "Idioma", "Escolha o idioma da interface do Vibe", "Continuar", "Configurações", "Salvar", "Carregar", "Excluir", "Pesquisar", "Ativado", "Desativado", "LIGADO", "DESLIGADO", "selecionado");
        localized("Ukrainian", "Мова", "Оберіть мову інтерфейсу Vibe", "Продовжити", "Налаштування", "Зберегти", "Завантажити", "Видалити", "Пошук", "Увімкнено", "Вимкнено", "УВІМК", "ВИМК", "вибрано");
        localized("Hindi", "भाषा", "Vibe इंटरफ़ेस भाषा चुनें", "जारी रखें", "सेटिंग्स", "सहेजें", "लोड करें", "हटाएं", "खोजें", "सक्षम", "अक्षम", "चालू", "बंद", "चयनित");
        localized("Standard Arabic", "اللغة", "اختر لغة واجهة Vibe", "متابعة", "الإعدادات", "حفظ", "تحميل", "حذف", "بحث", "مفعّل", "معطّل", "تشغيل", "إيقاف", "محدد");
        localized("Bengali", "ভাষা", "Vibe ইন্টারফেসের ভাষা বেছে নিন", "চালিয়ে যান", "সেটিংস", "সংরক্ষণ", "লোড", "মুছুন", "খুঁজুন", "সক্রিয়", "নিষ্ক্রিয়", "চালু", "বন্ধ", "নির্বাচিত");
        localized("Indonesian", "Bahasa", "Pilih bahasa antarmuka Vibe", "Lanjutkan", "Pengaturan", "Simpan", "Muat", "Hapus", "Cari", "Aktif", "Nonaktif", "NYALA", "MATI", "dipilih");
        localized("Urdu", "زبان", "Vibe انٹرفیس کی زبان منتخب کریں", "جاری رکھیں", "ترتیبات", "محفوظ کریں", "لوڈ کریں", "حذف کریں", "تلاش", "فعال", "غیر فعال", "آن", "آف", "منتخب");
        localized("Nigerian Pidgin", "Language", "Pick Vibe screen language", "Continue", "Settings", "Save", "Load", "Delete", "Search", "E dey on", "E dey off", "ON", "OFF", "wey you pick");
        localized("Egyptian Arabic", "اللغة", "اختار لغة واجهة Vibe", "كمّل", "الإعدادات", "احفظ", "حمّل", "امسح", "دوّر", "شغّال", "مقفول", "شغّال", "مقفول", "متحدد");
        localized("Marathi", "भाषा", "Vibe इंटरफेस भाषा निवडा", "पुढे", "सेटिंग्ज", "जतन करा", "लोड करा", "हटवा", "शोधा", "सक्रिय", "निष्क्रिय", "चालू", "बंद", "निवडलेले");
        localized("Vietnamese", "Ngôn ngữ", "Chọn ngôn ngữ giao diện Vibe", "Tiếp tục", "Cài đặt", "Lưu", "Tải", "Xóa", "Tìm kiếm", "Bật", "Tắt", "BẬT", "TẮT", "đã chọn");
        localized("Telugu", "భాష", "Vibe ఇంటర్‌ఫేస్ భాషను ఎంచుకోండి", "కొనసాగించు", "సెట్టింగ్‌లు", "సేవ్", "లోడ్", "తొలగించు", "వెతుకు", "ప్రారంభం", "నిలిపివేయబడింది", "ఆన్", "ఆఫ్", "ఎంచుకున్నవి");
        localized("Swahili", "Lugha", "Chagua lugha ya kiolesura cha Vibe", "Endelea", "Mipangilio", "Hifadhi", "Pakia", "Futa", "Tafuta", "Imewashwa", "Imezimwa", "WASHA", "ZIMA", "imechaguliwa");
        localized("Hausa", "Harshe", "Zaɓi harshen Vibe", "Ci gaba", "Saituna", "Ajiye", "Loda", "Share", "Bincika", "Kunna", "Kashe", "KUNNA", "KASHE", "zaɓaɓɓe");
        localized("Turkish", "Dil", "Vibe arayüz dilini seçin", "Devam", "Ayarlar", "Kaydet", "Yükle", "Sil", "Ara", "Etkin", "Devre dışı", "AÇIK", "KAPALI", "seçili");
        localized("Western Punjabi", "زبان", "Vibe انٹرفیس دی زبان چنو", "جاری رکھو", "ترتیباں", "محفوظ کرو", "لوڈ کرو", "مٹاؤ", "لبھو", "چالو", "بند", "چالو", "بند", "چنیا");
        localized("Tagalog", "Wika", "Piliin ang wika ng interface ng Vibe", "Magpatuloy", "Mga setting", "I-save", "I-load", "Tanggalin", "Maghanap", "Naka-enable", "Naka-disable", "BUKAS", "SARADO", "napili");
        localized("Tamil", "மொழி", "Vibe இடைமுக மொழியைத் தேர்ந்தெடுக்கவும்", "தொடரவும்", "அமைப்புகள்", "சேமி", "ஏற்று", "நீக்கு", "தேடு", "இயக்கப்பட்டது", "முடக்கப்பட்டது", "ஆன்", "ஆஃப்", "தேர்ந்தெடுக்கப்பட்டது");
        localized("Iranian Persian", "زبان", "زبان رابط Vibe را انتخاب کنید", "ادامه", "تنظیمات", "ذخیره", "بارگذاری", "حذف", "جستجو", "فعال", "غیرفعال", "روشن", "خاموش", "انتخاب‌شده");
        localized("Korean", "언어", "Vibe 인터페이스 언어를 선택하세요", "계속", "설정", "저장", "불러오기", "삭제", "검색", "활성화됨", "비활성화됨", "켜짐", "꺼짐", "선택됨");
        localized("Amharic", "ቋንቋ", "የVibe በይነገጽ ቋንቋ ይምረጡ", "ቀጥል", "ቅንብሮች", "አስቀምጥ", "ጫን", "ሰርዝ", "ፈልግ", "ነቅቷል", "ተሰናክሏል", "በርቷል", "ጠፍቷል", "ተመርጧል");
        localized("Thai", "ภาษา", "เลือกภาษาอินเทอร์เฟซ Vibe", "ดำเนินการต่อ", "การตั้งค่า", "บันทึก", "โหลด", "ลบ", "ค้นหา", "เปิดใช้งาน", "ปิดใช้งาน", "เปิด", "ปิด", "เลือกแล้ว");
        localized("Javanese", "Basa", "Pilih basa antarmuka Vibe", "Terusake", "Pangaturan", "Simpen", "Muat", "Busak", "Golek", "Diaktifake", "Pateni", "URIP", "MATI", "dipilih");
        localized("Italian", "Lingua", "Scegli la lingua dell'interfaccia Vibe", "Continua", "Impostazioni", "Salva", "Carica", "Elimina", "Cerca", "Abilitato", "Disabilitato", "ATTIVO", "DISATTIVO", "selezionato");
        localized("Gujarati", "ભાષા", "Vibe ઇન્ટરફેસ ભાષા પસંદ કરો", "ચાલુ રાખો", "સેટિંગ્સ", "સાચવો", "લોડ કરો", "કાઢી નાખો", "શોધો", "સક્રિય", "નિષ્ક્રિય", "ચાલુ", "બંધ", "પસંદ કરેલ");
        localized("Dutch", "Taal", "Kies de interfacetaal van Vibe", "Doorgaan", "Instellingen", "Opslaan", "Laden", "Verwijderen", "Zoeken", "Ingeschakeld", "Uitgeschakeld", "AAN", "UIT", "geselecteerd");
        localized("Nepali", "भाषा", "Vibe इन्टरफेस भाषा छान्नुहोस्", "जारी राख्नुहोस्", "सेटिङहरू", "सुरक्षित गर्नुहोस्", "लोड गर्नुहोस्", "मेटाउनुहोस्", "खोज्नुहोस्", "सक्षम", "असक्षम", "चालु", "बन्द", "छानिएको");
        localized("Czech", "Jazyk", "Zvolte jazyk rozhraní Vibe", "Pokračovat", "Nastavení", "Uložit", "Načíst", "Smazat", "Hledat", "Povoleno", "Zakázáno", "ZAP", "VYP", "vybráno");
        localized("Polish", "Język", "Wybierz język interfejsu Vibe", "Kontynuuj", "Ustawienia", "Zapisz", "Wczytaj", "Usuń", "Szukaj", "Włączone", "Wyłączone", "WŁ", "WYŁ", "wybrane");
        localized("Zulu", "Ulimi", "Khetha ulimi lwesixhumi se-Vibe", "Qhubeka", "Izilungiselelo", "Londoloza", "Layisha", "Susa", "Sesha", "Kuvuliwe", "Kukhutshaziwe", "VULIWE", "VALIWE", "kukhethiwe");
        localized("Romanian", "Limbă", "Alege limba interfeței Vibe", "Continuă", "Setări", "Salvează", "Încarcă", "Șterge", "Caută", "Activat", "Dezactivat", "PORNIT", "OPRIT", "selectat");
        localized("Aurebesh", "Aurebesh", "Choose Vibe interface language", "Continue", "Settings", "Save", "Load", "Delete", "Search", "Enabled", "Disabled", "ON", "OFF", "selected");
    }

    private static void localized(String language, String languageLabel, String choose, String continueText, String settings,
            String save, String load, String delete, String search, String enabled, String disabled, String on, String off, String selected) {
        add(language, pairs("Language", languageLabel, "Choose Vibe's interface language", choose,
                "Continue", continueText, "Settings", settings, "Back", "Back", "Save", save, "Load", load,
                "Delete", delete, "Search", search, "Enabled", enabled, "Disabled", disabled, "ON", on,
                "OFF", off, "selected", selected, "Language selector", languageLabel, "Choose language", choose,
                "Close", "×", "Elements", "Elements", "Theme", "Theme", "Inspector", "Inspector"));
    }

    /** New module names are catalog entries too, so they no longer disappear behind an English fallback. */
    private static void registerModernModuleLabels() {
        Map<String, String> stableNames = pairs("Custom Cosmetics", "Custom Cosmetics", "LagRange", "LagRange",
                "TickBase", "TickBase", "TimerRange", "TimerRange", "LongJump", "LongJump", "NoFall", "NoFall",
                "Flag Detector", "Flag Detector", "Picken Switch", "Picken Switch", "Statistics", "Statistics",
                "Hypixel", "Hypixel", "Music", "Music", "HUD Editor", "HUD Editor", "Murder Mystery", "Murder Mystery",
                "BlockParty", "BlockParty", "PitBot", "PitBot", "LiquidGlass", "LiquidGlass", "Array list", "Array list");
        for (String language : LANGUAGES) if (!"English".equals(language)) extend(language, stableNames);
        extend("Chinese", pairs("Custom Cosmetics", "自定义饰品", "LagRange", "延迟范围", "TickBase", "Tick 基础", "TimerRange", "计时范围", "LongJump", "长跳", "NoFall", "无摔落", "Flag Detector", "回弹检测", "Picken Switch", "附魔切换", "Statistics", "统计", "Hypixel", "Hypixel", "Music", "音乐", "HUD Editor", "界面编辑器", "Murder Mystery", "谋杀之谜", "BlockParty", "方块派对", "PitBot", "矿坑机器人", "Array list", "模块列表"));
        extend("Russian", pairs("Custom Cosmetics", "Своя косметика", "LagRange", "Лаг-дистанция", "TickBase", "Тик-база", "TimerRange", "Таймер-дистанция", "LongJump", "Дальний прыжок", "NoFall", "Без урона от падения", "Flag Detector", "Детектор флагов", "Picken Switch", "Переключение чар", "Statistics", "Статистика", "Music", "Музыка", "HUD Editor", "Редактор HUD", "Murder Mystery", "Мистерия убийцы", "BlockParty", "Блок-пати", "PitBot", "Пит-бот", "Array list", "Список модулей"));
        extend("Japanese", pairs("Custom Cosmetics", "カスタムコスメ", "LagRange", "ラグ範囲", "TickBase", "ティックベース", "TimerRange", "タイマー範囲", "LongJump", "ロングジャンプ", "NoFall", "落下無効", "Flag Detector", "フラグ検出", "Picken Switch", "エンチャント切替", "Statistics", "統計", "Music", "音楽", "HUD Editor", "HUD エディター", "Murder Mystery", "マーダーミステリー", "BlockParty", "ブロックパーティー", "PitBot", "ピットボット", "Array list", "モジュール一覧"));
        extend("Bavarian", pairs("Custom Cosmetics", "Eigene Kosmetik", "LagRange", "Lag-Reichweitn", "TickBase", "Tick-Basis", "TimerRange", "Timer-Reichweitn", "LongJump", "Weit-Springa", "NoFall", "Koan Fallschadn", "Flag Detector", "Flaggn-Prüfer", "Picken Switch", "Verzauberungs-Wechsler", "Statistics", "Statistik", "Music", "Musi", "HUD Editor", "Anzeige-Editor", "Array list", "Modullistn"));
        extend("Spanish", pairs("Custom Cosmetics", "Cosméticos personalizados", "LongJump", "Salto largo", "NoFall", "Sin caída", "Flag Detector", "Detector de flags", "Statistics", "Estadísticas", "Music", "Música", "HUD Editor", "Editor HUD", "Array list", "Lista de módulos"));
        extend("German", pairs("Custom Cosmetics", "Eigene Kosmetik", "LongJump", "Weitsprung", "NoFall", "Kein Fallschaden", "Flag Detector", "Flaggen-Erkennung", "Statistics", "Statistiken", "Music", "Musik", "HUD Editor", "HUD-Editor", "Array list", "Modulliste"));
        extend("French", pairs("Custom Cosmetics", "Cosmétiques personnalisés", "LongJump", "Saut long", "NoFall", "Sans chute", "Flag Detector", "Détecteur de flags", "Statistics", "Statistiques", "Music", "Musique", "HUD Editor", "Éditeur HUD", "Array list", "Liste des modules"));
        extend("Portuguese", pairs("Custom Cosmetics", "Cosméticos personalizados", "LongJump", "Salto longo", "NoFall", "Sem dano de queda", "Flag Detector", "Detector de flags", "Statistics", "Estatísticas", "Music", "Música", "HUD Editor", "Editor de HUD"));
        extend("Turkish", pairs("Custom Cosmetics", "Özel kozmetikler", "LongJump", "Uzun atlama", "NoFall", "Düşüş yok", "Flag Detector", "Bayrak algılayıcı", "Statistics", "İstatistikler", "Music", "Müzik", "HUD Editor", "HUD düzenleyici"));
        extend("Korean", pairs("Custom Cosmetics", "사용자 화장품", "LongJump", "장거리 점프", "NoFall", "낙하 방지", "Flag Detector", "플래그 감지기", "Statistics", "통계", "Music", "음악", "HUD Editor", "HUD 편집기"));
        extend("Italian", pairs("Custom Cosmetics", "Cosmetici personalizzati", "LongJump", "Salto lungo", "NoFall", "Niente caduta", "Flag Detector", "Rilevatore flag", "Statistics", "Statistiche", "Music", "Musica", "HUD Editor", "Editor HUD"));
        extend("Dutch", pairs("Custom Cosmetics", "Aangepaste cosmetica", "LongJump", "Lange sprong", "NoFall", "Geen valschade", "Flag Detector", "Flagdetector", "Statistics", "Statistieken", "Music", "Muziek", "HUD Editor", "HUD-editor"));
        extend("Polish", pairs("Custom Cosmetics", "Własne kosmetyki", "LongJump", "Długi skok", "NoFall", "Bez obrażeń od upadku", "Flag Detector", "Wykrywanie flag", "Statistics", "Statystyki", "Music", "Muzyka", "HUD Editor", "Edytor HUD"));
    }

    private static void loadInterfaceCatalog() {
        String[] languages = {"Chinese", "Russian", "Japanese", "Bavarian"};
        java.io.InputStream stream = LanguageManager.class.getResourceAsStream("/assets/vibe/lang/interface.tsv");
        if (stream == null) return;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                String[] columns = line.split("\t", -1);
                if (columns.length != 5) throw new java.io.IOException("Invalid interface translation row");
                for (int i=0; i<languages.length; i++) extend(languages[i], pairs(columns[0], columns[i+1]));
            }
        } catch (java.io.IOException e) { System.err.println("[Vibe] Could not read interface translations: " + e.getMessage()); }
    }

    /** Characters needed by the native GUI atlas, including every supported language. */
    public static String interfaceGlyphs() {
        StringBuilder text = new StringBuilder();
        for (Map<String, String> language : CATALOG.values()) for (String value : language.values()) text.append(value);
        return text.toString();
    }

    public static String selectedLanguage() {
        if (Vibe.getInstance() == null) return "English";
        if (Vibe.getInstance().getModuleManager() == null) {
            return Vibe.getInstance().getIdentity() == null ? "English" : normalizeLanguage(Vibe.getInstance().getIdentity().getLanguage());
        }
        LanguageModule module = Vibe.getInstance().getModuleManager().getModule(LanguageModule.class);
        return module == null ? "English" : normalizeLanguage(module.getLanguage().getValue());
    }

    /**
     * Generated from every built-in module/setting/UI key.  It is deliberately
     * a packaged resource rather than a web service so changing language never
     * sends any local text or requires an internet connection in the client.
     */
    private static void loadCompleteCatalog() {
        java.io.InputStream stream = LanguageManager.class.getResourceAsStream("/assets/vibe/lang/complete.tsv");
        if (stream == null) return;
        Map<String, Map<String, String>> expanded = new HashMap<String, Map<String, String>>();
        for (String language : LANGUAGES) if (!"English".equals(language)) {
            Map<String, String> existing = CATALOG.get(language);
            expanded.put(language, existing == null ? new HashMap<String, String>() : new HashMap<String, String>(existing));
        }
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) return;
            String[] columns = header.split("\\t", -1);
            if (columns.length < 2 || !"key".equals(columns[0])) throw new java.io.IOException("Invalid complete translation header");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] row = line.split("\\t", -1);
                if (row.length == 0 || row[0].isEmpty()) continue;
                for (int index = 1; index < columns.length && index < row.length; index++) {
                    Map<String, String> target = expanded.get(columns[index]);
                    if (target != null && !row[index].isEmpty()) target.put(row[0].toLowerCase(Locale.ROOT), row[index]);
                }
            }
            for (Map.Entry<String, Map<String, String>> entry : expanded.entrySet()) {
                CATALOG.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
            }
        } catch (java.io.IOException e) {
            System.err.println("[Vibe] Could not read complete interface translations: " + e.getMessage());
        }
    }

    private static void add(String language, Map<String, String> values) {
        CATALOG.put(language, Collections.unmodifiableMap(values));
    }

    private static void extend(String language, Map<String, String> values) {
        Map<String, String> combined = new HashMap<String, String>();
        Map<String, String> current = CATALOG.get(language);
        if (current != null) combined.putAll(current);
        combined.putAll(values);
        CATALOG.put(language, Collections.unmodifiableMap(combined));
    }

    private static Map<String, String> pairs(String... entries) {
        Map<String, String> map = new HashMap<String, String>();
        for (int index = 0; index + 1 < entries.length; index += 2) map.put(entries[index].toLowerCase(Locale.ROOT), entries[index + 1]);
        return map;
    }
}
