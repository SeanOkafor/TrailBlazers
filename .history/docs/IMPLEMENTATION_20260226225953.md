# Implementation Documentation

## Project: Trail Blazers
**Repository:** https://github.com/SeanOkafor/GameDevAssignment1.git

---

## Running the Application

### Prerequisites
- Java JDK 21 (or compatible version)
- Git (for version control)

### Compilation
From the project root directory:

```powershell
cd src
javac util/*.java
javac *.java
cd ..
```

### Running the Game
From the project root directory:

```powershell
java -classpath src MainWindow
```

---

## Project Structure
```
BasicGameTemplate/
├── src/
│   ├── MainWindow.java    - Main entry point, screen navigation, and game loop
│   ├── Level1Panel.java   - Level 1 parallax scrolling background (mountain theme)
│   ├── Level2Panel.java   - Level 2 parallax scrolling background (industrial theme)
│   ├── Controller.java    - Handles keyboard input (singleton pattern)
│   ├── Model.java         - Game logic and state
│   ├── Viewer.java        - Rendering and display
│   └── util/
│       ├── GameObject.java
│       ├── Point3f.java
│       ├── Vector3f.java
│       └── UnitTests.java
├── res/
│   └── New Graphics/
│       ├── Main Screen.png          - Main menu screen (1152x928)
│       ├── ControlsScreen.png       - Controls screen (1152x928)
│       ├── Level Selection.png      - Level selection screen (1344x800)
│       ├── parallax_mountain_pack/  - Level 1 background layers (5 layers, 272x160 / 544x160)
│       └── parallax-industrial-pack/ - Level 2 background layers (4 layers, 213-272 x 104-160)
├── docs/
│   ├── IMPLEMENTATION.md  - This file
│   └── REQUIREMENTS.md    - Game design & requirements document
└── bin/                   - Compiled classes (gitignored)
```

---

## Game Overview
**Trail Blazers** is a horizontal side-scrolling game inspired by *Jetpack Joyride* and *Geometry Dash*. The game features a multi-screen navigation system with pre-designed screen images and transparent button overlays, single player and multiplayer modes, and level-based progression.

---

## Implementation Details

### Screen Navigation System

The game uses a screen-based navigation system where each screen is a pre-designed PNG image displayed as a `JLabel` with `ImageIcon`, scaled to fill the 1000x1000 game window. Transparent `JButton` overlays are positioned on top of the images at coordinates matching the button artwork in each image.

#### Image Scaling
Each screen image has its own original resolution and is scaled to 1000x1000 for display. Button coordinates from the original image are mathematically scaled to match:

| Screen | Original Size | X Scale (1000/width) | Y Scale (1000/height) |
|--------|--------------|----------------------|----------------------|
| Main Screen | 1152x928 | 0.868 | 1.078 |
| Controls Screen | 1152x928 | 0.868 | 1.078 |
| Level Selection | 1344x800 | 0.744 | 1.250 |

#### Transparent Button Pattern
All buttons use the same styling to appear invisible over the screen artwork:
```java
setOpaque(false);
setContentAreaFilled(false);
setBorderPainted(false);
setFocusPainted(false);
setCursor(HAND_CURSOR);
```

#### Navigation Flow
```
Main Screen
├── [Single Player Button] → Level Selection Screen (multiplayerEnabled = false)
├── [Multiplayer Button]   → Level Selection Screen (multiplayerEnabled = true)
└── [Controls Button]      → Controls Screen

Controls Screen
└── [Back Button]          → Main Screen

Level Selection Screen
├── [Level 1 Button]       → Level 1 (mountain parallax background)
├── [Level 2 Button]       → Level 2 (industrial parallax background)
└── [Back Button]          → Main Screen
```

#### Button Coordinates

| Button | Screen | Original Coords | Image Size | Scaled Bounds (x, y, w, h) |
|--------|--------|-----------------|------------|---------------------------|
| Single Player | Main | (263,833)→(560,900) | 1152x928 | (228, 897, 258, 72) |
| Multiplayer | Main | (587,833)→(852,900) | 1152x928 | (509, 897, 230, 72) |
| Controls | Main | (912,832)→(1124,897) | 1152x928 | (792, 897, 184, 70) |
| Back | Controls | (529,770)→(622,798) | 1152x928 | (459, 830, 81, 30) |
| Level 1 | Level Selection | (63,232)→(653,587) | 1344x800 | (47, 290, 439, 444) |
| Level 2 | Level Selection | (690,233)→(1282,587) | 1344x800 | (513, 291, 441, 443) |
| Back | Level Selection | (626,730)→(718,759) | 1344x800 | (466, 913, 68, 36) |

### Multiplayer Toggle
- `private static boolean multiplayerEnabled` — set to `false` by Single Player button, `true` by Multiplayer button
- `public static boolean isMultiplayerEnabled()` — getter for use by other classes
- Both modes navigate to the same Level Selection screen

### Key Methods in MainWindow.java
| Method | Purpose |
|--------|---------|
| `showMainScreen()` | Shows main screen and its 3 buttons, hides all other screens |
| `showControlsScreen()` | Shows controls screen and back button, hides all others |
| `showLevelSelectionScreen()` | Shows level selection with Level 1, Level 2 and back buttons |
| `showLevel1()` | Shows Level 1 parallax mountain background |
| `showLevel2()` | Shows Level 2 parallax industrial background |
| `isMultiplayerEnabled()` | Static getter for multiplayer mode state |
| `gameloop()` | Main game loop — updates model, redraws view, updates score |

### Parallax Scrolling System

Both levels use a parallax scrolling technique implemented in `Level1Panel.java` and `Level2Panel.java`. The animation code is heavily commented — see those files for a detailed walkthrough.

**How it works:**
- Each level has multiple PNG layers (small images, e.g. 272x160)
- Each layer has a scroll offset and a speed (pixels per frame)
- Back layers scroll slowly (e.g. 0.2 px/frame for sky), front layers scroll faster (e.g. 2.5 px/frame for foreground)
- Every frame, `updateParallax()` increments each offset by its speed
- `paintComponent()` scales each layer to fill the 1000px panel height, tiles it horizontally, and shifts by the offset
- Modulo wrapping ensures infinite seamless looping

**Level 1 — Mountain Theme** (`Level1Panel.java`):
- 5 layers: sky bg → far mountains → closer mountains → trees → foreground trees
- Source: `parallax_mountain_pack` (272x160 and 544x160 images)

**Level 2 — Industrial Theme** (`Level2Panel.java`):
- 4 layers: sky bg → far buildings → closer buildings → foreground structures
- Source: `parallax-industrial-pack` (213-272 x 104-160 images)

### Game Controls
| Action | Player 1 | Player 2 |
|--------|----------|----------|
| Move Up | W | Up Arrow |
| Move Down | S | Down Arrow |
| Shoot | G | L |

### MVC Architecture
- **Model** (`Model.java`) — Manages game state, player, enemies, bullets, collision detection, score
- **Viewer** (`Viewer.java`) — Renders game objects using sprite images with `Graphics2D`
- **Controller** (`Controller.java`) — Singleton handling keyboard input

---

## Change Log

### February 19, 2026 — Initial Setup
- Initialized Git repository
- Created .gitignore for Java projects
- Connected to GitHub repository
- Initial commit with basic MVC game template

### February 26, 2026 — Screen Navigation System
- Changed window title to "Trail Blazers"
- Removed old "Start Game" button
- Added Main Screen with `Main Screen.png`
- Added Controls Screen with `ControlsScreen.png` and Back button
- Added Level Selection Screen with `Level Selection.png` and Back button
- Added Single Player button (sets multiplayer = false, navigates to Level Selection)
- Added Multiplayer button (sets multiplayer = true, navigates to Level Selection)
- All buttons are transparent overlays positioned to match artwork in screen images
- Created REQUIREMENTS.md game design document

### February 26, 2026 — Level 1 & Level 2 Parallax Backgrounds
- Created `Level1Panel.java` — parallax scrolling mountain background (5 layers)
- Created `Level2Panel.java` — parallax scrolling industrial background (4 layers)
- Added Level 1 button on Level Selection screen → shows mountain parallax
- Added Level 2 button on Level Selection screen → shows industrial parallax
- Both panels animate at ~100 FPS via the main game loop
- Added detailed code comments explaining how the parallax animation system works
- Updated all show/hide methods to handle Level 1 and Level 2 panel visibility

### February 26, 2026 — Menu Music
- Added looping background music (`res/Music/awesomeness.wav`) on menu screens
- Music plays on main screen, controls screen, and level selection screen
- Music stops when entering Level 1 or Level 2 (levels will have their own music)
- Music resumes when returning to main screen from a level

---

## References

All assets listed below are by **ansimuz** on [OpenGameArt.org](https://opengameart.org), licensed under their respective open licenses.

| Asset | Source | Usage |
|-------|--------|-------|
| Menu Music | [Menu Music](https://opengameart.org/content/menu-music) | Background music looping on menu screens (`res/Music/awesomeness.wav`) |
| Level 1 Background | [Mountain at Dusk Background](https://opengameart.org/content/mountain-at-dusk-background) | 5-layer parallax scrolling background for Level 1 (`parallax_mountain_pack`) |
| Level 2 Background | [Industrial Parallax Background](https://opengameart.org/content/industrial-parallax-background) | 4-layer parallax scrolling background for Level 2 (`parallax-industrial-pack`) |
| Main Menu Inspiration | [Warped Character Pro](https://opengameart.org/content/warped-character-pro) | Visual inspiration for the main menu screen design (`Main Screen.png`) |