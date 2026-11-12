# Jellyflix TV: user stories (Netflix-style TV experience)

These stories describe the behaviour we want on an Android TV remote. They are written from the
viewer's point of view and say whether the current build meets each one. Netflix is the reference
for behaviour and layout only. Jellyflix keeps its own name, logo and colours.

Status key: **Done** = in the current build and checked on a Sony BRAVIA VH2. **Partial** = present
but not right yet. **To do** = not started.

## Home

1. **Start on Home with something to watch.** As a viewer, when I open the app, I see a large
   featured title at the top with Play and More Info, so I can start watching immediately. *Done.*
2. **Preview by focus.** As a viewer, when I move focus onto a title in any row, the featured area
   changes to that title (art, title, facts, synopsis). *Done.*
3. **Know what a card is.** As a viewer, each card tells me what it is: the show name for an
   episode, with season, episode number and episode title under it. *Done.*
4. **Pick up where I left off.** As a viewer, Continue watching shows landscape cards with a red
   progress bar along the bottom. *Done.*
5. **Scroll rows sideways without the card clipping.** As a viewer, rows scroll past the screen
   edge and the focused card stays readable. *Done.*
6. **Move from the nav into the page and back.** As a viewer, DPAD-down from the top nav lands on
   the page's first item, and DPAD-up from the top row returns to the nav. *Done on Home and the
   grid tabs; check each tab on the device.*
7. **Keep the focused title's text on screen.** As a viewer, when I move down into a row, its
   title and episode line stay visible above the bottom edge. *Done (150dp bottom margin).*

## Browse

8. **Browse a full catalogue.** As a viewer, the TV Shows and Movies tabs list every title in the
   library as poster rows. *Done (rows scroll sideways).*
9. **Search.** As a viewer, I type into a search box and see results as I type, without a
   keyboard popping up before I choose the field. *Partial: results appear after a short pause;
   confirm keyboard behaviour on the TV.*
10. **See what's new.** As a viewer, Home has Recently added TV shows and Recently added movies rows under Continue watching. *Done.* (My List was removed: there is no way to add or remove titles yet.)

## Title page

11. **See a title's facts.** As a viewer, a show's page shows its name, rating, year, runtime, age
    rating and synopsis. *Done.*
12. **Start the title.** As a viewer, Play is focused when the page opens, so Enter plays it.
    *Done.*
13. **Choose an episode.** As a viewer, a series page lists its seasons as chips and shows that
    season's episodes in a row at the bottom. I move to an episode and press Enter to play it.
    *Done in the current build; check on the device.*
14. **Switch seasons.** As a viewer, choosing another season chip changes the episode row.
    *Done.*

## Playback

15. **Control playback with the remote.** As a viewer, Enter pauses or plays, left and right seek
    10 seconds back or 30 seconds forward. *Done.*
16. **Controls that get out of the way.** As a viewer, the controls appear when I press a button
    and hide after 3 seconds of no input while playing. *Done.*
17. **Netflix-style layout.** As a viewer, the back arrow and title are top left, with Episodes,
    Subtitles and Audio top right. Play, replay 10 seconds, the scrub bar and the time remaining are
    along the bottom. *Built; check on the device.*
18. **Choose subtitles and audio.** As a viewer, I open a subtitle or audio picker from the top
    right and choose a track. Subtitles start off. *Done.*
19. **Resume.** As a viewer, playback resumes at my last position, and Continue Watching stays
    current. *Done.*
20. **Skip the intro.** As a viewer, a Skip Intro button appears bottom right while the intro plays,
    and one press of Enter jumps past it. The same button reads Skip Recap during a recap. The ranges
    come from the server (Intro Skipper), so shows it has not scanned get no button. *Built; check on
    the device.*
21. **Skip the credits.** As a viewer, during the credits the button reads Next Episode and starts the
    next episode. On the last episode it reads Skip Credits. When an episode ends, the next one starts
    on its own; after the last one the player closes and the title page is back. *Built; check on
    the device.*
22. **Browse episodes without leaving the video.** As a viewer, Episodes opens a row of the show's
    episodes under the picture, a season at a time, each with its picture, length and synopsis. The
    playing episode is focused. Choosing one switches to it. Back closes the row. *Built; check on
    the device.*
23. **See chapters.** As a viewer, the scrub bar has a tick at each chapter the file defines.
    *Built; check on the device.*

## Account and settings

24. **Choose a profile.** As a viewer, I pick my profile from the start screen. *Done.*
25. **Settings.** As a viewer, Settings is always reachable from the top bar. *Done (basic).*

## Not in scope yet

- Trailers and previews that play on focus.
- Downloads, offline viewing and multiple-device sync.
- Netflix's catalogue, branding and artwork. Jellyflix shows whatever is in the user's own Jellyfin
  library.
