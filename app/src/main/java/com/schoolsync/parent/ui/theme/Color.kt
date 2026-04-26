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

    // Banner foreground (text/icons over saturated banner gradients)
    // Always white in both palettes — banners use saturated colors that
    // contrast with white in either theme.
    val onBanner: Color,
    val onBannerMuted: Color,

    // Status bar
    val statusBarColor: Color,
    val lightStatusBar: Boolean,
)

// ─── DARK PALETTE — Corporate Navy + Gold ────────────────────────────────────
val DarkColors = AppColors(
    isDark = true,
    bgStart = Color(0xFF0A1428),
    bgMid = Color(0xFF0F1F3A),
    bgEnd = Color(0xFF13294B),
    glass = Color(0x801E3352),
    glassBorder = Color(0x80445A7A),
    glassHighlight = Color(0x0DFFFFFF),
    accent = Color(0xFFD4AF37),
    accentSecondary = Color(0xFFB8941F),
    accentBg = Color(0x1AD4AF37),
    slateBlue = Color(0xFF2A4266),
    textPrimary = Color(0xFFF5EEDB),
    textSecondary = Color(0xFFA8B5C8),
    textTertiary = Color(0xFF5A6A80),
    success = Color(0xFF3FBE6D),
    successBg = Color(0x1A3FBE6D),
    warning = Color(0xFFF59E0B),
    warningBg = Color(0x14F59E0B),
    error = Color(0xFFE74C3C),
    errorBg = Color(0x14E74C3C),
    info = Color(0xFF3B82F6),
    infoBg = Color(0x1A3B82F6),
    purple = Color(0xFF9B7ED8),
    purpleBg = Color(0x149B7ED8),
    coral = Color(0xFFE89880),
    coralBg = Color(0x14E89880),
    teal = Color(0xFF4FC3A1),
    tealBg = Color(0x144FC3A1),
    attPresent = Color(0xFF3FBE6D),
    attAbsent = Color(0xFFE74C3C),
    attLeave = Color(0xFFF59E0B),
    attHoliday = Color(0xFF3B82F6),
    attVacation = Color(0xFF9B7ED8),
    surfaceDark = Color(0xFF0D1A30),
    surfaceElevated = Color(0xFF16263F),
    divider = Color(0x0DFFFFFF),
    navBg = Color(0x7314263F),
    navBorder = Color(0x66445A7A),
    navActive = Color(0xFFD4AF37),
    navDot = Color(0xFFD4AF37),
    navInactive = Color(0xFF5A6A80),
    chatSent = Color(0xFFD4AF37),
    chatReceived = Color(0xFF16263F),
    banner1Start = Color(0xFF13294B),
    banner1End = Color(0xFF2A4266),
    banner2Start = Color(0xFF0A2F1F),
    banner2End = Color(0xFF1E5038),
    banner3Start = Color(0xFF4A3610),
    banner3End = Color(0xFF8B6E1F),
    shimmerBase = Color(0xFF16263F),
    shimmerHighlight = Color(0xFF22365A),
    pillBg = Color(0xFFD4AF37),
    pillText = Color(0xFF0A1428),
    onBanner = Color(0xFFFFFFFF),
    onBannerMuted = Color(0xB3FFFFFF),
    statusBarColor = Color(0xFF0A1428),
    lightStatusBar = false,
)

// ─── LIGHT PALETTE — Corporate Navy + Gold ───────────────────────────────────
val LightColors = AppColors(
    isDark = false,
    bgStart = Color(0xFFF7F4ED),
    bgMid = Color(0xFFFBFAF5),
    bgEnd = Color(0xFFF0EAD8),
    glass = Color(0xCCFFFFFF),
    glassBorder = Color(0xA6E8DDB8),
    glassHighlight = Color(0x1AFFFFFF),
    accent = Color(0xFF0F2949),
    accentSecondary = Color(0xFF1E4372),
    accentBg = Color(0x1A0F2949),
    slateBlue = Color(0xFF0F2949),
    textPrimary = Color(0xFF0A1428),
    textSecondary = Color(0xFF4A5568),
    textTertiary = Color(0xFF8A95A5),
    success = Color(0xFF1B8742),
    successBg = Color(0x1E1B8742),
    warning = Color(0xFFB8740A),
    warningBg = Color(0x14B8740A),
    error = Color(0xFFB82D25),
    errorBg = Color(0x14B82D25),
    info = Color(0xFF1E4B9E),
    infoBg = Color(0x1A1E4B9E),
    purple = Color(0xFF5A3FA8),
    purpleBg = Color(0x145A3FA8),
    coral = Color(0xFFC25C3E),
    coralBg = Color(0x14C25C3E),
    teal = Color(0xFF1A8570),
    tealBg = Color(0x141A8570),
    attPresent = Color(0xFF1B8742),
    attAbsent = Color(0xFFB82D25),
    attLeave = Color(0xFFB8740A),
    attHoliday = Color(0xFF1E4B9E),
    attVacation = Color(0xFF5A3FA8),
    surfaceDark = Color(0xFFEDE6D0),
    surfaceElevated = Color(0xFFFFFFFF),
    divider = Color(0x0D000000),
    navBg = Color(0x59FFFFFF),
    navBorder = Color(0x8CE8DDB8),
    navActive = Color(0xFF0F2949),
    navDot = Color(0xFFB8941F),
    navInactive = Color(0xFF8A95A5),
    chatSent = Color(0xFF0F2949),
    chatReceived = Color(0xFFFFFFFF),
    banner1Start = Color(0xFF0F2949),
    banner1End = Color(0xFF3D5B82),
    banner2Start = Color(0xFF1B8742),
    banner2End = Color(0xFF3FBE6D),
    banner3Start = Color(0xFF8B6E1F),
    banner3End = Color(0xFFC9A743),
    shimmerBase = Color(0xFFF0EAD8),
    shimmerHighlight = Color(0xFFFAF6E8),
    pillBg = Color(0xFF0F2949),
    pillText = Color(0xFFFFFFFF),
    onBanner = Color(0xFFFFFFFF),
    onBannerMuted = Color(0xB3FFFFFF),
    statusBarColor = Color(0xFFF7F4ED),
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
