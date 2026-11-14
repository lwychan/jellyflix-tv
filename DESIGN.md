---
name: Notflix
description: Netflix-style dark Android TV client for a personal Jellyfin server, built for a 10-foot remote-driven screen.
colors:
  primary: "#E50914"
  primary-container: "#B20710"
  background: "#141414"
  surface: "#181818"
  surface-variant: "#2F2F2F"
  on-background: "#FFFFFF"
  on-surface: "#E5E5E5"
  on-surface-muted: "#B3B3B3"
  scrim-ink: "#141414"
  focus-fill: "#FFFFFF"
typography:
  display:
    fontFamily: "Inter"
    fontSize: "42px"
    fontWeight: 600
    lineHeight: "50px"
  headline:
    fontFamily: "Inter"
    fontSize: "26px"
    fontWeight: 600
    lineHeight: "32px"
  title:
    fontFamily: "Inter"
    fontSize: "18px"
    fontWeight: 500
    lineHeight: "24px"
  body:
    fontFamily: "Inter"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: "22px"
  label:
    fontFamily: "Inter"
    fontSize: "14px"
    fontWeight: 500
    lineHeight: "18px"
rounded:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  pill: "9999px"
spacing:
  edge: "56px"
  gap: "16px"
  row: "24px"
components:
  button-primary:
    backgroundColor: "{colors.focus-fill}"
    textColor: "{colors.scrim-ink}"
    rounded: "{rounded.pill}"
    height: "36px"
  button-secondary:
    backgroundColor: "#6D6D6E"
    textColor: "{colors.on-background}"
    rounded: "{rounded.pill}"
    height: "36px"
  profile-tile:
    rounded: "{rounded.sm}"
    size: "72px"
---

## Overview

Netflix's dark, poster-led browsing, adapted for Android TV and Fire TV remotes. The hero sits inside a rounded panel below the top rail; rows of posters and 16:9 cards follow. Text sits in the lower left of the hero, and artwork fills the rest. The look is a personal media app, not a product marketing page.

## Colors

Page background is near-black (`#141414`). The single accent is Netflix red (`#E50914`), used for the wordmark, the resume bar on partly watched titles and the watched bar on finished titles. Secondary text is `#B3B3B3`; primary text is white or `#E5E5E5`. Focus is shown by a solid white fill with black text, not by a red outline.

## Typography

Inter throughout, in one variable font file. Titles use SemiBold; labels and metadata use Medium or Regular. The hero title is artwork (a logo) when the server has one, otherwise 34sp text. Buttons use 13sp labels.

## Layout

Top rail of 96dp: avatar and search on the left, text tabs centred, settings and the C mark on the right. Content uses a 56dp horizontal edge. Hero panels are inset 56dp, with 16dp rounded corners, and sit below the rail. Rows of cards scroll horizontally with 16dp gaps.

## Elevation & Depth

Flat. No drop shadows on the page. The profile menu floats on a `#202020` panel at 92% opacity with a 1dp `#33FFFFFF` border.

## Shapes

Round pills for buttons and season chips. Posters and cards use 8dp to 10dp corners. Profile avatars use the same 10dp rounded square as the profile picker tiles. Round shapes are kept only for the settings and search icon buttons.

## Components

- **Play / Back buttons:** 200×36dp pills, 13sp label, 16dp icon. No focus scale: focus changes colour only.
- **Cards:** focused card grows, with a white 1.5dp outline. A bar along the bottom edge shows progress: red for partly watched, full red for finished.
- **Profile menu:** 230dp wide, opens from the avatar, with the avatar and name in a header. The name is centred in the header.

## Do's and Don'ts

- Do keep the hero text narrow (about 420dp) and small; the artwork is the focus.
- Do use the same shape language for avatars and picker tiles.
- Don't scale buttons on focus; use the colour change.
- Don't add a second accent colour.
- Don't put a synopsis in the home hero; the details page carries it.
