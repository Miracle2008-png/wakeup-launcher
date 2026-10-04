# WakeUp Design Specification (Part 1 of 3)

> Engineering and security requirements will be provided separately. This document is the source brief and is kept verbatim. Do not lose any of these requirements when implementing the design system.

You are designing **WakeUp**, a completely independent product/project from any previous project using the same name.

WakeUp is a premium Android launcher and device-customization ecosystem, initially designed around the Samsung Galaxy S25 Ultra, with a genuinely native iPadOS companion experience planned as a separate platform implementation.

This is NOT a generic launcher with a wallpaper selector attached.

This is a complete visual/interaction system built around a concept called **Theme DNA**.

The design philosophy is:

**Build it like a Pagani or Koenigsegg.**

Do not decorate an ordinary product and call it premium. The beauty must come from the underlying design system, interaction design, engineering constraints, motion language, typography, spacing, materials, responsiveness and attention to detail.

The final product should feel:

* luxurious
* beautiful
* sophisticated
* cinematic
* atmospheric
* calm
* organic
* extremely polished
* technically precise
* modern
* familiar enough for everyday use
* experimental when appropriate
* never cyberpunk
* never gamer-like
* never tacky
* never overloaded
* never "AI-looking"
* never generic Android template UI

## CORE DESIGN PRINCIPLE

WakeUp must feel like one coherent machine.

The launcher, themes, wallpapers, widgets, icons, typography, motion, sound, haptics, lighting, weather, time, device state, editing tools and cinematic experiences should feel like parts of the same system.

Do not create isolated features that look like they came from different apps.

Create one recognizable WakeUp design language.

The visual language must use **rounded, smooth, beautifully controlled corners** throughout the application and launcher interface.

Corners should generally feel Apple-like and premium, but do not copy Apple UI literally.

Use:

* refined corner radii
* smooth continuous-looking shapes
* excellent spacing
* subtle depth
* carefully controlled blur/transparency where appropriate
* elegant typography
* restrained shadows
* tactile surfaces
* smooth transitions
* strong hierarchy
* excellent empty space

The interface should never feel like a collection of boxes.

## DEFAULT EXPERIENCE

The default Home Screen must be familiar.

A normal user should immediately understand:

* where apps are
* how to swipe between pages
* how to open the app drawer
* how to use folders
* how to use widgets
* how to search
* how to customize the launcher

Do NOT sacrifice usability to demonstrate creativity.

However, the default experience should be exceptionally beautiful.

The system can become more expressive when the user chooses a theme that supports it.

## DESIGN INTENSITY

WakeUp needs multiple levels of visual intensity.

Everyday/default themes:

* subtle
* refined
* extremely smooth
* low visual fatigue

Special themes:

* cinematic
* immersive
* deeper motion
* richer environmental interaction
* stronger depth
* more expressive transitions

The interface must intelligently determine how much visual intensity is appropriate.

Never make every screen cinematic merely because the technology allows it.

## HOME SCREEN

The Home Screen should remain recognizable and practical.

Support:

* familiar Android-style grid behavior
* multiple pages
* folders
* dock
* widgets
* app icons
* clock/date
* weather
* optional contextual information
* wallpaper/environment
* theme-aware composition
* subtle depth
* subtle parallax
* touch response
* swipe response

The Home Screen should feel alive without feeling like a game.

Every visual element must have a reason to move.

## PAGE TRANSITIONS

Page transitions should use:

* subtle parallax
* depth
* smooth movement
* spring physics where appropriate
* environmental continuity where appropriate
* theme-specific transitions when they improve the theme

Some themes may make multiple pages feel like different parts of the same environment.

Other themes may simply use a refined page transition.

Claude has creative freedom to determine the appropriate behavior per theme.

The default behavior must remain fast and familiar.

## APP ICONS

Default behavior:

* recognizable original app icons
* excellent spacing
* excellent sizing
* consistent presentation

Allow:

* refined icon treatment
* custom icon packs
* theme-specific icon languages
* user overrides

A theme may completely transform the icon language, but recognizability and usability must remain important.

Do not create bizarre icons simply for visual novelty.

## DOCK

The dock should be adaptive.

It can intelligently react to:

* theme
* orientation
* context
* available space
* user preferences

It should remain fast and accessible.

Do not force unnecessary dock transformations.

## FOLDERS

Folders should be:

* theme-aware
* physically responsive
* beautifully animated
* fast to open
* easy to understand

Opening and closing should have excellent motion.

The folder should feel like part of the environment rather than a completely separate UI layer.

## APP DRAWER

Claude has freedom to design the best app drawer.

Requirements:

* extremely fast
* excellent search
* familiar interaction
* elegant organization
* beautiful typography
* excellent spacing
* subtle animation
* theme integration where appropriate

Do not let visual effects delay app discovery or launching.

## SEARCH

Design an excellent launcher search experience.

It should be capable of becoming a central utility for:

* apps
* launcher settings
* themes
* widgets
* device content where technically and legally appropriate
* launcher actions

It must prioritize speed and relevance.

Do not create a slow cinematic search experience.

## WIDGETS

WakeUp needs its own custom widget ecosystem.

Widgets should be:

* beautiful
* functional
* readable
* theme-aware
* adaptable
* compositional

Some widgets can visually belong to the environment.

For example, a weather widget might appear physically embedded into a rainy scene.

But widgets must remain useful.

Do not turn every widget into decorative artwork.

Widgets can communicate visually with each other while remaining independently functional.

Support:

* different sizes
* resizing
* intelligent positioning
* theme-aware styling
* expandable widgets where appropriate
* subtle animation
* dynamic data
* environmental integration

## WIDGET MOTION

Motion must be carefully controlled.

Possible states:

* idle
* touch
* swipe
* expanded
* data change
* theme transition
* weather change
* device state change

Only use states where they improve the experience.

## WALLPAPERS / LIVING WORLDS

WakeUp's wallpaper system is one of its defining features.

Wallpapers may use:

* cinematic photography
* realistic digital art
* painted environments
* layered scenes
* real-time rendering
* lightweight particles
* shaders
* pre-rendered animation
* video where appropriate
* hybrid techniques

Do not force one technology onto every theme.

Use whatever visual medium produces the best result while maintaining performance.

Scenes may contain:

* clouds
* rain
* fog
* water
* wind
* snow
* light
* shadows
* birds
* animals
* boats
* cars
* trains
* environmental movement
* silhouettes
* natural particles

Avoid uncanny human characters.

People should generally NOT be the focus of scenes.

## ENVIRONMENTAL INTERACTION

Themes can react to:

* touch
* swipe
* tilt
* device motion
* time
* weather
* charging
* battery state
* music
* headphones
* day/night
* focus state
* other legitimate device context

Interactions should feel natural.

No nausea-inducing motion.

No excessive camera movement.

No game mechanics.

## DAY/NIGHT

Themes should be able to evolve over time.

Lighting can gradually transition through:

* morning
* afternoon
* evening
* night

The change should be smooth.

Do not simply switch between four static images unless the theme calls for it.

## WEATHER

Weather should be real where technically available.

Themes may respond with:

* rain
* fog
* clouds
* sunlight
* snow
* wind
* atmospheric changes

Weather effects must remain tasteful.

## TYPOGRAPHY

Typography is a core part of Theme DNA.

Themes may have different typographic identities.

Support:

* large cinematic clocks
* minimal clocks
* editorial typography
* practical typography
* user customization
* excellent readability

Never sacrifice readability for aesthetics.

## SOUND

WakeUp may have:

* ambient soundscapes
* environmental loops
* interaction sounds
* theme-specific sounds

But:

**A locked device must never make WakeUp sounds.**

Sound controls must include:

* global volume
* per-theme volume
* interaction sound control
* ambient sound control

Users should be able to disable everything.

## HAPTICS

Haptics should be:

* subtle
* device-appropriate
* purposeful
* customizable

Never vibrate simply because a visual animation exists.

## THEME DNA

This is the central design abstraction.

A Theme DNA definition controls:

* wallpaper
* scenes
* layers
* lighting
* motion
* depth
* typography
* colors
* widgets
* icons
* sounds
* haptics
* layout
* interactions
* weather behavior
* time behavior
* device-state behavior
* animation intensity
* cinematic moments

A theme should feel like a complete world rather than a wallpaper plus a color filter.

## THEME MODES

Themes may contain modes such as:

* Pure
* Minimal
* Living
* Immersive
* Cinema
* Focus

Claude should determine the appropriate structure per theme.

## THEME BROWSER

Create a premium theme discovery experience.

Possible behavior:

* cinematic gallery
* beautiful previews
* immersive previews
* collections
* favorites
* recent themes
* saved setups
* individual assets
* complete themes

The user must be able to preview a theme before applying it.

Applying a theme should feel special but remain fast.

## THEME STUDIO

WakeUp needs a serious creative studio.

The studio should allow users to control:

* wallpaper
* layers
* depth
* parallax
* animation speed
* wind
* rain
* fog
* lighting
* particles
* typography
* widgets
* icons
* colors
* sound
* haptics
* interactions
* time
* weather
* scene behavior

The studio must have two levels:

1. Simple controls
2. Deep controls

Do not overwhelm the user immediately.

## THEME LABORATORY

Provide an advanced creative playground where users can experiment with:

* motion
* lighting
* particles
* typography
* widgets
* sound
* interactions
* environmental behavior

The laboratory can be significantly more experimental than normal launcher settings.

## THEME REMIX

Users should be able to say what they want changed.

Example:

"Make this world colder, quieter and more atmospheric, with stronger fog and less movement."

The system should create a remix while preserving the original.

Never overwrite the original automatically.

## VERSION HISTORY

Themes need:

* named snapshots
* preview
* restore
* duplicate
* duplicate-as-new-theme
* version history

## NATURAL-LANGUAGE THEME CREATION

This is a major feature.

The user should eventually be able to describe an environment such as:

"A quiet Scandinavian lake at 6:30 PM in autumn, soft rain, dark green forest, warm cabin lights, gentle fog."

WakeUp should use available/local/free generation architecture to help create the theme.

AI must remain OPTIONAL.

The core product must work without paid AI APIs.

Do not make WakeUp dependent on a paid AI provider.

## REFERENCE IMAGES

Allow a user to provide a reference image.

It may:

* inspire a new world
* become a visual foundation
* influence colors
* influence composition
* become part of an animated scene

Respect copyright and user ownership rules.

Do not imply that a user owns an image simply because they uploaded it.

## CINEMA MODE

Provide a full-screen cinematic mode.

This is where the visual system can become much more immersive.

Cinema Mode may:

* hide launcher controls
* expand the world
* intensify environmental animation
* use sound
* use interaction
* show the scene without normal Home Screen UI

It should feel like entering the world.

## FOCUS MODE

Each world can have a beautiful Focus state.

The wallpaper becomes the primary visual experience.

Information should become minimal.

## CINEMATIC MOMENTS

Rare automatic cinematic moments are allowed.

Examples:

* a bird crossing the scene
* a distant train
* sunlight breaking through clouds
* rain becoming stronger
* lights turning on
* a wave reaching shore

They must be rare.

Do not constantly trigger cinematic events.

## PERSONAL MEDIA

Users can integrate:

* photographs
* personal images
* personal media

into themes.

Do not automatically distort or aggressively process personal media.

Provide intelligent fitting/cropping.

## PALETTE

Support:

* automatic color extraction
* artistic grading
* manual override
* theme palette
* widget palette
* icon palette
* UI palette where Android permits it

Do not rely on hacks to recolor system UI.

## AUTOMATION

Support user-configurable automation based on legitimate available context:

* time
* day
* weather
* battery
* charging
* device state
* location/context only where technically and legally appropriate

Never auto-change a user's setup without permission unless the user explicitly configured that automation.

## COLLECTIONS

Support:

* favorites
* folders
* collections
* individual assets
* complete setups
* wallpapers
* widgets
* themes

## SURPRISE ME

"Surprise Me" should select from curated existing content.

Do NOT randomly generate expensive content simply because the user pressed Surprise Me.

## SETUPS

Users can save complete setups.

A setup may include:

* theme
* pages
* widgets
* icons
* wallpaper
* sounds
* haptics
* layout
* settings

Allow quick switching.

Unlimited saved setups should be supported where storage permits.

## LIVE PREVIEW

Theme preview should feel like a real device.

The preview should allow:

* swiping
* interacting with widgets
* changing pages
* previewing animations
* previewing sound
* previewing theme states
* previewing day/night
* previewing weather
* testing interactions

## EDITOR

The Home Screen editor should be extremely polished.

Support where platform permits:

* drag
* resize
* align
* position
* layers
* depth
* spacing
* grid
* typography
* widget configuration
* theme settings

Entry should feel familiar.

The editor itself can then become a sophisticated design environment.

## MOTION SYSTEM

WakeUp needs a unified motion language.

Baseline:

* smooth
* refined
* physical
* responsive

Advanced themes may use:

* stronger spring physics
* depth
* environmental motion
* expressive transitions

Motion should always be:

* purposeful
* consistent
* interruptible
* performant

Never allow animation to make the device feel slower.

## MATERIAL SYSTEM

The launcher interface may use:

* glass
* blur
* translucent surfaces
* solid luxury surfaces
* dynamic surfaces
* nearly invisible surfaces

Claude decides contextually.

Do not apply glass everywhere.

Do not create visual noise.

## ACCESSIBILITY

Accessibility must be designed into the visual system.

Support appropriate:

* text scaling
* contrast
* reduced motion
* sound controls
* haptic controls
* touch targets
* readable typography
* screen reader semantics

Reduced Motion should meaningfully reduce motion rather than simply removing one animation.

## ONBOARDING

Onboarding should be beautiful but short.

The user should understand:

* what WakeUp is
* how to make it the launcher
* how themes work
* how customization works
* how permissions work

Do not force users through a giant tutorial.

## PLATFORM DIFFERENCES

S25 Ultra:

Design specifically for a large Samsung Android flagship display.

Use the capabilities available to Android/Samsung legitimately.

Do not assume root.

Do not require hacks.

iPad:

Do NOT attempt to visually pretend that iPadOS is Android.

The iPad experience should have its own:

* compositions
* large-screen layouts
* portrait/landscape behavior
* widgets
* creative studio
* cinematic experiences
* Pencil support where appropriate
* multitasking-aware layouts
* motion interactions

Maintain one WakeUp identity across platforms while allowing each platform to be genuinely native.

## FINAL DESIGN STANDARD

Do not stop at "looks good."

The result should look like a product that could plausibly become a premium consumer technology brand.

Every screen must receive deliberate attention.

No:

* generic cards
* generic dashboards
* default component-library aesthetics
* unnecessary gradients
* cyberpunk visuals
* excessive glass
* excessive rounded rectangles
* excessive animations
* meaningless particles
* fake 3D
* AI slop
* decorative UI that harms usability

Use rounded/smooth corners as a core visual language, but vary geometry intelligently.

The interface should feel **luxurious, beautiful, alive, engineered and calm**.

Most importantly:

**Do not lose any of these requirements when implementing the design system.**

Treat this document as part of a larger three-part WakeUp specification. Engineering and security requirements will be provided separately.
