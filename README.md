# WakeUp

A premium Android launcher and device-customization ecosystem, designed first for the Samsung Galaxy S25 Ultra, with a separate, genuinely native iPadOS companion planned.

Built around **Theme DNA**: one coherent system for wallpaper, motion, typography, widgets, icons, sound, haptics and device context. The organising idea is **Held Light**: the interface owns no light of its own, everything is lit by the world behind it.

## Status

Design stage. No production code yet.

| Part | State |
|---|---|
| [01 Design brief](docs/01-design-spec.md) | verbatim, complete |
| [02 Design system: Held Light](docs/02-design-system.md) | v0.1 |
| [Theme DNA schema](theme-dna/schema.json) and [Fjordlys reference theme](theme-dna/fjordlys.dna.json) | v0.1 |
| [Interactive prototype](prototype/index.html) | v0.1, three living worlds |
| Engineering specification | to come |
| Security specification | to come |

## Try the prototype

```bash
python -m http.server 8765 --directory prototype
```

Open http://localhost:8765. Drag between pages, swipe up for the drawer and search ("fog", "cinema", "theme linen"), scrub time, change weather, switch modes, try Reduced Motion, Remix, and lock the device (ambient sound stops).

The prototype is a design instrument, not the product: placeholder app glyphs, manual weather, a canvas renderer standing in for the real Android pipeline.
