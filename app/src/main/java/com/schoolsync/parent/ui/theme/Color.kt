package com.schoolsync.parent.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * SchoolSync color tokens — Light & Dark, from the design system HTML.
 * All screens read from [LocalAppColors] so they auto-switch.
 */
data class AppColors(
    val isDark: Boolean,

    // Background gradient
    val bgStart: Color,
    val bgMid: Color,
    val bgEnd: Color,

    // Glass card
    val glass: Color,
    val glassBorder: Color,
    val glassHighlight: Color,

    // Accent
    val accent: Color,
    val accentSecondary: Color,
    val accentBg: Color,
    val slateBlue: Color,

    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,

    // Semantic
    val success: Color,
    val successBg: Color,
    val warning: Color,
    val warningBg: Color,
    val error: Color,
    val errorBg: Color,
    val info: Color,
    val infoBg: Color,

    // Extended
    val purple: Color,
    val purpleBg: Color,
    val coral: Color,
    val coralBg: Color,
    val teal: Color,
    val tealBg: Color,

    // Attendance
    val attPresent: Color,
    val attAbsent: Color,
    val attLeave: Color,
    val attHoliday: Color,
    val attVacation: Color,

    // Surface
    val surfaceDark: Color,
    val surfaceElevated: Color,
    val divider: Color,

    // Nav bar
    val navBg: Color,
    val navBorder: Color,
    val navActive: Color,
    val navDot: Color,
    val navInactive: Color,

    // Chat
    val chatSent: Color,
    val chatReceived: Color,

    // Banner gradients
    val banner1Start: Color,
    val banner1End: Color,
    val banner2Start: Color,
    val banner2End: Color,
    val banner3Start: Color,
    val banner3End: Color,

    // Shimmer
    val shimmerBase: Color,
    val shimmerHighlight: Color,

    // Pill
    val pillBg: Color,
    val pillText: Color,

    // Status bar
    val statusBarColor: Color,
    val lightStatusBar: Boolean,
)

// ─── DARK PALETTE ────────────────────────────────────────────────────────────
val DarkColors = AppColors(
    isDark = true,
    bgStart = Color(0xFF0E1822),
    bgMid = Color(0xFF162030),
    bgEnd = Color(0xFF1A2838),
    glass = Color(0x801E2A3A),
    glassBorder = Color(0x80324155),
    glassHighlight = Color(0x0AFFFFFF),
    accent = Color(0xFF8DBDD8),
    accentSecondary = Color(0xFF6A9AB8),
    accentBg = Color(0x1A8DBDD8),
    slateBlue = Color(0xFF3D4F5F),
    textPrimary = Color(0xFFE4ECF4),
    textSecondary = Color(0xFF8AA0B8),
    textTertiary = Color(0xFF4A6078),
    success = Color(0xFF4ADE80),
    successBg = Color(0x1A4ADE80),
    warning = Color(0xFFF5C842),
    warningBg = Color(0x14F5C842),
    error = Color(0xFFF87171),
    errorBg = Color(0x14F87171),
    info = Color(0xFF3B82F6),
    infoBg = Color(0x1A3B82F6),
    purple = Color(0xFFB49AEF),
    purpleBg = Color(0x14B49AEF),
    coral = Color(0xFFE89880),
    coralBg = Color(0x14E89880),
    teal = Color(0xFF5EDDCC),
    tealBg = Color(0x145EDDCC),
    attPresent = Color(0xFF4ADE80),
    attAbsent = Color(0xFFF87171),
    attLeave = Color(0xFFF5C842),
    attHoliday = Color(0xFF3B82F6),
    attVacation = Color(0xFFB49AEF),
    surfaceDark = Color(0xFF121E2B),
    surfaceElevated = Color(0xFF1E2A3A),
    divider = Color(0x0DFFFFFF),
    navBg = Color(0x73141E2A),
    navBorder = Color(0x66324155),
    navActive = Color(0xFFE4ECF4),
    navDot = Color(0xFF8DBDD8),
    navInactive = Color(0xFF4A6078),
    chatSent = Color(0xFF8DBDD8),
    chatReceived = Color(0xFF1E2A3A),
    banner1Start = Color(0xFF1A2838),
    banner1End = Color(0xFF2A4058),
    banner2Start = Color(0xFF0A2818),
    banner2End = Color(0xFF1A4828),
    banner3Start = Color(0xFF1A1630),
    banner3End = Color(0xFF2A2848),
    shimmerBase = Color(0xFF1E2A3A),
    shimmerHighlight = Color(0xFF2A3A4E),
    pillBg = Color(0xFF8DBDD8),
    pillText = Color(0xFF0E1015),
    statusBarColor = Color(0xFF0E1822),
    lightStatusBar = false,
)

// ─── LIGHT PALETTE ───────────────────────────────────────────────────────────
val LightColors = AppColors(
    isDark = false,
    bgStart = Color(0xFFC8D8E8),
    bgMid = Color(0xFFE8EEF4),
    bgEnd = Color(0xFFD4DEE8),
    glass = Color(0x73FFFFFF),           // rgba(255,255,255,0.45)
    glassBorder = Color(0xA6FFFFFF),     // rgba(255,255,255,0.65)
    glassHighlight = Color(0x1AFFFFFF),
    accent = Color(0xFF3D4F5F),
    accentSecondary = Color(0xFF6B8299),
    accentBg = Color(0x1A3D4F5F),       // rgba(61,79,95,0.1)
    slateBlue = Color(0xFF3D4F5F),
    textPrimary = Color(0xFF1A2A3A),
    textSecondary = Color(0xFF5A6A7A),
    textTertiary = Color(0xFF8A9AAA),
    success = Color(0xFF2D9D5A),
    successBg = Color(0x1E2D9D5A),
    warning = Color(0xFFD4880A),
    warningBg = Color(0x14D4880A),
    error = Color(0xFFCC3333),
    errorBg = Color(0x14CC3333),
    info = Color(0xFF2563EB),
    infoBg = Color(0x1A2563EB),
    purple = Color(0xFF6B52C4),
    purpleBg = Color(0x146B52C4),
    coral = Color(0xFFD4725C),
    coralBg = Color(0x14D4725C),
    teal = Color(0xFF1D9E8F),
    tealBg = Color(0x141D9E8F),
    attPresent = Color(0xFF2D9D5A),
    attAbsent = Color(0xFFCC3333),
    attLeave = Color(0xFFD4880A),
    attHoliday = Color(0xFF2563EB),
    attVacation = Color(0xFF6B52C4),
    surfaceDark = Color(0xFFF0EFED),
    surfaceElevated = Color(0xFFFFFFFF),
    divider = Color(0x0D000000),
    navBg = Color(0x59FFFFFF),           // rgba(255,255,255,0.35)
    navBorder = Color(0x8CFFFFFF),       // rgba(255,255,255,0.55)
    navActive = Color(0xFF3D4F5F),
    navDot = Color(0xFF3D4F5F),
    navInactive = Color(0xFF8A9AAA),
    chatSent = Color(0xFF3D4F5F),
    chatReceived = Color(0xFFFFFFFF),
    banner1Start = Color(0xFF3D4F5F),
    banner1End = Color(0xFF6B8299),
    banner2Start = Color(0xFF2D9D5A),
    banner2End = Color(0xFF6AE0A0),
    banner3Start = Color(0xFF6B52C4),
    banner3End = Color(0xFFB49AEF),
    shimmerBase = Color(0xFFE8EEF4),
    shimmerHighlight = Color(0xFFF4F6F8),
    pillBg = Color(0xFF3D4F5F),
    pillText = Color(0xFFFFFFFF),
    statusBarColor = Color(0xFFC8D8E8),
    lightStatusBar = true,
)

val LocalAppColors = staticCompositionLocalOf { DarkColors }

// ─── Backward-compatible top-level aliases ───────────────────────────────────
// These are used by screens that haven't migrated to LocalAppColors yet.
// They point to the DARK palette so existing code keeps compiling.
val BgStart = DarkColors.bgStart
val BgMid = DarkColors.bgMid
val BgEnd = DarkColors.bgEnd
val GlassCard = DarkColors.glass
val GlassBorder = DarkColors.glassBorder
val GlassHighlight = DarkColors.glassHighlight
val Accent = DarkColors.accent
val AccentSecondary = DarkColors.accentSecondary
val AccentBg = DarkColors.accentBg
val SlateBlue = DarkColors.slateBlue
val TextPrimary = DarkColors.textPrimary
val TextSecondary = DarkColors.textSecondary
val TextTertiary = DarkColors.textTertiary
val SuccessGreen = DarkColors.success
val SuccessGreenBg = DarkColors.successBg
val WarningAmber = DarkColors.warning
val WarningAmberBg = DarkColors.warningBg
val ErrorRed = DarkColors.error
val ErrorRedBg = DarkColors.errorBg
val InfoBlue = DarkColors.info
val InfoBlueBg = DarkColors.infoBg
val Purple = DarkColors.purple
val PurpleBg = DarkColors.purpleBg
val Coral = DarkColors.coral
val CoralBg = DarkColors.coralBg
val Teal = DarkColors.teal
val TealBg = DarkColors.tealBg
val AttendancePresent = DarkColors.attPresent
val AttendanceAbsent = DarkColors.attAbsent
val AttendanceLeave = DarkColors.attLeave
val AttendanceHoliday = DarkColors.attHoliday
val AttendanceVacation = DarkColors.attVacation
val SurfaceDark = DarkColors.surfaceDark
val SurfaceElevated = DarkColors.surfaceElevated
val DividerColor = DarkColors.divider
val NavBarBg = DarkColors.navBg
val NavBarBorder = DarkColors.navBorder
val NavItemActive = DarkColors.navActive
val NavDot = DarkColors.navDot
val NavItemInactive = DarkColors.navInactive
val ChatSent = DarkColors.chatSent
val ChatReceived = DarkColors.chatReceived
val BannerGrad1Start = DarkColors.banner1Start
val BannerGrad1End = DarkColors.banner1End
val BannerGrad2Start = DarkColors.banner2Start
val BannerGrad2End = DarkColors.banner2End
val BannerGrad3Start = DarkColors.banner3Start
val BannerGrad3End = DarkColors.banner3End
val ShimmerBase = DarkColors.shimmerBase
val ShimmerHighlight = DarkColors.shimmerHighlight
val PillBg = DarkColors.pillBg
val PillText = DarkColors.pillText
val TealDark = AccentSecondary
val TealLight = Accent
val SlateBlueLight = Color(0xFF4A6274)
