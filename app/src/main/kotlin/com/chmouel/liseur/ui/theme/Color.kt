package com.chmouel.liseur.ui.theme

import androidx.compose.ui.graphics.Color

// Liburuville palette: cream paper, warm ink, raspberry accent, gold highlights.
// (Forked from Liseur; the names below are kept so the rest of the code is untouched.)
//
// Paper is white, as paper is. The warmth is in the ink and the leather,
// where it belongs: a page tinted the colour of a page is one of those
// things that looks charming for a second and grubby thereafter, and it
// makes every photograph on it -- which is to say every book cover --
// look as though the screen needs cleaning.
val Paper = Color(0xFFF5F1E6)

// Everything M3 stacks on top of the page: a menu, a sheet, a card, an
// app bar that has been scrolled under. The steps are small and warm --
// paper in shadow, not a grey card laid on a white desk. Cards fill
// most of a settings screen, and at any darker a tint they stop reading
// as part of the page and start reading as slabs sitting on it.
val PaperRaised = Color(0xFFF2EDE2)
val PaperCard = Color(0xFFEDE7D8)
val PaperHigh = Color(0xFFE8E1D1)
val PaperHighest = Color(0xFFE3D9C6)
val PaperDim = Color(0xFFD9D1BD)
val PaperWarm = Color(0xFFEDE7D8)
val Rule = Color(0xFFD9D1BD)
val RuleStrong = Color(0xFF8A8476)
val Ink = Color(0xFF1B1A17)
val InkSoft = Color(0xFF5E5A50)
val InkInverse = Color(0xFF2A2825)
val PaperInverse = Color(0xFFF5F1E6)
val Leather = Color(0xFFB23A6E)
val LeatherLight = Color(0xFFFFD9E7)
val LeatherDark = Color(0xFF5E1636)

// The colour of "this one is chosen".
//
// M3 draws every selected chip and segmented button from the secondary
// family, and in this app that is the only thing the family is ever
// used for. It used to be sage, which made the one selected thing on a
// screen the one green thing on it -- not a second accent so much as a
// stray one, since nothing else anywhere was green. Selection now looks
// like the app: leather, washed out enough to sit under dark text.
val LeatherSoft = Color(0xFF9A3460)
val LeatherWash = Color(0xFFF6D5E2)
val LeatherInk = Color(0xFF4A1230)

val Teal = Color(0xFF8A6A12)
val TealLight = Color(0xFFF6E3A8)

// Night is the room in the library bar's night picture, so that picture
// reads as a window into the app rather than a navy sticker on it. It
// used to be the day palette's warm neutrals walked down, and brown is
// exactly what the painted navy wall clashed with.
//
// The wall gives the surfaces, muted and kept to one hue so cards do not
// drift purple or teal as they rise. The sofa gives the raised ones, the
// moon in the frame the text, and the cushion the one warm accent: a cool
// room with a single warm thing in it, as the picture has.
val NightSurface = Color(0xFF111111)
val NightSurfaceLowest = Color(0xFF0B0B0B)
val NightSurfaceLow = Color(0xFF151514)
val NightSurfaceContainer = Color(0xFF1A1917)
val NightSurfaceHigh = Color(0xFF22211F)
val NightSurfaceHighest = Color(0xFF2B2A27)
val NightSurfaceBright = Color(0xFF34322F)
val NightVariant = Color(0xFF3A3833)
val NightText = Color(0xFFEDE7D8)
val NightTextSoft = Color(0xFFA8A39A)
val NightRule = Color(0xFF2E2C28)
val NightRuleStrong = Color(0xFF6E6A62)
val LeatherNight = Color(0xFFF2A7C3)
val LeatherNightContainer = Color(0xFF7E2A52)

// The cushion again, as the lamp catches it: the accent lifted just
// enough to stay readable on the slate below.
val LeatherLamplight = Color(0xFFF7C3D6)

// Icon discs and the selected chip or segment. Lighter than the sofa it
// comes from on purpose: the default card is NightSurfaceHighest, and a
// selected filter chip has no border, so anything closer to the card
// would leave the chip with no shape.
val SlateNight = Color(0xFF4A2A3B)
val TealNight = Color(0xFFE8B83A)
val TealNightDeep = Color(0xFF5A4512)

