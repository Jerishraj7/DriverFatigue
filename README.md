# Driver Fatigue Detection (Android)

Android application for **real-time driver fatigue detection** using on-device face landmark tracking.  
Developed as part of an MSc project on improving road safety with low-cost, deployable technology.

---

##  Overview

This app monitors the driver’s face using the **front camera** and detects signs of drowsiness
(eye closure, prolonged blinking, head pose). When fatigue is detected it:

- Plays an **audible alert** on the phone
- Optionally sends an **SMS alert with GPS location** to a configured emergency contact

All processing is done **locally on the device** – no cloud back-end or internet connection is required
for detection, which keeps latency low and protects privacy.

---

##  Key Features

- Real-time driver monitoring using the front camera
- On-device face landmark detection (eyes, mouth, head pose)
- Detection of prolonged eye closure / drowsiness
- Loud audio/voice alert when fatigue is detected
- Configurable **emergency contact number**
- Automatic **SMS alert with location** (when network & GPS are available)
- Works offline for detection (only SMS/location require connectivity)
- Designed to run on affordable Android devices (no extra hardware)

---

##  Tech Stack

- **Platform:** Android (native)
- **Language:** Kotlin / Java (check your code and update here)
- **Min / Target Android:** currently tested on Android 10 – Android 16
- **ML / CV:** Google face landmark pipeline (e.g. MediaPipe / ML Kit)
- **IDE:** Android Studio

---

##  Architecture (High Level)

1. **Camera Module**  
   - Uses the front camera to stream frames in real time.

2. **Face Landmark & Feature Extraction**  
   - Runs the on-device face landmark model.
   - Extracts features such as eye aspect ratio, eye closure time, and head pose.

3. **Drowsiness Detection Logic**  
   - Applies thresholds and rules over a sliding time window.
   - If eyes are closed / head position is abnormal for more than *N* frames, marks as “fatigued”.

4. **Alerting Module**  
   - Triggers an **audio alert** immediately.
   - Sends an **SMS alert** (with current GPS location) to the stored emergency contact number.

5. **Local Storage**  
   - Saves only configuration data (e.g. emergency contact).  
   - Does **not** store video frames long-term.

---

##  Getting Started

### Prerequisites

- Android Studio (latest stable version)
- Android device or emulator with:
  - Android 10 or higher
  - Front-facing camera
  - SIM + SMS capability (for testing SMS alerts)
  - GPS (for location in alerts)

### Clone the repository

```bash
git clone https://github.com/Jerishraj7/DriverFatigue.git
cd DriverFatigue
