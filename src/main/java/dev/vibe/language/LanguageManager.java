package dev.vibe.language;

import dev.vibe.Vibe;
import dev.vibe.module.impl.LanguageModule;
import java.util.Collections;
import java.util.HashMap;
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
    }

    private LanguageManager() { }

    public static String translate(String text) {
        return translate(text, selectedLanguage());
    }

    public static String translate(String text, String language) {
        if (text == null || text.isEmpty()) return text;
        if ("English".equalsIgnoreCase(language)) return text;
        Map<String, String> translations = CATALOG.get(language);
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
            return Vibe.getInstance().getIdentity() == null ? "English" : Vibe.getInstance().getIdentity().getLanguage();
        }
        LanguageModule module = Vibe.getInstance().getModuleManager().getModule(LanguageModule.class);
        return module == null ? "English" : module.getLanguage().getValue();
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
