package com.weelo.logistics.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color

data class IllustrationBlendPalette(
    val sectionBackground: Color,
    val haloColor: Color,
    val haloAlpha: Float,
    val cardContainer: Color,
    val edgeBlendColor: Color
)

enum class SectionBlendMode {
    NONE,
    PANEL,
    FULL_HOST
}

private val IllustrationPaletteSand = IllustrationBlendPalette(
    sectionBackground = Color(0xFFF7F3EA),
    haloColor = Color(0xFFF1E7D3),
    haloAlpha = 0.54f,
    cardContainer = Color(0xFFF8F4EC),
    edgeBlendColor = Color(0xFFE8DCC5)
)

private val IllustrationPaletteMint = IllustrationBlendPalette(
    sectionBackground = Color(0xFFEEF7F0),
    haloColor = Color(0xFFDDF0E2),
    haloAlpha = 0.50f,
    cardContainer = Color(0xFFF1F9F3),
    edgeBlendColor = Color(0xFFCDE5D4)
)

private val IllustrationPaletteSky = IllustrationBlendPalette(
    sectionBackground = Color(0xFFEEF5FB),
    haloColor = Color(0xFFDDEBF7),
    haloAlpha = 0.52f,
    cardContainer = Color(0xFFF2F8FD),
    edgeBlendColor = Color(0xFFD0E1F1)
)

private val IllustrationPalettePeach = IllustrationBlendPalette(
    sectionBackground = Color(0xFFFBF1EA),
    haloColor = Color(0xFFF6E4D8),
    haloAlpha = 0.50f,
    cardContainer = Color(0xFFFCF5EF),
    edgeBlendColor = Color(0xFFE9D4C4)
)

private val IllustrationPaletteSlate = IllustrationBlendPalette(
    sectionBackground = Color(0xFFF1F4F8),
    haloColor = Color(0xFFE3EAF2),
    haloAlpha = 0.48f,
    cardContainer = Color(0xFFF5F7FA),
    edgeBlendColor = Color(0xFFD6DEE8)
)

private val IllustrationPaletteLavender = IllustrationBlendPalette(
    sectionBackground = Color(0xFFF4F1FB),
    haloColor = Color(0xFFEAE3FA),
    haloAlpha = 0.50f,
    cardContainer = Color(0xFFF7F4FD),
    edgeBlendColor = Color(0xDDDCCFF3)
)

fun EmptyStateArtwork.blendPalette(): IllustrationBlendPalette = when (this) {
    EmptyStateArtwork.VEHICLES_FIRST_RUN -> IllustrationPaletteSand
    EmptyStateArtwork.FLEET_FILTER -> IllustrationPaletteMint
    EmptyStateArtwork.MATCHING_TRUCKS -> IllustrationPaletteSand
    EmptyStateArtwork.VEHICLE_DETAILS_NOT_FOUND -> IllustrationPaletteSand
    EmptyStateArtwork.CREATE_TRIP_NO_VEHICLES -> IllustrationPaletteMint
    EmptyStateArtwork.FLEET_MAP_IDLE -> IllustrationPaletteMint

    EmptyStateArtwork.DRIVERS_FIRST_RUN -> IllustrationPaletteSky
    EmptyStateArtwork.DRIVER_SEARCH -> IllustrationPaletteSky
    EmptyStateArtwork.DRIVER_DETAILS_NOT_FOUND -> IllustrationPalettePeach
    EmptyStateArtwork.MATCHING_DRIVERS -> IllustrationPaletteSky
    EmptyStateArtwork.ASSIGNMENT_BUSY_DRIVERS -> IllustrationPalettePeach
    EmptyStateArtwork.CREATE_TRIP_NO_DRIVERS -> IllustrationPalettePeach

    EmptyStateArtwork.TRIPS_FIRST_RUN -> IllustrationPaletteSlate
    EmptyStateArtwork.TRIP_FILTER -> IllustrationPaletteSlate
    EmptyStateArtwork.TRIP_HISTORY -> IllustrationPaletteSand
    EmptyStateArtwork.TRIP_DETAILS_NOT_FOUND -> IllustrationPaletteSlate

    EmptyStateArtwork.NOTIFICATIONS_ALL_CAUGHT_UP -> IllustrationPaletteLavender
    EmptyStateArtwork.REQUESTS_ALL_CAUGHT_UP -> IllustrationPaletteLavender
    EmptyStateArtwork.PERFORMANCE_NO_TRENDS -> IllustrationPaletteSky
    EmptyStateArtwork.PERFORMANCE_NO_FEEDBACK -> IllustrationPaletteLavender
    EmptyStateArtwork.EARNINGS_NO_TRIPS -> IllustrationPaletteSky
    EmptyStateArtwork.EARNINGS_PENDING_CAUGHT_UP -> IllustrationPaletteLavender
}

fun CardArtwork.blendPaletteOrNull(): IllustrationBlendPalette? = when (this) {
    CardArtwork.AUTH_ROLE_TRANSPORTER,
    CardArtwork.AUTH_ROLE_DRIVER,
    CardArtwork.AUTH_LOGIN_TRANSPORTER,
    CardArtwork.AUTH_LOGIN_DRIVER,
    CardArtwork.AUTH_OTP,
    CardArtwork.AUTH_SIGNUP_PHONE,
    CardArtwork.AUTH_SIGNUP_NAME,
    CardArtwork.AUTH_SIGNUP_LOCATION -> null

    CardArtwork.ADD_VEHICLE_TYPE_SELECTOR -> IllustrationPaletteMint
    CardArtwork.ADD_VEHICLE_TRUCK_CATEGORY -> IllustrationPaletteSand
    CardArtwork.DETAIL_VEHICLE -> IllustrationPaletteMint
    CardArtwork.DETAIL_DRIVER -> IllustrationPaletteSky
    CardArtwork.DETAIL_TRIP -> IllustrationPaletteSlate
    CardArtwork.DRIVER_PERFORMANCE -> IllustrationPaletteLavender
    CardArtwork.DRIVER_EARNINGS -> IllustrationPaletteSky
    CardArtwork.DRIVER_DOCUMENTS -> IllustrationPalettePeach
    CardArtwork.DRIVER_SETTINGS -> IllustrationPaletteSlate
}

fun blendPaletteForIllustrationRes(@DrawableRes illustrationRes: Int): IllustrationBlendPalette? {
    val artwork = enumValues<EmptyStateArtwork>().firstOrNull { it.drawableRes == illustrationRes }
    return artwork?.blendPalette()
}

