// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.display;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** 山海：以原生 GUI 纹理管线提取可着色的 16×16 PNG 图标。 */
public record GraphicIcon(Identifier texture, @Nullable GraphicIconMotion motion) {
    public static final int PIXELS = 16;
    public static final GraphicIcon INFO = builtIn("info");
    public static final GraphicIcon SUCCESS = builtIn("success");
    public static final GraphicIcon WARNING = builtIn("warning");
    public static final GraphicIcon CRITICAL_WARNING = builtIn("critical_warning");
    public static final GraphicIcon ERROR = builtIn("error");
    public static final GraphicIcon STATUS_READY = builtIn("status_ready");
    public static final GraphicIcon STATUS_PENDING = builtIn("status_pending").withMotion(GraphicIconMotion.SPIN);
    public static final GraphicIcon STATUS_SUCCEEDED = builtIn("status_succeeded");
    public static final GraphicIcon STATUS_PARTIAL = builtIn("status_partial");
    public static final GraphicIcon STATUS_FAILED = builtIn("status_failed");
    public static final GraphicIcon STATUS_CANCELLED = builtIn("status_cancelled");
    public static final GraphicIcon STATUS_TIMED_OUT = builtIn("status_timed_out");
    public static final GraphicIcon PREVIOUS = builtIn("previous");
    public static final GraphicIcon NEXT = builtIn("next");
    public static final GraphicIcon BACK = builtIn("back");
    public static final GraphicIcon FORWARD = builtIn("forward");
    public static final GraphicIcon UNDO = builtIn("undo");
    public static final GraphicIcon SWITCH = builtIn("switch");
    public static final GraphicIcon MERGE = builtIn("merge");
    public static final GraphicIcon SEPARATE = builtIn("separate");
    public static final GraphicIcon NEW = builtIn("new");
    public static final GraphicIcon SAVE = builtIn("save");
    public static final GraphicIcon EXPORT = builtIn("export");
    public static final GraphicIcon INPUT = builtIn("input");
    public static final GraphicIcon OUTPUT = builtIn("output");
    public static final GraphicIcon RUN = builtIn("run");
    public static final GraphicIcon PAUSE = builtIn("pause");
    public static final GraphicIcon RESUME = builtIn("resume");
    public static final GraphicIcon ADD = builtIn("add");
    public static final GraphicIcon REMOVE = builtIn("remove");
    public static final GraphicIcon SEARCH = builtIn("search");
    public static final GraphicIcon WEB_SEARCH = builtIn("web_search");
    public static final GraphicIcon FILTER = builtIn("filter");
    public static final GraphicIcon SELECT_ALL = builtIn("select_all");
    public static final GraphicIcon SELECT_PARTIAL = builtIn("select_partial");
    public static final GraphicIcon SORT = builtIn("sort");
    public static final GraphicIcon EDIT = builtIn("edit");
    public static final GraphicIcon COPY = builtIn("copy");
    public static final GraphicIcon PASTE = builtIn("paste");
    public static final GraphicIcon CLIPBOARD = builtIn("clipboard");
    public static final GraphicIcon CUT = builtIn("cut");
    public static final GraphicIcon MOVE = builtIn("move");
    public static final GraphicIcon SCALE = builtIn("scale");
    public static final GraphicIcon ROTATE = builtIn("rotate");
    public static final GraphicIcon MIRROR = builtIn("mirror");
    public static final GraphicIcon SYMMETRY = builtIn("symmetry");
    public static final GraphicIcon FILL = builtIn("fill");
    public static final GraphicIcon STROKE = builtIn("stroke");
    public static final GraphicIcon WARP = builtIn("warp");
    public static final GraphicIcon BLUR = builtIn("blur");
    public static final GraphicIcon RENDER = builtIn("render");
    public static final GraphicIcon DELETE = builtIn("delete");
    public static final GraphicIcon FAVORITE = builtIn("favorite");
    public static final GraphicIcon MY_FAVORITES = builtIn("my_favorites");
    public static final GraphicIcon BOOKMARK_FAVORITE = builtIn("bookmark_favorite");
    public static final GraphicIcon BOOKMARK = builtIn("bookmark");
    public static final GraphicIcon LOCK = builtIn("lock");
    public static final GraphicIcon UNLOCK = builtIn("unlock");
    public static final GraphicIcon COMPRESS = builtIn("compress");
    public static final GraphicIcon DECOMPRESS = builtIn("decompress");
    public static final GraphicIcon ENCRYPT = builtIn("encrypt");
    public static final GraphicIcon DECRYPT = builtIn("decrypt");
    public static final GraphicIcon EXPAND = builtIn("expand");
    public static final GraphicIcon COLLAPSE = builtIn("collapse");
    public static final GraphicIcon UPLOAD = builtIn("upload");
    public static final GraphicIcon DOWNLOAD = builtIn("download");
    public static final GraphicIcon INSTALL = builtIn("install");
    public static final GraphicIcon SETTINGS = builtIn("settings");
    public static final GraphicIcon TOOL = builtIn("tool");
    public static final GraphicIcon TOOLBAR = builtIn("toolbar");
    public static final GraphicIcon REFRESH = builtIn("refresh");
    public static final GraphicIcon REPAIR = builtIn("repair");
    public static final GraphicIcon RESET = builtIn("reset");
    public static final GraphicIcon DISABLE = builtIn("disable");
    public static final GraphicIcon ENABLE = builtIn("enable");
    public static final GraphicIcon HIDE = builtIn("hide");
    public static final GraphicIcon SHOW = builtIn("show");
    public static final GraphicIcon ORGANIZE = builtIn("organize");
    public static final GraphicIcon CACHE = builtIn("cache");
    public static final GraphicIcon LOG = builtIn("log");
    public static final GraphicIcon MONITOR = builtIn("monitor");
    public static final GraphicIcon TODO = builtIn("todo");
    public static final GraphicIcon PLAN = builtIn("plan");
    public static final GraphicIcon GOAL = builtIn("goal");
    public static final GraphicIcon FOLDER = builtIn("folder");
    public static final GraphicIcon FILE = builtIn("file");
    public static final GraphicIcon DOCUMENT = builtIn("document");
    public static final GraphicIcon GUIDE = builtIn("guide");
    public static final GraphicIcon FILE_EXE = builtIn("file_exe");
    public static final GraphicIcon FILE_MD = builtIn("file_md");
    public static final GraphicIcon FILE_JAVA = builtIn("file_java");
    public static final GraphicIcon FILE_KT = builtIn("file_kt");
    public static final GraphicIcon CODE = builtIn("code");
    public static final GraphicIcon EQUATION = builtIn("equation");
    public static final GraphicIcon FUNCTION = builtIn("function");
    public static final GraphicIcon TEST = builtIn("test");
    public static final GraphicIcon DEVELOPMENT = builtIn("development");
    public static final GraphicIcon COLLECTION = builtIn("collection");
    public static final GraphicIcon HISTORY = builtIn("history");
    public static final GraphicIcon USER = builtIn("user");
    public static final GraphicIcon MY_PROFILE = builtIn("my_profile");
    public static final GraphicIcon SYSTEM = builtIn("system");
    public static final GraphicIcon PHONE = builtIn("phone");
    public static final GraphicIcon COMPUTER = builtIn("computer");
    public static final GraphicIcon WINDOWS = builtIn("windows");
    public static final GraphicIcon MACOS = builtIn("macos");
    public static final GraphicIcon IOS = builtIn("ios");
    public static final GraphicIcon ANDROID = builtIn("android");
    public static final GraphicIcon LINUX = builtIn("linux");
    public static final GraphicIcon MINECRAFT = builtIn("minecraft");
    public static final GraphicIcon GAMEPAD = builtIn("gamepad");
    public static final GraphicIcon MOUSE = builtIn("mouse");
    public static final GraphicIcon KEYBOARD = builtIn("keyboard");
    public static final GraphicIcon HEADPHONES = builtIn("headphones");
    public static final GraphicIcon MICROPHONE = builtIn("microphone");
    public static final GraphicIcon FORGE = builtIn("forge");
    public static final GraphicIcon NEOFORGE = builtIn("neoforge");
    public static final GraphicIcon COMPATIBILITY = builtIn("compatibility");
    public static final GraphicIcon BLOCK = builtIn("block");
    public static final GraphicIcon ITEM = builtIn("item");
    public static final GraphicIcon VIRTUAL_ITEM_PROVIDER = builtIn("virtual_item_provider");
    public static final GraphicIcon VIRTUAL_ITEM = builtIn("virtual_item");
    public static final GraphicIcon VIRTUAL = builtIn("virtual");
    public static final GraphicIcon ATTRIBUTES = builtIn("attributes");
    public static final GraphicIcon DESCRIPTION = builtIn("description");
    public static final GraphicIcon TOOLTIP = builtIn("tooltip");
    public static final GraphicIcon INTERACTION = builtIn("interaction");
    public static final GraphicIcon AE2 = builtIn("ae2");
    public static final GraphicIcon QUANTUM = builtIn("quantum");
    public static final GraphicIcon AE2_PATTERN = builtIn("ae2_pattern");
    public static final GraphicIcon QUICK_PATTERN_ENCODE = builtIn("quick_pattern_encode");
    public static final GraphicIcon QUICK = builtIn("quick");
    public static final GraphicIcon CLOUD_PATTERN_LIBRARY = builtIn("cloud_pattern_library");
    public static final GraphicIcon CLOUD = builtIn("cloud");
    public static final GraphicIcon CRAFTING = builtIn("crafting");
    public static final GraphicIcon RECIPE_TYPE = builtIn("recipe_type");
    public static final GraphicIcon RECIPE = builtIn("recipe");
    public static final GraphicIcon RECIPE_ID = builtIn("recipe_id");
    public static final GraphicIcon ID = builtIn("id");
    public static final GraphicIcon MEKANISM = builtIn("mekanism");
    public static final GraphicIcon FTB_QUESTS = builtIn("ftb_quests");
    public static final GraphicIcon KUBEJS = builtIn("kubejs");
    public static final GraphicIcon GUIDEME = builtIn("guideme");
    public static final GraphicIcon GREGTECH = builtIn("gregtech");
    public static final GraphicIcon PLUGIN = builtIn("plugin");
    public static final GraphicIcon GITHUB = builtIn("github");
    public static final GraphicIcon GITHUB_REPO = builtIn("github_repo");
    public static final GraphicIcon GITHUB_PULL_REQUEST = builtIn("github_pull_request");
    public static final GraphicIcon GITHUB_ISSUE = builtIn("github_issue");
    public static final GraphicIcon GITHUB_DISCUSSION = builtIn("github_discussion");
    public static final GraphicIcon GITHUB_ACTIONS = builtIn("github_actions");
    public static final GraphicIcon GITHUB_README = builtIn("github_readme");
    public static final GraphicIcon GITHUB_RELEASE = builtIn("github_release");
    public static final GraphicIcon GITHUB_COMMITS = builtIn("github_commits");
    public static final GraphicIcon LICENSE = builtIn("license");
    public static final GraphicIcon LICENSE_OPEN_SOURCE = builtIn("license_open_source");
    public static final GraphicIcon LICENSE_CLOSED_SOURCE = builtIn("license_closed_source");
    public static final GraphicIcon AUTHORIZATION = builtIn("authorization");
    public static final GraphicIcon CONTRIBUTORS = builtIn("contributors");
    public static final GraphicIcon AFDIAN = builtIn("afdian");
    public static final GraphicIcon SUPPORT = builtIn("support");
    public static final GraphicIcon ACKNOWLEDGMENTS = builtIn("acknowledgments");
    public static final GraphicIcon CHATGPT = builtIn("chatgpt");
    public static final GraphicIcon CLAUDE = builtIn("claude");
    public static final GraphicIcon DEEPSEEK = builtIn("deepseek");
    public static final GraphicIcon GROK = builtIn("grok");
    public static final GraphicIcon GEMINI = builtIn("gemini");
    public static final GraphicIcon QQ = builtIn("qq");
    public static final GraphicIcon WECHAT = builtIn("wechat");
    public static final GraphicIcon X = builtIn("x");
    public static final GraphicIcon EMAIL = builtIn("email");
    public static final GraphicIcon QQ_MAIL = builtIn("qq_mail");
    public static final GraphicIcon ICLOUD_MAIL = builtIn("icloud_mail");
    public static final GraphicIcon GMAIL = builtIn("gmail");
    public static final GraphicIcon DISCORD = builtIn("discord");
    public static final GraphicIcon TELEGRAM = builtIn("telegram");
    public static final GraphicIcon WHATSAPP = builtIn("whatsapp");
    public static final GraphicIcon FACEBOOK = builtIn("facebook");
    public static final GraphicIcon BILIBILI = builtIn("bilibili");
    public static final GraphicIcon DOUYIN = builtIn("douyin");
    public static final GraphicIcon KUAISHOU = builtIn("kuaishou");
    public static final GraphicIcon YOUTUBE = builtIn("youtube");
    public static final GraphicIcon MUSIC = builtIn("music");
    public static final GraphicIcon APP = builtIn("app");
    public static final GraphicIcon QUESTION = builtIn("question");
    public static final GraphicIcon HELP = QUESTION;
    public static final GraphicIcon CALCULATE = builtIn("calculate");
    public static final GraphicIcon CPU = builtIn("cpu");
    public static final GraphicIcon GPU = builtIn("gpu");
    public static final GraphicIcon DISK = builtIn("disk");
    public static final GraphicIcon MAP = builtIn("map");
    public static final GraphicIcon LOCATION = builtIn("location");
    public static final GraphicIcon CHANNEL = builtIn("channel");
    public static final GraphicIcon AE_CHANNEL = builtIn("ae_channel");
    public static final GraphicIcon SHOP = builtIn("shop");
    public static final GraphicIcon PRODUCT_CARD = builtIn("product_card");
    public static final GraphicIcon PRODUCT = builtIn("product");
    public static final GraphicIcon PRODUCT_OUTPUT = builtIn("product_output");
    public static final GraphicIcon PRODUCT_INPUT = builtIn("product_input");
    public static final GraphicIcon BANK = builtIn("bank");
    public static final GraphicIcon MEMBERSHIP = builtIn("membership");
    public static final GraphicIcon CARD = builtIn("card");
    public static final GraphicIcon POINTS = builtIn("points");
    public static final GraphicIcon REDEEM = builtIn("redeem");
    public static final GraphicIcon COUPON = builtIn("coupon");
    public static final GraphicIcon POWER = builtIn("power");
    public static final GraphicIcon SPARK = builtIn("spark");
    public static final GraphicIcon CURRENCY = builtIn("currency");
    public static final GraphicIcon TRADE = builtIn("trade");
    public static final GraphicIcon PAYMENT = builtIn("payment");
    public static final GraphicIcon BILL = builtIn("bill");
    public static final GraphicIcon SETTLEMENT = builtIn("settlement");
    public static final GraphicIcon WITHDRAW = builtIn("withdraw");
    public static final GraphicIcon TRANSFER = builtIn("transfer");
    public static final GraphicIcon BALANCE = builtIn("balance");
    public static final GraphicIcon BUY = builtIn("buy");
    public static final GraphicIcon SELL = builtIn("sell");
    public static final GraphicIcon SHOPPING_CART = builtIn("shopping_cart");
    public static final GraphicIcon TITLE = builtIn("title");
    public static final GraphicIcon TAG = builtIn("tag");
    public static final GraphicIcon COVER = builtIn("cover");
    public static final GraphicIcon CATEGORY = builtIn("category");
    public static final GraphicIcon CHINESE = builtIn("chinese");
    public static final GraphicIcon SUMMARY = builtIn("summary");
    public static final GraphicIcon BODY_TEXT = builtIn("body_text");
    public static final GraphicIcon EMOJI = builtIn("emoji");
    public static final GraphicIcon SYMBOL = builtIn("symbol");
    public static final GraphicIcon TEXT_INPUT = builtIn("text_input");
    public static final GraphicIcon TRANSLATE = builtIn("translate");
    public static final GraphicIcon NUMBER_INPUT = builtIn("number_input");
    public static final GraphicIcon SOPHISTICATED_BACKPACKS = builtIn("sophisticated_backpacks");
    public static final GraphicIcon TEXTURE = builtIn("texture");
    public static final GraphicIcon SHBUI = builtIn("shbui");
    public static final GraphicIcon DARK_MODE = builtIn("dark_mode");
    public static final GraphicIcon LIGHT_MODE = builtIn("light_mode");
    public static final GraphicIcon CUSTOM_THEME = builtIn("custom_theme");
    public static final GraphicIcon MCMOD = builtIn("mcmod");
    public static final GraphicIcon WIKI = builtIn("wiki");
    public static final GraphicIcon PULL_DOWN = builtIn("pull_down");
    public static final GraphicIcon BUBBLE = builtIn("bubble");
    public static final GraphicIcon COMMENT = BUBBLE;
    public static final GraphicIcon CHAT = BUBBLE;
    public static final GraphicIcon CLOSE = builtIn("close");
    public static final GraphicIcon CANCEL = CLOSE;
    public static final GraphicIcon CRITICAL_CANCEL = builtIn("critical_cancel");
    public static final GraphicIcon HOME = builtIn("home");
    public static final GraphicIcon LIST = builtIn("list");
    public static final GraphicIcon WIRELESS = builtIn("wireless");
    public static final GraphicIcon TOPOLOGY = builtIn("topology");
    public static final GraphicIcon STATISTICS = builtIn("statistics");
    public static final GraphicIcon MEMBERS = builtIn("members");
    public static final GraphicIcon CONFIG = builtIn("config");
    public static final GraphicIcon MANAGEMENT = builtIn("management");
    public static final GraphicIcon PERMISSIONS = builtIn("permissions");
    public static final GraphicIcon ACCESS_DENIED = builtIn("access_denied");
    public static final GraphicIcon DETAILS = builtIn("details");
    public static final GraphicIcon MOD = builtIn("mod");
    public static final GraphicIcon TEMPLATE = builtIn("template");
    public static final GraphicIcon DATA = builtIn("data");
    public static final GraphicIcon NETWORK = builtIn("network");
    public static final GraphicIcon SERVER = builtIn("server");
    public static final GraphicIcon DATABASE = builtIn("database");
    public static final GraphicIcon BACKUP = builtIn("backup");
    public static final GraphicIcon MIGRATE = builtIn("migrate");
    public static final GraphicIcon UPGRADE = builtIn("upgrade");
    public static final GraphicIcon SECURITY = builtIn("security");
    public static final GraphicIcon AUDIT = builtIn("audit");
    public static final GraphicIcon REPORT = builtIn("report");
    public static final GraphicIcon QUEUE = builtIn("queue");
    public static final GraphicIcon POLLING = builtIn("polling");
    public static final GraphicIcon ASYNC = builtIn("async");
    public static final GraphicIcon CONCURRENCY = builtIn("concurrency");
    public static final GraphicIcon THREAD = builtIn("thread");
    public static final GraphicIcon PROCESS = builtIn("process");
    public static final GraphicIcon PARALLEL = builtIn("parallel");
    public static final GraphicIcon REWARD = builtIn("reward");
    public static final GraphicIcon SCHEDULER = builtIn("scheduler");
    public static final GraphicIcon TASK = builtIn("task");
    public static final GraphicIcon VISIT = builtIn("visit");
    public static final GraphicIcon PIN = builtIn("pin");
    public static final GraphicIcon SHARE = builtIn("share");
    public static final GraphicIcon LINK = builtIn("link");
    public static final GraphicIcon WINDOW = builtIn("window");
    public static final GraphicIcon GUI = builtIn("gui");
    public static final GraphicIcon UI = builtIn("ui");
    public static final GraphicIcon BROWSER = builtIn("browser");
    public static final GraphicIcon ARCHIVE = builtIn("archive");
    public static final GraphicIcon CLOCK = builtIn("clock");
    public static final GraphicIcon CALENDAR = builtIn("calendar");
    public static final GraphicIcon PLAYER = builtIn("player");
    public static final GraphicIcon MOB = builtIn("mob");
    public static final GraphicIcon ENTITY = builtIn("entity");
    public static final GraphicIcon TICK = builtIn("tick");
    public static final GraphicIcon TPS = builtIn("tps");
    public static final GraphicIcon MSPT = builtIn("mspt");
    public static final GraphicIcon COMPACT_SIGNAL = builtIn("compact_signal");
    public static final GraphicIcon COMPACT_CLOCK = builtIn("compact_clock");
    public static final GraphicIcon COMPACT_CALENDAR = builtIn("compact_calendar");
    public static final GraphicIcon COMPACT_WORLD = builtIn("compact_world");
    public static final GraphicIcon COMPACT_BIOME = builtIn("compact_biome");
    public static final GraphicIcon COMPACT_LOCATION = builtIn("compact_location");
    public static final GraphicIcon COMPACT_CHUNK = builtIn("compact_chunk");
    public static final GraphicIcon COMPACT_TORCH = builtIn("compact_torch");
    public static final GraphicIcon COMPACT_TPS = builtIn("compact_tps");
    public static final GraphicIcon COMPACT_MSPT = builtIn("compact_mspt");
    public static final GraphicIcon COMPACT_MEMORY = builtIn("compact_memory");
    public static final GraphicIcon COMPACT_CPU = builtIn("compact_cpu");
    public static final GraphicIcon COMPACT_GPU = builtIn("compact_gpu");
    public static final GraphicIcon COMPACT_DISK = builtIn("compact_disk");
    public static final GraphicIcon JEI = builtIn("jei");

    public GraphicIcon {
        Objects.requireNonNull(texture, "texture");
    }

    public GraphicIcon(Identifier texture) {
        this(texture, null);
    }

    public GraphicIcon withMotion(@Nullable GraphicIconMotion motion) {
        return new GraphicIcon(texture, motion);
    }

    /** 按正整数倍缩放，坐标和返回尺寸均使用逻辑 GUI 单位。 */
    public static int size(int scale) {
        if (scale <= 0) throw new IllegalArgumentException("scale must be positive");
        return Math.multiplyExact(PIXELS, scale);
    }

    public void draw(GuiGraphicsExtractor graphics, int x, int y, int scale, int argb) {
        draw(graphics, x, y, scale, argb, UiPreferences.configured());
    }

    public void draw(GuiGraphicsExtractor graphics, int x, int y, int scale, int argb, UiPreferences preferences) {
        int size = size(scale);
        if ((argb >>> 24) == 0) return;
        if (motion == null || preferences.reducedMotion()) {
            blit(graphics, x, y, size, argb);
            return;
        }
        var frame = motion.sample(Util.getMillis(), size, false);
        float centerX = x + size * 0.5f, centerY = y + size * 0.5f;
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(centerX, centerY + frame.offsetY())
                    .rotate((float) Math.toRadians(frame.rotationDegrees()))
                    .scale(frame.scale())
                    .translate(-centerX, -centerY);
            blit(graphics, x, y, size, argb);
        } finally {
            pose.popMatrix();
        }
    }

    private void blit(GuiGraphicsExtractor graphics, int x, int y, int size, int argb) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, size, size,
                PIXELS, PIXELS, PIXELS, PIXELS, argb);
    }

    private static GraphicIcon builtIn(String name) {
        return new GraphicIcon(Identifier.fromNamespaceAndPath("sh_better_ui", "textures/gui/display/" + name + ".png"));
    }
}
