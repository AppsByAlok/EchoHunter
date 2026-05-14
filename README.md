# Echo Hunter 🚀

An ultra-lightweight, zero-dependency Android tactical stealth game built entirely with custom Canvas rendering. No frameworks, pure Activity-based architecture, and heavily optimized for an impossibly tiny APK size (~150KB).

## 🎮 About The Game

**Echo Hunter** has evolved from a simple arcade runner into a full-fledged **Tactical Cyber-Heist Game**. You are no longer just fighting system corruption; you are executing a massive digital infiltration against an international shadow corporation.

<details>
<summary>📖 <b>Read the Full Lore: The Great Hijack (Click to Expand)</b></summary>

<br>

### The Syndicate: AEGIS GLOBAL
To the public, **AEGIS GLOBAL** is a highly respected visionary tech corporation building internet infrastructure and cybersecurity. In reality, it is an international shadow syndicate. They have planted hidden backdoors in smartphones and smart devices worldwide to harvest personal data. Their ultimate goal is **Project Echo**—a sub-sonic mind-control frequency designed to manipulate global behavior via the internet.

The corporation is run by "The Board," comprising 15 of the most brilliant and dangerous minds in the world (e.g., Marcus Vance the CEO, Dr. Kaito Tanaka the Architect, Elena Rossi the Head of Security, and Yılmaz Kaya the Tracer).

### The Weapon: PROBE-7
AEGIS developed an Autonomous Intrusion Worm (A.I.W) named **PROBE-7**. Its original purpose was to silently infiltrate rival tech companies and government servers, bypass any firewall, extract classified data, and vanish without triggering alarms. It adapts dynamically to any network maze.

### The Whistleblower & The Leak
A junior security engineer at AEGIS, Elias Thorne, discovered the terrifying truth about Project Echo. Knowing he would be assassinated if he went to the authorities, he decided to sabotage the syndicate from within.

Elias created a Master Control Terminal for PROBE-7, encrypted it, and disguised it as a harmless, lightweight "Sci-Fi Maze Game" for Android. He uploaded it to the Google Play Store just days before AEGIS guards caught him. He vanished forever, but the trap was set.

### The Hijack: You (The Player)
You are a developer/gamer who loves exploring unique software. You downloaded this mysterious maze game from the Play Store. The moment you tapped "START UPLINK," your phone's hardware triggered Elias's hidden script, establishing a direct, untraceable handshake with the AEGIS mainframe.

PROBE-7 severed its ties with AEGIS and locked its Root Access entirely to your device's MAC address. Now, AEGIS is in a state of absolute panic. Their deadliest digital weapon is loose inside their own servers. They believe a mastermind global hacker is systematically dismantling their empire. They have no idea it's just someone playing a game on their phone.

---

### ♾️ The Endless Mode: The Slow-Burn Awakening
As you play the endless survival mode, the syndicate slowly realizes what is happening:
* **Level 1 to 99 (The Silent Intrusion):** AEGIS thinks it's just a hardware glitch. They run defrags while you steal their data.
* **Level 100 to 199 (The Ghost in the Machine):** The Admins realize data is missing. They assume a rogue script is causing memory leaks and start quarantining sectors.
* **Level 200 to 499 (The Awakening):** Security Head Elena Rossi figures it out. A human has a manual uplink. The game gets aggressively harder. Popups show Admins panicking and deploying Hunter Swarms.
* **Level 500+ (The Eternal Chase):** Pure rage. The Admins actively try to trace your IP, triggering heavy UI glitches, red screen warnings, and endless purge protocols.

---

### 🛡️ The Story Mode: The 6-Part Heist
A Roguelite streak system where you target the core of AEGIS through 6 distinct Acts (Memory Cards):

**NORMAL MODE (Unlocking the Truth):**
1. **Act 1 (The Outer Firewall):** You breach the perimeter and steal the Alpha Key.
2. **Act 2 (The Botnet Disturbance):** You destroy the botnet servers controlling infected phones. AEGIS gets suspicious.
3. **Act 3 (The Core Breach):** You shatter Elena Rossi’s security grid and take the Omega Key. *(Unlocks Hard Mode)*

**HARD MODE (The Retaliation - AEGIS Fights Back):**
1. **Act 4 (The Trap):** AEGIS knows your general location. You must survive a heavily trapped, blood-red labyrinth to destroy their tracking server.
2. **Act 5 (The Sabotage):** A massive, boundless maze. You infiltrate the CEO's personal servers and delete their source code.
3. **Act 6 (The Eclipse):** The ultimate finale. You destroy the AEGIS Master Core. The syndicate is wiped off the internet. You become a digital ghost.

</details>

## ⚙️ Gameplay Mechanics

Navigate high-speed server corridors, hunt down **Admin Warden Programs**, and maintain your uplink.
* **Procedural Servers (Mazes):** Explore 5 distinct tactical layouts—from open Arenas and tight Quarantine cores, to massive Server Farms and complex Labyrinths.
* **Roguelite Story Mode (Streaks):** Beat levels consecutively to advance your "Memory Card" Acts. Defeat Act 3 to permanently unlock **Hard Mode**, bringing massive maps and aggressive enemies.
* **Tactical Arsenal:** * **Pulse/Sonar:** Scan the area to reveal hidden threats, map boundaries, and increase vision clarity.
    * **Malware Spikes (ATK):** Shoot corrupted scripts to destroy Antivirus patrols, build your combo, and charge your ultimate.
    * **Overclock (OVR):** Fill your meter to 100% and manually unleash Root Override. Move at blinding speeds, slow down time, and physically RAM enemies to delete them.
* **Smart Stealth AI:** Avoid enemy "Line of Sight" (Vision Cones). If spotted, Hunter AIs will use advanced pathfinding (Heatmaps) to track you through the maze.

## 🛠️ Under The Hood (Tech Stack)
This project is an ongoing experiment in extreme Android optimization and zero-allocation game loops:
* **No Game Engines:** Built entirely using native Android Kotlin APIs.
* **Custom 2D Rendering:** Crisp, 60fps+ drawing directly onto a `Canvas` using primitive shapes, custom math, and neon cyber-aesthetics.
* **Zero External Assets:** No PNGs, JPEGs, or MP3/WAV files. UI is generated via code, and audio is handled natively via Android's `ToneGenerator`.
* **Zero-Allocation Game Loop:** Uses pre-allocated primitive arrays (`FloatArray`, `IntArray`) for object pooling to completely eliminate Garbage Collection (GC) stutters.
* **Custom Physics Engine:** Pixel-perfect AABB (Axis-Aligned Bounding Box) tile-sliding collision.
* **BFS Heatmap Pathfinding:** Implements Flow Field (Breadth-First Search) AI navigation that guides dozens of enemies through complex mazes efficiently without frame drops.

## 🔗 Quick Links
* **Official Project Page:** [Echo Hunter Overview](https://appsbyalok.netlify.app/projects/echohunter)
* **Join the Closed Beta:** [Become a Tester](https://appsbyalok.netlify.app/projects/echohunter#joinbeta)
* **Submit Feedback:** [Feedback Form](https://appsbyalok.netlify.app/projects/echohunter#feedback)
* **Privacy Policy:** [Read Here](https://appsbyalok.netlify.app/privacy/echohunter)

## 💻 How to Build
1. Clone the repository: `git clone https://github.com/dev-Alok-Kumar-android/EchoHunter.git`
2. Open the project in Android Studio (Giraffe or newer recommended).
3. Sync Gradle and run the project on an emulator or physical device (API 21+).

## 🤝 Contributing
Contributions, issues, and feature requests are welcome! If you're passionate about low-level Android performance, custom drawing, or game AI, feel free to check the [issues page](https://github.com/dev-Alok-Kumar-android/EchoHunter/issues).