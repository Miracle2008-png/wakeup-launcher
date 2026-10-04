# WakeUp Design System: "Held Light"

Status: v0.1 design direction. Companion to the source brief in [01-design-spec.md](01-design-spec.md). Every requirement in the brief maps to a section here; see the traceability table at the end.

## 1. The one idea

**The interface owns no light of its own.**

Every surface, glyph, shadow and highlight in WakeUp is lit by the *world* behind it. A dock is not a grey translucent bar; it is a piece of material sitting in this scene's air, at this hour, in this weather. When dusk comes, the dock warms. When fog rolls in, the icons lose a little contrast and gain a little softness, but never below accessibility floors. When the device is in Focus, the world is turned up and the interface is turned down, because the interface was only ever borrowed light.

This is what makes WakeUp "one coherent machine" instead of a launcher plus a wallpaper: there is a single **Light Model** (Section 4) that the scene renderer writes and the UI reads, every frame, through one small struct. Nothing in the UI picks its own colour at runtime.

Why this beats the usual approach: a wallpaper plus tint filter gets you matching colours. A shared light model gets you matching *behaviour*: shadows fall away from the same sun, glass takes the sky's colour, and a cabin light switching on at 17:40 also warms the folder you are holding.

## 2. Geometry: continuous corners, concentric with the glass

Rounded corners are the signature, so they are engineered, not styled.

**2.1 Continuous curvature.** Standard rounded rectangles join a straight edge to a circular arc with an abrupt curvature change, which the eye reads as a pinch. WakeUp shapes use a smoothed corner (a superellipse-like Bezier join, roughly 60% smoothing) so curvature eases in. On Android this is a custom `Shape` producing a cubic Bezier path (Compose's `RoundedCornerShape` is circular-arc only); on iPadOS it maps to the native continuous corner curve. Same intent, native implementation.

**2.2 The radius scale is proportional, not fixed.** `r = clamp(k * min(w, h), rMin, rMax)` with k by role:

| Role | k | Notes |
|---|---|---|
| Icon tile | 0.235 | Content-forward; tile reads as an object, not a box |
| Control (button, chip) | 0.5, capped | Fully pill below 40dp tall |
| Widget | 0.11, 24 to 36dp | Larger widgets get proportionally tighter radius so big surfaces do not balloon |
| Sheet / drawer | 0.045, 28 to 40dp top only | Bottom edge belongs to the device |
| Folder | 0.14 | Opens *from* the icon radius and interpolates |

**2.3 Concentric nesting.** Inner radius = outer radius minus padding. Always. A button inside a sheet inside the display never has the same radius as its parent; it has the parent's minus the gap.

**2.4 Concentric with the hardware.** The S25 Ultra has a rounded display. WakeUp reads the real corner radii (`WindowInsets.getRoundedCorner`, API 31+) and derives the dock, sheet and edge-glow geometry from them, so the dock sits concentric with the physical corner. No hard-coded phone radius. This is the kind of detail nobody names and everybody feels.

**2.5 Varied geometry, deliberately.** Not everything is a rounded rectangle. Planned shape vocabulary: the continuous rect (surfaces), the pill (controls, search), the **horizon cut** (a widget whose top edge follows the scene's horizon line), the **arch** (clock plate in Fjordlys, echoing cabin windows), and the **open edge** (surfaces with no boundary at all that fade into the scene via luminance, used in Focus). Rule: a screen may use at most two shape families so it stays calm.

**2.6 Spacing.** 4dp base unit, spacing scale 4, 8, 12, 16, 24, 32, 48, 72. Home grid margins are 20dp on the S25 Ultra (412dp viewport). Empty space is a feature: at default intensity at least 38% of the Home Screen area is unoccupied by UI.

## 3. Intensity governor

The brief: "never make every screen cinematic merely because the technology allows it". So intensity is a first-class runtime value, not a theme setting alone.

| Level | Name | World | UI |
|---|---|---|---|
| 0 | Still | Static render, no animation | Plain surfaces, springs only |
| 1 | Calm | Slow ambient drift (clouds, light), no particles | Subtle parallax |
| 2 | Living | Particles, weather, water, 30 to 60 fps scene | Depth, material light response |
| 3 | Immersive | Environmental interaction, tilt, touch ripples | Theme-specific transitions |
| 4 | Cinema | Everything, interface hidden | None |

A theme declares its *ceiling* and its *default*. The governor picks the live level every second from: user choice, Reduced Motion (forces 0 or 1, see 9), battery saver, thermal status, charging, battery under 15%, sustained frame-time misses, Focus state, and screen content density (a page with a full widget wall steps down; an empty page may step up). It moves one level at a time with a 600ms crossfade, never snaps, and the user can pin a level. Cinema is only ever entered by the user.

**Frame budget.** UI always targets the display rate (up to 120 Hz, 8.3ms). The world renders on its own clock: 30 fps by default, 60 at Immersive, paused when the screen is off, covered by a full-screen app, or the launcher is not resumed. If the UI misses a frame because of the world, the world loses, always.

## 4. The Light Model

One struct, written by the scene each frame (or on change at low levels), read by every themed surface:

```
Light {
  sunDir:     vec2     // direction shadows fall away from
  key:        color    // dominant light colour
  ambient:    color    // sky/fill
  warmth:     0..1     // practical lights (cabin, streetlamps) on/off blend
  haze:       0..1     // fog/rain density -> softens UI contrast within a11y floor
  luminance:  0..1     // average scene brightness behind UI -> text/glass polarity
  time:       0..1     // 0 = dawn, 1 = next dawn (continuous, not 4 buckets)
}
```

Consumers: glass tint and edge highlight, shadow direction and softness, icon tile shading, text colour polarity, widget accents, the dock, system-bar hints, even the haptic profile (fog = softer pulses). `luminance` plus per-region sampling decides when text flips between light and dark so clocks never sit unreadable on a bright cloud.

## 5. Materials

Material is chosen per element by *what it must do*, from a closed set of five. Glass is the exception, not the base.

1. **Air.** No surface at all. Text and icons sit directly on the world with a soft local scrim computed from `luminance`. Default for clock, date, Focus.
2. **Matte.** Opaque or near-opaque, low-saturation tinted by `ambient`. Default for widgets and drawer: cheap, legible, calm.
3. **Frost.** Blurred backdrop with 70 to 85% tint. Only for the dock, folders, sheets and the search field: places where something genuinely floats above busy content. Blur is a real backdrop blur (RenderEffect on Android 12+, with a pre-blurred static fallback below that).
4. **Lacquer.** Solid, deep, slight specular from `sunDir`. Reserved for premium moments: theme apply, the Studio canvas chrome. Used sparingly so it still feels special.
5. **Etched.** A pressed or incised look for toggles and sliders; reads as tactile without skeuomorphism.

Budget rule: at most one Frost layer may be on screen per layout column, and no Frost over Frost.

## 6. Typography

Open-licence faces only, so themes can ship without font licensing risk.

* **Display / clock:** a variable serif with optical-size and softness axes (Fraunces class). Used big and light, never bold. Axes animate subtly with time of day (weight drifts slightly heavier toward evening).
* **UI:** a neutral grotesk with tabular figures (Geist class). 13/16, 15/20, 17/24, 22/28 with a 1.2 scale; labels under icons are 11.5sp minimum and honour system font scale.
* **Data:** a mono with slashed zero for numerals in widgets (battery, temperature, countdowns).

Per-theme typography is a DNA field (`type.clock`, `type.ui`), constrained to a curated registry plus user-imported fonts that pass a readability check (x-height, contrast at 12sp). Clock styles: Cinematic (huge, light, tight tracking), Minimal (single line, numerals only), Editorial (stacked words, "Half past six"), Practical (24h, seconds optional). Text over the world always passes WCAG 4.5:1 against the *measured* backdrop, or the local scrim thickens until it does.

## 7. Motion language

Name: **Settle.** Everything moves like a heavy, well-damped object that has just been touched.

**Springs, not curves.** Four named springs (Compose `spring(dampingRatio, stiffness)`; iPadOS maps to the same response/damping):

| Token | Damping | Stiffness | Use |
|---|---|---|---|
| `settle` | 0.90 | 380 | Default for page, sheet, drawer |
| `glide` | 1.00 | 220 | Parallax, world drift, slow continuity |
| `snap` | 0.82 | 700 | Icon pickup, toggles, drag drop |
| `bloom` | 0.72 | 320 | Folder open, widget expand (a single gentle overshoot) |

**Rules, all enforced in the motion layer, not left to each screen:**
1. Interruptible: every animation is a spring on live state, so a touch mid-flight redirects it with velocity preserved.
2. Zero launch delay: tapping an app starts the launch intent in the same frame as the visual. Visuals chase the intent, never gate it.
3. Feedback inside 100ms. Anything slower than that is a loading state and is labelled as one.
4. Parallax is a function of *position*, never time: it only moves when the user's finger or the device moves. Idle motion belongs to the world, not the UI.
5. Max camera travel: world layers shift at most 3% of screen width per page, tilt at most 14px equivalent. Nothing ever scales more than 6% during a gesture. This is the anti-nausea rule.
6. Every moving element declares a reason in the DNA (`motion.reason`), or the lint rejects the theme.

**Page transitions.** Default: edge-to-edge page slide with a 1:0.94 depth ratio between icon layer and background (parallax). Environment-continuous themes (Fjordlys, Salt Flat) treat pages as a pan across one wide scene: the world is 1.4x screen width and pages slide it. Cuts between unrelated pages are forbidden in those themes.

## 8. Theme DNA

DNA is a versioned, declarative, diffable document (JSON) plus a bundle of assets. It is the *only* thing a theme is. See `theme-dna/schema.json` and the reference theme `theme-dna/fjordlys.dna.json`.

**Sections** (maps 1:1 to the brief's list): `world` (wallpaper, scenes, layers, lighting, depth), `weather`, `time`, `device`, `motion`, `type`, `palette`, `widgets`, `icons`, `sound`, `haptics`, `layout`, `interactions`, `intensity`, `moments`, `modes`.

**Modes** are not separate themes; they are *named overrides* of a base DNA, so switching is a crossfade of parameters, not a reload:

| Mode | What changes |
|---|---|
| Pure | Intensity 0, original icons, Matte surfaces, the quiet default |
| Minimal | Hides widgets and labels, clock only, intensity 1 |
| Living | The theme as designed, intensity 2 |
| Immersive | Environmental interaction on, theme transitions, intensity 3 |
| Cinema | Interface hidden, full-bleed world, sound on, intensity 4 |
| Focus | World up, UI to clock plus one line; DND-aware |

**World composition.** A world is 4 to 9 depth-ordered layers (sky, far, mid, water, near, foreground particles, light, overlay), each with parallax factor, wind response, and light response. Layer sources can be pre-rendered bitmaps, procedural shaders (AGSL on Android 13+), lightweight particle systems, or short video loops. Technique per layer is the theme's choice. Hybrid is the norm: painted/photographic base, shader water and fog, particle rain.

**Time and weather are continuous.** Palette and light are defined as keyframes on `time: 0..1` and interpolated in a perceptual colour space (OKLCH), not stepped through four images. Weather is a vector `{rain, fog, cloud, snow, wind, clear}` each 0..1, fed by real data when the user allows it (a weather provider, cached), or by a manual override, and cross-faded over 20 to 60 seconds.

**Cinematic moments.** Each is `{id, trigger, cooldown, probability, duration, minIntensity}`. A global governor allows at most one per 25 minutes of active screen time, never while the user is mid-gesture, never at intensity under 2, never when Reduced Motion is on, and skips if the last one has not been dismissed from memory. Rare by construction.

## 9. Accessibility, built into tokens

* **Contrast floors** are checked against the live backdrop, not a static colour. Text 4.5:1 (3:1 for 24sp+), icons and controls 3:1. When `haze` would break a floor, the local scrim thickens; the scene is never allowed to win over legibility.
* **Reduced Motion is a mode, not a switch.** It forces intensity to 0 or 1, replaces springs with 120ms linear-eased fades, removes all parallax and tilt, freezes particles, turns clouds and water into a slow 90-second dissolve between two stills, disables cinematic moments, and replaces haptic flourishes with one standard click. It follows the system setting and can be overridden per theme.
* **Touch targets** 48dp minimum, 56dp for primary actions; icon grid cells are never smaller than 64dp on any text scale.
* **Text scaling** to 200%: the grid reflows (4 columns to 3 to 2), labels wrap to two lines, widgets switch to their compact variant; nothing truncates silently.
* **Screen reader semantics:** every scene gets a one-line plain description ("Lake at dusk, light rain"), exposed once on theme apply; decorative layers are hidden from the accessibility tree; the editor is operable with TalkBack through move/resize actions as well as drag.
* **Sound and haptics** each have a master switch independent of the theme, and no information is conveyed by sound or vibration alone.

## 10. Sound and haptics

**Sound.** Four independent controls: global volume, per-theme volume, interaction sounds, ambient sounds. All off with one tap. Hard rules in the audio layer, not per theme:
* No audio plays while the device is locked or the screen is off. The audio focus is released on `ACTION_SCREEN_OFF` and on lock; ambient resumes only after unlock and only if the launcher is visible.
* Ambient is a ducking loop that yields to any other audio (music, calls, video) rather than competing.
* Respect the system ringer mode and DND.
* Interaction sounds are under 300ms, short natural textures (wood, glass, water, wind) never beeps.

**Haptics.** Purpose-only. A closed vocabulary: `tick` (detent in a slider or page snap), `settle` (drop completes), `confirm` (theme applied), `soft` (long-press recognised). Uses the system `VibrationEffect` predefined effects where available so it respects the S25 Ultra's actuator. No haptic for an animation merely existing. Global strength slider plus off.

## 11. Screens

### 11.1 Home
Default theme (Linen, Pure mode) is a recognisable Android home: 5x6 grid, dock of 4, a clock/date block, one weather line, page dots. The depth is in the details: tiles are lit from the scene, labels sit in Air material, and swiping has parallax between icon layer and world.

### 11.2 Dock
Frost, concentric with the display corner. Adaptive: in landscape it moves to the side; with a full widget page it shrinks its blur radius; in Focus it dissolves to Air with just glyphs. It never reorders itself without being asked.

### 11.3 Folders
Open from the icon with `bloom`: the tile's continuous corner interpolates to the folder's, the folder is Frost lit by the scene, contents ease out in a 12ms-staggered ripple (max 80ms total). Close is the same path reversed and interruptible. Drag in/out is physical (snap spring, neighbours part).

### 11.4 App drawer
Swipe up anywhere on the home or tap the dock handle. Instant: the list is already laid out, the open is a spring on a layer, and focus can land in search immediately. Organisation: a left alphabet spine for scrubbing, an adaptive "Now" row (3 predicted apps, local only), and optional user categories. No cinematic effects inside the list itself; only the backdrop scrim and the sheet edge carry the theme.

### 11.5 Search
One field, one ranked list. Sources: apps, launcher actions, settings, themes, widgets, and (with permission) contacts and files via system providers. Index is local, in-memory trie plus on-disk, first result on the first keystroke (budget: under 16ms per keystroke for the app tier). Ranking: exact prefix, then fuzzy token match, then recency and frequency, with the strongest app hit pinned so Enter always launches it. Typed commands like `theme fjordlys` or `fog 70` act directly. No animation beyond the field's focus ring and the list's fade; search is a tool, not a scene.

### 11.6 Widgets
A custom ecosystem, three layers:
* **Content** (always legible, Matte or Air): clock, weather, calendar next-up, battery, now playing, notes, countdown.
* **Environment** (optional): a weather widget can place its glyph *in* the scene (rain on the glass shown as streaks within the widget bounds) while the temperature and condition stay as plain, high-contrast text.
* **Link:** widgets exchange a small signal bus (`weather.changed`, `time.phase`, `media.playing`) so they react to each other without coupling. Sizes: 2x1, 2x2, 4x2, 4x4, plus a resize handle with a live reflow preview. States: idle, touch (1.5% depress), swipe (content pans), expanded (bloom to a sheet), data-change (a 240ms numeric roll), theme transition, weather change, device-state change. A widget may opt out of any state; the lint warns if it animates more than three.

### 11.7 Theme Browser
A cinematic gallery. Full-bleed, one theme at a time, vertical paging; collections sit as a quiet row of arches along the bottom. Tapping holds the world for a live preview on a real-device frame (11.10). Apply = a 700ms transition where the old world dissolves through the light model (key colour crossfade first, layers second), then a single `confirm` haptic. Favourites, Recents, Saved Setups, Individual Assets and Complete Themes are tabs of the same gallery, so there is one mental model.

### 11.8 Studio
Two levels. **Simple:** six large controls with live feedback on the actual home: Time, Weather, Mood (a 2D pad: calm to dramatic, warm to cold), Motion (Still to Alive), Type, Sound. **Deep:** reveals the layer stack, per-layer depth/parallax/wind/blend, particle systems, light model keyframes, palette in OKLCH, motion tokens, sound mix, haptic map, intensity ceiling. A persistent **Compare** gesture (hold) flashes the original.

### 11.9 Laboratory
Free-form, intentionally experimental sandbox with live shader/particle/motion parameters, a scrubbable day-length timeline, and an "extremes" toggle that removes the intensity and anti-nausea limits *inside the lab only*, with a visible badge. Nothing from the lab reaches the Home Screen without passing the same lint, a11y and performance checks every theme passes.

### 11.10 Live Preview
A real-device frame (412x891dp, concentric corners), driven by the same renderer as production, not a video. Supports swipe, widget interaction, pages, a day/night scrubber, weather chips, a Reduced Motion toggle, and a sound preview that is muted until the user taps it.

### 11.11 Remix
A text prompt creates a **branch** of the DNA: the original stays immutable, the result is `Fjordlys (colder)`, shown side by side with a parameter diff ("fog +0.35, wind -0.20, key hue 28 to 214, ambient volume -6dB"). Local-first: the interpreter maps language to DNA parameter deltas with a small on-device model or rules-based parser. A cloud model is an optional plug-in; nothing requires it.

### 11.12 Version history
Named snapshots, content-addressed, with preview and restore, duplicate, and duplicate-as-new-theme. History is a timeline strip of mini live previews.

### 11.13 Natural-language creation and reference images
Optional pipeline: prompt -> scene recipe (layers, palette keyframes, weather, moments) assembled from a licensed library of procedural layers and base art, optionally with local diffusion for a base plate. A reference image yields palette, composition guides and optionally a foundation layer with depth estimation. A provenance record travels with each asset: where it came from, who declared rights, and an "I have the right to use this" gate. Uploading does not grant or imply ownership, and themes built from third-party images are marked **personal-use** and cannot be published.

### 11.14 Cinema and Focus
**Cinema:** entered by a long press on the world or from the Browser. Interface fades to Air then to nothing, the world scales 3% and picks up a layer of environmental motion, ambient sound fades in (never while locked), a single tap or swipe-down exits. Moments are allowed. **Focus:** same world, intensity 1, UI reduced to the clock and one chosen line; notifications hold unless allowed.

### 11.15 Editor
Entry: long-press on empty space (familiar). Then it becomes the design environment: a magnetic grid, alignment guides, a size ruler, per-widget inspector, a layer/depth tray, and an "intelligent position" suggestion that nudges a widget off a bright patch of sky that would cost it contrast. Everything is also doable with taps for accessibility.

### 11.16 Onboarding
Four short screens, skippable. (1) A world fades up with the wordmark: what WakeUp is. (2) "Make WakeUp your home" with the single system role request, shown just before the system prompt. (3) Pick a mood (three worlds, not a gallery). (4) A permission summary: nothing is requested up front; location (weather), notifications, usage stats are each explained and optional. Under 40 seconds.

### 11.17 Setups and Collections
A setup is a bundle `{theme+mode, pages, widgets, icons, wallpaper, sounds, haptics, layout, settings}` identified by content hash. Quick switch from a dock gesture (two-finger swipe) or search. Collections are user folders of anything (themes, wallpapers, widgets, setups). **Surprise Me** selects from a curated, already-installed set only, weighted by time and weather and never repeating the last three.

### 11.18 Automation
Rules `when [time | day | weather | battery | charging | device state | place] then [apply setup | set mode | pin intensity]` created only by the user. Each rule shows what it will do and when it last ran. Nothing ever changes the setup without a rule the user wrote. Location is only offered as coarse "home/work/other" selected by the user.

## 12. Palette

Automatic extraction (k-means in OKLab on a downscaled copy, with region weighting so a bright sun does not become the accent), then **artistic grading**: the extracted palette is pushed toward the theme's `grade` (lift shadows toward a hue, compress highlights, cap chroma), then manual override per slot (key, ambient, accent, ink, surface). Palettes are exposed at four scopes: theme, widget, icon, UI. On Android, system UI recolouring uses only supported mechanisms (Material You dynamic colour seeds via the wallpaper colours API); no hacks, no accessibility-service tricks.

## 13. App icons

Default: the app's own icon, unaltered, on a continuous-corner grid with consistent optical size (icons are normalised so a circular glyph and a full-bleed square occupy equal visual weight). Refinements are *treatments*: Light (tile shaded by `sunDir`), Mono (single-hue tint from the theme palette), Glass-edge. Icon packs and per-icon overrides are supported. A theme may define an icon language, but a **recognisability guard** compares each treated icon to the original (colour-histogram and silhouette similarity) and falls back to the original if it fails.

## 14. Platforms

**S25 Ultra (Android).** 6.9" display, 1440x3120, around 498 ppi, 19.5:9, up to 120 Hz LTPO; about 412x891dp in the default size. Design uses: the large flagship display's reachable-zone mapping (primary controls in the lower 55%), edge-to-edge with display-cutout and rounded-corner insets, high-refresh adaptive rendering, HDR brightness headroom for sun and lights where the compositor allows it, and the Always-On Display remains the system's, we do not touch it. Built on public Android APIs only: `HOME` role, `LauncherApps`, `AppWidgetHost`, RenderEffect/AGSL, `WallpaperService`. No root, no hidden APIs, no accessibility-service abuse.

**iPad (iPadOS).** Not a port. A separate native design (SwiftUI, with Metal for the worlds): a wide composition where a theme world is a *landscape*, not a stretched phone; widgets arranged on a canvas; Stage Manager and Split View aware layouts with the world continuing behind windows; the Studio as a full workspace with Apple Pencil for painting light and wind direction directly on the scene; Cinema Mode that uses the whole display; hardware keyboard shortcuts. iPadOS has no launcher role, so the iPad experience is a first-class **WakeUp app plus widgets, wallpapers, Lock/Home Screen assets and Cinema Mode**, honestly presented as such. Shared across both: Theme DNA, the Light Model, the Motion tokens and the brand.

## 15. Brand

Wordmark: lowercase "wakeup" set in the display serif at light weight, with the "u" opened at the top like a horizon; one-colour, always on the world rather than on a chip. Voice: plain, quiet, specific ("Fog in about twenty minutes"), never exclamation marks, never "AI-powered".

## 16. Reference worlds

| Theme | Idea | Ceiling | Pages |
|---|---|---|---|
| **Linen** (default) | Morning light on pale paper; the quiet baseline. Pure mode by default | 1 | Separate |
| **Fjordlys** | A Nordic lake from dusk into night, fog, light rain, cabin lights that come on | 3 | One wide scene |
| **Salt Flat** | Desert dawn, long shadows, wind-blown dust, a distant heat shimmer | 3 | One wide scene |
| **Night Freight** | A single train crosses a plain under stars every so often | 3 | Separate |
| **Kelp Hour** | Underwater light shafts, slow drift, silent | 2 | Separate |
| **Monsoon Terrace** | Warm rain on glass over a blurred city, no figures | 3 | Separate |

## 17. Anti-pattern lint

Themes and screens are checked automatically; failures block publishing: more than two shape families on a screen; Frost over Frost; any text under its contrast floor on the measured backdrop; more than three simultaneously animating widgets; a moving element with no declared reason; parallax travel over the limits; any audio path reachable while locked; cinematic moments exceeding the governor; gradients used where a single flat tone would carry the surface.

## 18. Requirement traceability

| Brief section | Where handled |
|---|---|
| Core principle, one machine | 1, 4 |
| Rounded smooth corners | 2 |
| Default experience, intensity | 3, 11.1 |
| Home, pages, transitions | 7, 11.1 |
| Icons, dock, folders | 11.2, 11.3, 13 |
| Drawer, search | 11.4, 11.5 |
| Widgets and widget motion | 11.6 |
| Living worlds, interaction, day/night, weather | 8, 4 |
| Typography | 6 |
| Sound, haptics | 10 |
| Theme DNA, modes | 8 |
| Browser, Studio, Lab, Remix, history | 11.7 to 11.12 |
| NL creation, reference images | 11.13 |
| Cinema, Focus, Moments | 8, 11.14 |
| Personal media | 11.13 (fit-not-filter: crop guides, never auto-retouch) |
| Palette | 12 |
| Automation, Collections, Surprise Me, Setups | 11.17, 11.18 |
| Live preview, Editor | 11.10, 11.15 |
| Motion, Material | 7, 5 |
| Accessibility | 9 |
| Onboarding | 11.16 |
| Platform differences | 14 |
| Final standard | 17 |
