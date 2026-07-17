# Echo Hunter 🚀

An ultra-lightweight, zero-dependency Android tactical stealth game built entirely with custom Canvas rendering. No frameworks, pure Activity-based architecture, and heavily optimized for an impossibly tiny APK size (~150KB).

> **Note on Versioning:** Initial closed beta releases were mistakenly labeled as v1.0.0 and v2.0.0. To properly follow Semantic Versioning (SemVer) during the active development and beta testing phase, the current version sequence has been reset to **v0.3.0**.

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

### ♾️ The Sandbox Mode: The Slow-Burn Awakening
As you play the survival mode, the syndicate slowly realizes what is happening:
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

Navigate high-speed server corridors, hunt down **HVTs (High Value Targets)**, and maintain your uplink.
* **Procedural Objectives:** Beyond just surviving, execute specific missions:
  * **Elimination:** Locate and neutralize heavily guarded HVTs marked with red targeting reticles.
  * **Clean Sweep:** Destroy "Circuit Chip" spawner nodes to trigger cascading system failures.
  * **Core Defense:** Protect stationary uplinks from kamikaze hunter swarms.
  * **Gate Hijack:** Breach the exit portal before the Admin purge protocol closes it.
* **Procedural Servers (Mazes):** Explore tactical layouts including open Arenas, tight Quarantine cores, Server Farms, and complex Labyrinths.
* **Nano-OS Dashboard:** A simulated 3-port operating system where you can manage your Arsenal, access past Sandbox nodes via Archives, or use the Decompiler for permanent firmware upgrades.
* **Tactical Arsenal (Weapons & Traps):** * **Pulse/Sonar:** Scan the area to reveal hidden threats and map boundaries.
  * **Weapons:** Choose between the standard Blaster, Shotgun (Spread), or Sniper (Pierce).
  * **Traps:** Deploy tactical modules like Camouflage (Invisibility), Decoy Holograms, or EMP Mines.
  * **Overclock (OVR):** Fill your meter to 100% to manually unleash Root Override. Move at blinding speeds and physically RAM enemies to delete them.
* **Smart Stealth AI:** Avoid enemy "Line of Sight" (Vision Cones).
  * **Hunters:** Standard viruses that track and dash toward your position.
  * **Guards (Heavy Units):** Cyan-colored elite units that orbit HVTs. They feature high-density code, making them immune to shield-kills—instead, they trigger a kinetic bounce-back.
  * **Pathfinding:** All enemies use advanced A* and Flow-Field algorithms to navigate server mazes.
* **Autopilot Mode:** Engage the self-playing AI. The drone will autonomously navigate mazes, hunt enemies, and locate the core using flow-field navigation.

## 🛠️ Under The Hood (Tech Stack)
This project is an ongoing experiment in extreme Android optimization and zero-allocation game loops:
* **No Game Engines:** Built entirely using native Android Kotlin APIs.
* **Dynamic Canvas Rendering:** Crisp, 60fps+ drawing directly onto a `Canvas` that calculates coordinates on the fly to seamlessly support both Portrait and Landscape orientations.
* **Zero External Assets:** No PNGs, JPEGs, or MP3/WAV files. UI is generated via code, and audio is handled natively via Android's `ToneGenerator`.
* **Zero-Allocation Game Loop:** Uses pre-allocated primitive arrays (`FloatArray`, `IntArray`) for object pooling to completely eliminate Garbage Collection (GC) stutters.
* **Advanced AI Navigation:**
  * Implements **BFS Heatmaps (Flow Field)** for multi-entity maze navigation.
  * Bosses utilize **A* Pathfinding** logic to actively hunt the player.
  * **Line-of-Sight (LoS) Raycasting** ensures enemies only detect you if their path is unblocked by server walls.

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