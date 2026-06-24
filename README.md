# ivss-bridge

Thin **Dahua NetSDK ↔ SMRMPTS** sidecar. It is the foundation (Phase 0 / UC #36) of the
IVSS per-person presence epic (#36–43). It does **only** three things:

1. Hold a NetSDK login session to the IVSS (port **37777**, native SDK — *not* HTTP).
2. Expose a small REST API so the NestJS `capstone-be` can sync faces + query status.
3. Subscribe to IVSS **face-recognition** events and forward them to NestJS as JSON.

It is **stateless** (no DB). All domain logic (channel→room→meeting, user mapping,
presence/duration/timeline/report) stays in `capstone-be`.

> Derived from the proven `sep490_ams_be` project, stripped of MySQL/JPA, MQTT, the external
> Python module, and all AMS business logic. The hard part — the NetSDK call sequences and the
> native libraries — is reused as-is.

---

## 1. Assembly — copy these from `sep490_ams_be` into this project

This skeleton contains only the **new thin layer**. You must drop in the reusable assets from
the original project (paths are relative to `sep490_ams_be/`):

| Copy FROM (sep490_ams_be) | Copy INTO (ivss-bridge) | Why |
| :-- | :-- | :-- |
| `src/main/java/vn/attendance/lib/`  (whole dir, ~2009 files) | `src/main/java/vn/attendance/lib/` | NetSDK JNA bindings: `NetSDKLib`, `structure/`, `enumeration/`, `ToolKits`, `LibraryLoad`, `Utils` |
| `src/main/java/vn/attendance/util/AvataUtils.java` | `src/main/java/vn/attendance/util/AvataUtils.java` | base64 → width/height/`Memory` for enroll |
| `src/main/resources/win64/`  (or `linux64/`) | `src/main/resources/win64/` | native `.dll` / `.so` the JNA layer loads |

Do **NOT** copy: `model/`, `repository/`, `service/` (AMS), `config/mqtt/`, `controller/`,
the original `callback/` (those are AMS-coupled — this skeleton already ships trimmed
`DisconnectCallback`/`HaveReconnectCallback`).

### Native library path
`vn.attendance.lib.Utils.getLoadLibrary` resolves natives at **`./resources/win64/`** (Windows)
relative to the run directory. On Linux it expects them on the system path. So:

- **Windows (dev):** ensure a `resources/win64/` folder with the dlls sits next to where you run
  `java -jar` (e.g. copy `src/main/resources/win64` → `./resources/win64`).
- **Linux (deploy):** put the `linux64/*.so` on `LD_LIBRARY_PATH` before starting.

---

## 2. New files in this skeleton (what *we* wrote)

```
vn/attendance/bridge/
  IvssBridgeApplication.java     main (excludes DataSource autoconfig)
  sdk/ServerInstance.java        holds login + event handles
  sdk/IvssConnection.java        init → login → attach events; reconnect; lifecycle
  sdk/EventListener.java         CLIENT_RealLoadPictureEx + callback → forward
  sdk/FaceDbService.java         createGroup / enrollFace / deleteFace  (NetSDK)
  sdk/DisconnectCallback.java    trimmed (no AMS deps)
  sdk/HaveReconnectCallback.java trimmed (no AMS deps)
  forward/NestForwarder.java     POST events to NestJS webhook (best-effort)
  web/BridgeController.java      REST control API
  dto/…                          FaceEventDto, EnrollFaceRequest, CreateGroupRequest, ApiResponse
```

---

## 3. Bridge ↔ NestJS contract

**NestJS → Bridge (REST control, base `/api/ivss`):**

| Method | Path | Body / Params | Returns |
| :-- | :-- | :-- | :-- |
| GET  | `/status` | – | `{connected}` |
| POST | `/login`  | `{ip,port,username,password}` | ok/fail |
| POST | `/groups` | `{name}` | `{groupId}` |
| POST | `/faces`  | `{groupId?,personId,name,imageBase64}` | `{szUid,groupId}` |
| DELETE | `/faces` | `?groupId=&szUid=` | ok/fail |
| GET  | `/cameras` | – | *(PORT: wrap `CLIENT_MatrixGetCameras`)* |

**Bridge → NestJS (event webhook, POST `nestjs.webhook-url`, header `X-Internal-Token`):**

```json
{
  "type": "face_recognition",
  "channelId": 3,
  "personUid": "<szUID returned at enroll>",
  "name": "Tran Duc Hai",
  "similarity": 92.0,
  "eventAction": "start",
  "utc": "2026-06-22T13:55:01",
  "imageBase64": "data:image/jpeg;base64,..."
}
```

`personUid` (the device `szUID`) is the **primary identity key**: it equals the `szUid` the
bridge returned when NestJS enrolled that user, so NestJS maps it straight back to a user.

---

## 4. Build & run

```bash
# JDK 17 required (confirmed on target machine)
./mvnw clean package -DskipTests          # add a maven wrapper, or use installed mvn
# Windows: make sure ./resources/win64/*.dll is next to the run dir
set IVSS_PASSWORD=...                      # do not hardcode in application.yml
set NESTJS_BRIDGE_TOKEN=...                # shared secret with capstone-be
java -jar target/ivss-bridge-0.1.0.jar \
  --device.password=%IVSS_PASSWORD% \
  --nestjs.api-key=%NESTJS_BRIDGE_TOKEN%
```

Config lives in `src/main/resources/application.yml` (device ip/port, NestJS webhook url, group).

---

## 5. Known blockers / live-verify (cannot be proven offline)

- **IVSS needs a hard drive** for the Face (Sample) Database to function → enroll + recognition
  cannot be tested until storage is installed.
- **Network**: the run host must be on the IVSS subnet (e.g. `192.168.1.x`).
- **Face Comparison must be enabled + armed** to the Sample Database on the IVSS web UI
  (Face *Detection* alone only yields `face_detect`, no identity).
- **Identity fields** (`szUID`, candidate name/similarity, `bEventAction`) — `EventListener`
  reads channel/UTC/image (verified from the prior project) and `szUID`; the candidate
  name/similarity + enter/leave action lines are left commented as **VERIFY** against
  `DEV_EVENT_FACERECOGNITION_INFO(_V1)` + candidate structs on the live device.
- **`deleteFace` / `GET /cameras`** are marked **PORT**: lift the exact bodies from
  `FaceRecognitionServiceImpl` / `DeviceServiceImpl` once you confirm fields on-device.

### One-time live smoke test (when HDD + network ready)
1. Start bridge → `GET /api/ivss/status` → `{connected:true}`.
2. `POST /api/ivss/groups {name:"SMRMPTS"}` → note `groupId`.
3. `POST /api/ivss/faces` with your portrait → note `szUid`.
4. Enable Face Comparison on the channel, armed to that group.
5. Stand in front of the camera → confirm NestJS webhook receives a `face_recognition`
   event whose `personUid == szUid`.
