# 🌌 AstraLogicGates
**Build complex redstone systems without cables using logic blocks, memory, timers and wireless signals.**

Created and maintained by **DawcoU** 👨‍💻

AstraLogicGates is a powerful tool for technical players and map makers, allowing you to create complex Redstone mechanisms using dedicated logic blocks. Written in **Java 21** and optimized specifically for **Paper** (and its forks) to ensure maximum performance and tick-accuracy. ⚡

---

### 🚀 Key Features
* **Core Logic Gates:** Includes all basic gates like **NOT, AND, OR, XOR, NAND, NOR, XNOR**. 🧩
* **Advanced Components:** Built-in support for complex systems:
    * **Memory:** RS-Latch, T-Flip-Flop.
    * **Timing:** Enhanced repeaters with delays up to several seconds.
* **Long-Range Sensing:** Motion sensors that work at a distance. 📡
* **Modern Tech:** Optimized for the latest Minecraft versions using Java 21 features.
* **Easy Setup:** Simple command-based block placement (`/bramka <type>`) where the logic is handled under the hood.

### 🛠️ Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/bramka <type>` | Receive a specific logic gate block | `astralogicgates.bramki` |
| `/bramka SENDER <channel>` | Receive a wireless signal transmitter | `astralogicgates.bramki` |
| `/bramka RECEIVER <channel>` | Receive a wireless signal receiver | `astralogicgates.bramki` |

---

### 📖 How to use (Tutorial)

**1. Basic Logic Gates:**
* Use the command `/bramka <type>` (e.g., `/bramka AND`) to receive a logic block.
* Place the block on the ground. The output will face the **direction you were looking** when placing the block. 🧱
* **Particle Indicators:** Once placed, you will see colored particles on the sides:
    * 🔴 **Red Particles:** Input/Output is currently **OFF**.
    * 🟢 **Green Particles:** Input/Output is currently **ON**.
* **Removing:** If you break the block, the particles disappear, the output power is cut, and the logic block drops back so you can reuse it. ♻️

**2. Wireless Redstone (Bluetooth System):**
Transfer signals over long distances without miles of cables! 📡

* **SENDER (The Transmitter):**
    * Type `/bramka SENDER <channel_name>` (e.g., `/bramka SENDER test`).
    * You can use multiple channels separated by commas.
    * Place the block and connect your Redstone input to it.
* **RECEIVER (The Output):**
    * Type `/bramka RECEIVER <channel_name>` (use the same name as the sender).
    * Place the block. It doesn't need an input – it will automatically receive the signal from your Sender and output it locally! ⚡

---

### 🛠️ Other Projects
🛡️ **[AstraLogin](https://modrinth.com/plugin/astralogin)** - Check out my other plugin! It's a modern, secure, and lightweight login system featuring Bcrypt hashing and advanced player protection.

---

# Links 💾

**GitHub AstraLogicGates** https://github.com/DawcoU/AstraLogicGates 🖥️

---

### 🌐 Support & Community
If you need help, want to report a bug, or follow the development by **DawcoU**, join our Discord: [https://discord.gg/XcmcPMJZMT]
