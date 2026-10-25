package uz.yangiuzbkey.keyboard.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import java.io.File
import uz.yangiuzbkey.keyboard.util.Prefs

/** Every colour the keyboard draws with. */
data class KeyboardTheme(
    val id: String,
    val name: String,
    val bg: Int,
    val bg2: Int = bg,
    val keyBg: Int,
    val keyFuncBg: Int,
    val keyPressed: Int,
    val keyText: Int,
    val keyFuncText: Int = keyText,
    val hint: Int,
    val accent: Int,
    val accentText: Int = Color.WHITE,
    val stripBg: Int = bg,
    val stripText: Int = keyText,
    val border: Int = 0x22000000,
    val popupBg: Int = keyBg,
    val popupText: Int = keyText,
    val popupSelected: Int = accent,
    val keyShadow: Int = 0x33000000,
    val dark: Boolean,
)

enum class KeyStyle { FILLED, OUTLINE, FLAT }

/** Theme + user style tweaks resolved for drawing. */
class ResolvedStyle(
    val theme: KeyboardTheme,
    val keyStyle: KeyStyle,
    val radiusDp: Float,
    val gapDp: Float,
    val fontScale: Float,
    val bold: Boolean,
    val border: Boolean,
    val background: Bitmap?,
    val dim: Float,
)

object ThemeManager {
    private fun c(hex: String) = Color.parseColor(hex)

    val LIGHT = KeyboardTheme(
        "light", "Yorugʻ", bg = c("#E8EBF0"), keyBg = c("#FFFFFF"), keyFuncBg = c("#C9CFD8"),
        keyPressed = c("#B6BEC9"), keyText = c("#1B1F24"), hint = c("#7A828C"), accent = c("#1E88E5"),
        stripBg = c("#E8EBF0"), border = c("#1F000000"), popupBg = c("#FFFFFF"), keyShadow = c("#33000000"), dark = false,
    )
    val DARK = KeyboardTheme(
        "dark", "Qorongʻi", bg = c("#1E2227"), keyBg = c("#3A3F46"), keyFuncBg = c("#2C3036"),
        keyPressed = c("#555C66"), keyText = c("#F1F3F5"), hint = c("#9AA3AD"), accent = c("#4DA3FF"),
        stripBg = c("#1E2227"), border = c("#33FFFFFF"), popupBg = c("#4A5059"), keyShadow = c("#66000000"), dark = true,
    )

    private val presets: List<KeyboardTheme> = listOf(
        LIGHT, DARK,
        KeyboardTheme("amoled", "AMOLED qora", bg = c("#000000"), keyBg = c("#161616"), keyFuncBg = c("#0C0C0C"),
            keyPressed = c("#333333"), keyText = c("#FFFFFF"), hint = c("#8A8A8A"), accent = c("#00BFA5"),
            border = c("#26FFFFFF"), popupBg = c("#262626"), keyShadow = c("#00000000"), dark = true),
        KeyboardTheme("ocean", "Okean", bg = c("#0D47A1"), bg2 = c("#1976D2"), keyBg = c("#2A6FD0"), keyFuncBg = c("#1A5BB8"),
            keyPressed = c("#5C95E6"), keyText = c("#FFFFFF"), hint = c("#BBD6FF"), accent = c("#FFC107"), accentText = c("#1B1F24"),
            border = c("#33FFFFFF"), popupBg = c("#2F7BE0"), dark = true),
        KeyboardTheme("mint", "Yalpiz", bg = c("#DFF5EE"), keyBg = c("#FFFFFF"), keyFuncBg = c("#B6E6D6"),
            keyPressed = c("#9ED9C6"), keyText = c("#103B30"), hint = c("#5F8F80"), accent = c("#00A383"), dark = false),
        KeyboardTheme("sunset", "Shafaq", bg = c("#FF7E5F"), bg2 = c("#FEB47B"), keyBg = c("#55FFFFFF"), keyFuncBg = c("#33FFFFFF"),
            keyPressed = c("#88FFFFFF"), keyText = c("#3A1606"), hint = c("#7A3A1C"), accent = c("#C2185B"),
            border = c("#33FFFFFF"), popupBg = c("#FFE0CF"), dark = false),
        KeyboardTheme("purple", "Binafsha", bg = c("#2A1B4A"), bg2 = c("#47267A"), keyBg = c("#4B3577"), keyFuncBg = c("#3A2861"),
            keyPressed = c("#6B54A0"), keyText = c("#F3EDFF"), hint = c("#BBA9E6"), accent = c("#B388FF"), accentText = c("#1B1033"),
            border = c("#33FFFFFF"), popupBg = c("#5A4288"), dark = true),
        KeyboardTheme("pink", "Pushti", bg = c("#FCE4EC"), keyBg = c("#FFFFFF"), keyFuncBg = c("#F8BBD0"),
            keyPressed = c("#F48FB1"), keyText = c("#4A1027"), hint = c("#A05A78"), accent = c("#E91E63"), dark = false),
        KeyboardTheme("forest", "Oʻrmon", bg = c("#1B3A2B"), bg2 = c("#24503A"), keyBg = c("#2F5E45"), keyFuncBg = c("#244A36"),
            keyPressed = c("#477A5E"), keyText = c("#EAF6EE"), hint = c("#A7CDB6"), accent = c("#8BC34A"), accentText = c("#10210F"),
            border = c("#33FFFFFF"), popupBg = c("#3A6B51"), dark = true),
        KeyboardTheme("coffee", "Qahva", bg = c("#3E2C23"), keyBg = c("#5A4136"), keyFuncBg = c("#4A352B"),
            keyPressed = c("#77584A"), keyText = c("#F6EBE3"), hint = c("#C9AE9E"), accent = c("#D7A86E"), accentText = c("#2A1C14"),
            border = c("#33FFFFFF"), popupBg = c("#6A4E41"), dark = true),
        KeyboardTheme("nord", "Nord", bg = c("#2E3440"), keyBg = c("#434C5E"), keyFuncBg = c("#3B4252"),
            keyPressed = c("#4C566A"), keyText = c("#ECEFF4"), hint = c("#9AA5B8"), accent = c("#88C0D0"), accentText = c("#2E3440"),
            border = c("#33FFFFFF"), popupBg = c("#4C566A"), dark = true),
        KeyboardTheme("dracula", "Drakula", bg = c("#21222C"), keyBg = c("#343746"), keyFuncBg = c("#282A36"),
            keyPressed = c("#44475A"), keyText = c("#F8F8F2"), hint = c("#9EA8D3"), accent = c("#FF79C6"), accentText = c("#21222C"),
            border = c("#33FFFFFF"), popupBg = c("#44475A"), dark = true),
        KeyboardTheme("solarized", "Solarized", bg = c("#EEE8D5"), keyBg = c("#FDF6E3"), keyFuncBg = c("#DDD6C1"),
            keyPressed = c("#CFC7AE"), keyText = c("#586E75"), hint = c("#93A1A1"), accent = c("#268BD2"), dark = false),
        KeyboardTheme("contrast", "Yuqori kontrast", bg = c("#000000"), keyBg = c("#000000"), keyFuncBg = c("#000000"),
            keyPressed = c("#444444"), keyText = c("#FFFF00"), keyFuncText = c("#FFFFFF"), hint = c("#FFFFFF"), accent = c("#FFFF00"),
            accentText = c("#000000"), border = c("#FFFFFFFF"), popupBg = c("#222222"), popupText = c("#FFFF00"), keyShadow = 0, dark = true),
        KeyboardTheme("uzbek", "Oʻzbekiston", bg = c("#0099B5"), bg2 = c("#1EB53A"), keyBg = c("#F2FFFFFF"), keyFuncBg = c("#CCFFFFFF"),
            keyPressed = c("#B3E5F2"), keyText = c("#0A3A47"), hint = c("#3C7B8B"), accent = c("#CE1126"),
            border = c("#26000000"), popupBg = c("#FFFFFF"), dark = false),
    )

    /** Ids shown in the theme picker, in order. */
    fun ids(): List<String> = listOf("auto") + (if (Build.VERSION.SDK_INT >= 31) listOf("dynamic") else emptyList()) + presets.map { it.id }

    fun displayName(ctx: Context, id: String): String = when (id) {
        "auto" -> "Avto (tizim)"
        "dynamic" -> "Material You"
        else -> presets.firstOrNull { it.id == id }?.name ?: id
    }

    fun isNight(ctx: Context) =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun theme(ctx: Context, id: String): KeyboardTheme = when (id) {
        "auto" -> if (isNight(ctx)) DARK.copy(id = "auto") else LIGHT.copy(id = "auto")
        "dynamic" -> if (Build.VERSION.SDK_INT >= 31) dynamic(ctx) else theme(ctx, "auto")
        else -> presets.firstOrNull { it.id == id } ?: LIGHT
    }

    private fun dynamic(ctx: Context): KeyboardTheme {
        val r = ctx.resources
        fun col(resId: Int) = if (Build.VERSION.SDK_INT >= 23) r.getColor(resId, ctx.theme) else 0
        val night = isNight(ctx)
        return if (night) KeyboardTheme(
            "dynamic", "Material You",
            bg = col(android.R.color.system_neutral1_900), keyBg = col(android.R.color.system_neutral1_700),
            keyFuncBg = col(android.R.color.system_accent2_800), keyPressed = col(android.R.color.system_neutral1_500),
            keyText = col(android.R.color.system_neutral1_50), hint = col(android.R.color.system_neutral2_300),
            accent = col(android.R.color.system_accent1_300), accentText = col(android.R.color.system_accent1_900),
            border = 0x33FFFFFF, popupBg = col(android.R.color.system_neutral1_600), dark = true,
        ) else KeyboardTheme(
            "dynamic", "Material You",
            bg = col(android.R.color.system_neutral1_100), keyBg = col(android.R.color.system_neutral1_10),
            keyFuncBg = col(android.R.color.system_accent2_100), keyPressed = col(android.R.color.system_accent1_200),
            keyText = col(android.R.color.system_neutral1_900), hint = col(android.R.color.system_neutral2_500),
            accent = col(android.R.color.system_accent1_600), accentText = Color.WHITE,
            border = 0x1F000000, popupBg = col(android.R.color.system_neutral1_10), dark = false,
        )
    }

    fun backgroundFile(ctx: Context) = File(ctx.filesDir, "bg.jpg")

    private var cachedBg: Bitmap? = null
    private var cachedBgStamp = -1L

    fun invalidateBackground() { cachedBg = null; cachedBgStamp = -1L }

    private fun loadBackground(ctx: Context): Bitmap? {
        val f = backgroundFile(ctx)
        if (!f.exists()) return null
        val stamp = f.lastModified() + f.length()
        if (cachedBg != null && stamp == cachedBgStamp) return cachedBg
        cachedBg = try { BitmapFactory.decodeFile(f.absolutePath) } catch (_: Throwable) { null }
        cachedBgStamp = stamp
        return cachedBg
    }

    fun resolve(ctx: Context, prefs: Prefs = Prefs(ctx)): ResolvedStyle {
        val sp = prefs.sp
        val t = theme(ctx, sp.getString("theme", "auto") ?: "auto")
        val style = when (sp.getString("key_style", "filled")) {
            "outline" -> KeyStyle.OUTLINE
            "flat" -> KeyStyle.FLAT
            else -> KeyStyle.FILLED
        }
        val useBg = sp.getBoolean("bg_image", false)
        return ResolvedStyle(
            theme = t,
            keyStyle = style,
            radiusDp = sp.getInt("key_radius", 8).coerceIn(0, 24).toFloat(),
            gapDp = sp.getInt("key_gap", 3).coerceIn(0, 8).toFloat(),
            fontScale = sp.getInt("font_scale", 100).coerceIn(70, 140) / 100f,
            bold = sp.getBoolean("key_bold", false),
            border = sp.getBoolean("key_border", false) || t.id == "contrast",
            background = if (useBg) loadBackground(ctx) else null,
            dim = sp.getInt("bg_dim", 30).coerceIn(0, 90) / 100f,
        )
    }
}
