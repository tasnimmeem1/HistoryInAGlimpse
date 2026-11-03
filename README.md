# History in a Glimpse

JavaFX desktop history explorer with a Spring Boot backend and private Supabase media storage.

## Open and run

1. Extract this ZIP into a NEW folder. Open its **HistoryInAGlimpse** folder in IntelliJ. Do not paste this whole project inside the old animation-backend folder.
2. Reload the root pom.xml. Link animation-backend/pom.xml as a Maven project if IntelliJ does not show it.
3. Use your JDK 25 (backend requires Java 21 or newer).
4. Set **SUPABASE_SECRET_KEY** in the backend run configuration to your existing secret. Credentials are not included in this download.
5. Run **AnimationBackendApplication**. Keep it running.
6. Run **Launcher** in package **org.example.historyinaglimpse** for the desktop.

The backend listens on http://127.0.0.1:8080. Change the desktop backend address with JVM option `-Dhistory.animation.backend=http://127.0.0.1:8080` only if necessary.

## Cloud scenes

Stone Age, Early Farming, Sumerian urban period, Old Kingdom, Indus Valley, Akkadian Empire, and Middle Kingdom download images through the backend. The four supplied ancient maps load through the backend too. These files are intentionally absent from the Java project. No upload-assets folder is included.

Keep the **animation-assets** bucket private. For the five new era folders, use:

| Folder | Expected files |
| --- | --- |
| sumerian-urban | background.png, actors.png, ambience.mp3 (or original ambience.wav) |
| egypt-old-kingdom | background.png, actors.png, map.png, ambience.mp3 (or original ambience.wav) |
| indus-valley | background.png, actors.png, map.png, ambience.mp3 (or original ambience.wav) |
| akkadian-empire | background.png, actors.png, map.png, ambience.mp3 (or original ambience.wav) |
| egypt-middle-kingdom | background.png, actors.png, map.png, ambience.mp3 (or original ambience.wav) |

The folders sit directly inside the bucket, beside stone-age and early-farming. Keep the exact spelling, including **middle** and **sumerian**.

Early Farming uses early-farming/farming-ambience.mp3. Stone Age keeps stone-age/stone-age-birds.mp3.

The new audio endpoint prefers ambience.mp3 for the original database ambience.wav setting. It falls back to WAV only if the MP3 is missing. Do not rename an MP3 extension to WAV. Sound loops and volume controls remain available; narration is optional through Description.

## Database setup


No private media or historical scene data is deleted from Supabase by this project cleanup. The backend uses metadata in public.animation_scenes and private object paths.

Example backend checks:
- http://localhost:8080/api/era-scenes/sumerian-urban (JSON)
- http://localhost:8080/api/era-scenes/sumerian-urban/background (PNG)
- http://localhost:8080/api/era-scenes/sumerian-urban/audio (your audio)
- http://localhost:8080/api/era-scenes/egypt-old-kingdom/map (PNG)

## What remains local

The timeline layout and history TSV files are still used locally; the separate history-database work has not replaced them. Shared illustration atlases, Phoenician animation layers, Persian/Roman maps, and sounds for other scenes are also still in use. They are kept so those parts continue working. Moving all remaining media to cloud storage would require a separate migration.

## Repair and validation

See docs/CLEANUP.md for changes and checks. See docs for artwork credits, map sources, dates, and history editing guidance.

The ZIP is a clean source export. Repository history, repository backup ZIPs, IDE caches and compiled target folders are omitted. Your original project is not changed by extracting this download; keep it until this copy works on your computer.

## Cloud content and cleanup
Historical media live in the private Supabase animation-assets bucket. Royal Society and Newton background prose is in animation_scenes.settings.history. Their timeline labels remain in the small chart resources. JavaFX classes remain required to render and animate the content.

Setup SQL is retained outside the source project with the update package. The app does not read SQL scripts at runtime. Obsolete local harbor artwork, the old harbor renderer/audio, and superseded Rome/Persia map images have been removed. Other local atlases, sounds and map geography are still used by entries not yet migrated.

## Accounts and navigation

The app opens on the splash screen, then the login screen. People can log in, create an account, or continue as a guest. After that the main menu lists every feature, and each feature is locked to a minimum role:

| Role | Who | Can use |
| --- | --- | --- |
| GUEST | not logged in | timeline |
| USER | anyone who registers | timeline, shared stories, saved history and reports |
| ADMIN | promoted in the database | everything, plus account management |

One-time database setup: run `sql/app_users.sql` in the Supabase SQL editor. To make an admin, register normally, then run `update public.app_users set role = 'ADMIN' where username = '...';`.

Backend endpoints (all JSON):
- `POST /api/auth/register` with `{"username", "displayName", "password"}`
- `POST /api/auth/login` with `{"username", "password"}`, returns `{"token", "user"}`
- `POST /api/auth/logout` and `GET /api/auth/me`, both with `Authorization: Bearer <token>`

Logins are kept in the backend's memory, so restarting the backend logs everyone out.

### Adding your screen

1. Build your screen as any JavaFX `Node`.
2. In `HistoryInAGlimpseApplication.registerFeatures()`, replace your placeholder:
   `navigator.addScreen("stories", "Shared stories", "Short description", Role.USER, StoryListView::new);`
   Your screen gets the shared toolbar with a Main menu button.
3. Call the backend with `ApiClient.get(...)` and `ApiClient.post(...)`. They add the logged-in user's token for you. Run the calls through `Session.async(...)` so the window doesn't freeze.
4. On the backend, protect an endpoint by taking the `Authorization` header and calling `authService.requireRole(header, Role.USER)`.
5. Style with the classes in `chart.css` (`form-card`, `form-title`, `primary-button`, and others). The colors are defined once at the top of that file.
