package com.araujo.karaoke_ia.ui.theme

import androidx.compose.ui.graphics.Color

// ── Karaokê IA — palco escuro com identidade neon magenta ────────────────────

// ── Fundos e Superfícies Escuras (Slate / Charcoal) ─────────────────────────
val DarkCharcoalBg    = Color(0xFF0D0820)   // Fundo geral profundo
val DarkSlateSurface  = Color(0xFF170D30)   // Superfície base
val DarkCardSurface   = Color(0xD91C1238)   // Cards principais
val DarkCardElevated  = Color(0xE62A1946)   // Cards em destaque / Hover
val DarkCardBorder    = Color(0x26FFFFFF)   // Borda sutil de card (15% branco)
val DarkPillBg        = Color(0x33252A3B)   // Fundo de botões em pílula inativos
val DarkPillBorder    = Color(0x33FFFFFF)   // Borda de pílula inativa

// ── Neon magenta da identidade visual ────────────────────────────────────────
val GoldPrimary       = Color(0xFFFF2DAB)   // Magenta primário (foco, textos chave)
val GoldAccent        = Color(0xFF9B5DE5)   // Rosa intenso (botões, badges)
val GoldDark          = Color(0xFFB5105C)   // Magenta escuro para degradês
val GoldGlow          = Color(0xFFFF83BE)   // Brilho rosa intenso
val GoldBadgeBg       = Color(0x2AFF2D96)   // Fundo translúcido magenta
val GoldBadgeBorder   = Color(0x80FF2D96)   // Borda magenta translúcida
val FocusGold         = Color(0xFFFF2DAB)   // Cor padrão do anel de foco da TV

// ── Bronze e Tons Quentes de Palco ──────────────────────────────────────────
val BronzeSurface     = Color(0xFF28133F)   // Superfície ameixa para cards especiais
val BronzeBorder      = Color(0xFF624082)   // Borda magenta escura
val BronzeGradient1   = Color(0xFF371747)   // Degradê ameixa início
val BronzeGradient2   = Color(0xFF190D2E)   // Degradê ameixa fim

// ── Tipografia e Neutros ────────────────────────────────────────────────────
val TextWhite         = Color(0xFFFFFFFF)   // Texto branco puro
val TextOffWhite      = Color(0xFFEDEDF2)   // Texto principal quase branco
val TextMuted         = Color(0xFFC4B8D8)   // Texto secundário cinza médio
val TextSubtle        = Color(0xFF9D8DB5)   // Texto terciário / legendas

// ── Cores Funcionais ────────────────────────────────────────────────────────
val HeartRed          = Color(0xFFEF4444)   // Coração de favoritos
val ScoreGreen        = Color(0xFF34D399)   // Verde positivo
val ScoreGold         = GoldPrimary        // Destaque de pontuação magenta

// ── Aliases de Retrocompatibilidade ─────────────────────────────────────────
val DiscoBg           = DarkCharcoalBg
val DiscoSidebar      = DarkSlateSurface
val DiscoCard         = DarkCardSurface
val DiscoCardAlt      = DarkCardElevated
val DiscoGold         = GoldPrimary
val DiscoGreen        = ScoreGreen
val DiscoAmber        = GoldAccent
val NeonPurple        = GoldAccent
val NeonMagenta       = GoldPrimary
val NeonPink          = HeartRed
val NeonCyan          = GoldPrimary
val DeepSpace         = DarkCharcoalBg
val NightBlue         = DarkSlateSurface
val GlassSurface      = DarkCardSurface
val GlassBorder       = DarkCardBorder
val SidebarSelected   = GoldPrimary
val ElectricYellow    = GoldPrimary
val Purple80          = GoldPrimary
val PurpleGrey80      = TextMuted
val Pink80            = GoldAccent
