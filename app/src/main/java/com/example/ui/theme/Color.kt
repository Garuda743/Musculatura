package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// High Density Theme Colors (Tailwind Slate & Lime palette)
val HighDensityBg = Color(0xFF0A0A0C)          // #0A0A0C - Ultra dark slate-black
val HighDensitySurface = Color(0xFF0F172A)     // #0F172A - Deep slate-900
val HighDensitySurfaceVariant = Color(0xFF1E293B) // #1E293B - Slate-800
val HighDensityPrimary = Color(0xFFA3E635)     // #A3E635 - Lime-400
val HighDensitySecondary = Color(0xFFFB923C)   // #FB923C - Orange-400
val HighDensityTertiary = Color(0xFF38BDF8)    // #38BDF8 - Sky-400

val HighDensityOnPrimary = Color(0xFF000000)
val HighDensityOnSecondary = Color(0xFFFFFFFF)
val HighDensityOnBackground = Color(0xFFF8FAFC) // Slate-50 (White-ish)
val HighDensityOnSurface = Color(0xFFF8FAFC)

// Dark Premium Athletic Theme Colors mapped to High Density
val AthleticDarkBg = HighDensityBg // Super dark background
val AthleticSurface = HighDensitySurface // Deep slate surface cards
val AthleticSurfaceVariant = HighDensitySurfaceVariant // Slate-800 for highlights
val AthleticPrimary = HighDensityPrimary // Lime Green (Energetic)
val AthleticSecondary = HighDensitySecondary // Orange Accent
val AthleticTertiary = HighDensityTertiary // Sky Accent

val AthleticOnPrimary = HighDensityOnPrimary // Black text on neon green
val AthleticOnSecondary = HighDensityOnSecondary
val AthleticBackground = HighDensityBg
val AthleticOnBackground = HighDensityOnBackground
val AthleticOnSurface = HighDensityOnSurface

// Standard Light Theme Colors for compatibility
val AthleticLightBg = Color(0xFFF8FAFC)
val AthleticLightSurface = Color(0xFFFFFFFF)
val AthleticLightPrimary = Color(0xFF4F46E5) // Indigo
val AthleticLightSecondary = Color(0xFF0EA5E9)
val AthleticLightTertiary = Color(0xFFD946EF)
