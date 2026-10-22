# LocalLink chat and room-file API

These endpoints operate on the backend host's local/LAN network; they do not
require an internet service. Every room-scoped endpoint requires
`Authorization: Bearer <sessionToken>`. The token identifies the device; sender
IDs and names are always taken from that authenticated device, never from
request fields. Room membership and expiry are checked on each request.

## Messages

`GET /api/v1/rooms/{roomId}/messages` returns at most the newest 100 messages,
newest first:

```json
[
  {
    "id": "message-uuid",
    "roomId": "room-uuid",
    "senderDeviceId": "device-uuid",
    "senderName": "Host laptop",
    "content": "Hello",
    "createdAt": "2026-10-21T15:30:00Z"
  }
]
```

`POST /api/v1/rooms/{roomId}/messages` accepts a client-generated
`clientMessageId` UUID and nonblank `content` (maximum 4,000 characters):

```json
{"clientMessageId":"request-uuid","content":"Hello"}
```

The response is the persisted message. Repeating a request with the same
client-message ID for the same room and sender returns the existing message
instead of inserting a duplicate.

## Files

`GET /api/v1/rooms/{roomId}/files` returns up to 100 newest file metadata
records per page (`?page=0&size=100`). The dashboard fetches subsequent pages
to populate the full room file list; `size` is capped at 100.
`POST /api/v1/rooms/{roomId}/files` accepts multipart form data with one `file`
field. Its response includes `id`, `roomId`, `uploaderDeviceId`, `uploaderName`,
`originalFilename`, `contentType`, `sizeBytes`, and `createdAt`.

Download a file with
`GET /api/v1/rooms/{roomId}/files/{fileId}/content`. The server streams it as
an attachment. A file ID from a different room is not downloadable.

Uploaded bytes are streamed to a temporary file and moved into the configured
local storage directory under server-generated room/file UUIDs. User-supplied
filenames are metadata only; they are sanitized and never used as filesystem
paths. Metadata is stored in the database. Expired-room file payloads and
metadata are swept daily at 03:15 server-local time. Active-room files are not
removed by the cleanup job.

## Configuration

Defaults in `application.properties`:

| Setting | Default | Purpose |
| --- | --- | --- |
| `LOCALLINK_FILE_STORAGE` | `./data/files` | File payload directory relative to the backend working directory |
| `LOCALLINK_MAX_FILE_SIZE_BYTES` | `2147483648` | Per-file application upload ceiling (2 GiB) |
| `LOCALLINK_MAX_FILE_SIZE` | `2GB` | Servlet multipart per-file ceiling |
| `LOCALLINK_MAX_REQUEST_SIZE` | `2050MB` | Servlet multipart request ceiling including multipart overhead |
| `LOCALLINK_FILE_CLEANUP_CRON` | `0 15 3 * * *` | Expired-room file cleanup schedule |
| `locallink.cors.allowed-origin-patterns` | localhost and private IPv4 HTTP origins | Static frontend origins allowed to call the API |

For a LAN deployment, set `LOCALLINK_FILE_STORAGE` to durable disk space and
add the exact static-frontend origin to
`locallink.cors.allowed-origin-patterns` (comma-separated). To raise or lower
the file-size ceiling, keep `LOCALLINK_MAX_FILE_SIZE_BYTES` and
`LOCALLINK_MAX_FILE_SIZE` aligned; the request limit must be at least as large
as the per-file limit plus multipart overhead. Ensure the backend is reachable
from other devices on port 8080 and the frontend is served from the same LAN
host (the browser derives the API host from the frontend host).

## Local verification

From `backend` run `.\mvnw.cmd test` and `.\mvnw.cmd package`. The integration
tests use an isolated three-byte file limit and `target/test-room-files` to
exercise storage, size validation, authorization, download, and cleanup.

From the project root run:

```powershell
node --test frontend\tests\room-dashboard.test.cjs
node --check frontend\src\scripts\room-dashboard.js
```

## Live room updates and connectivity behavior

The backend exposes an authenticated STOMP endpoint at `ws://<backend-host>:8080/ws`
(use `wss://` when the frontend is served over HTTPS). The browser sends its
room session token in the STOMP `CONNECT` frame's `Authorization: Bearer <token>`
native header. The server validates it on connection and revalidates the
session/device/room on every subscription and publication; it retains only a
SHA-256 token fingerprint in the in-memory WebSocket session.

Clients subscribe to:

| Destination | Payload |
| --- | --- |
| `/topic/rooms/{roomId}/messages` | Persisted message response |
| `/topic/rooms/{roomId}/members` | `{ "roomId": "...", "memberCount": 2 }` |

Clients may publish chat to `/app/rooms/{roomId}/messages` with the same
`clientMessageId` and `content` shape as the REST endpoint. The server derives
the sender from the authenticated connection, persists the message, and
broadcasts it only after the database transaction commits. The authenticated
REST history endpoint remains the recovery source after reconnects. Message
IDs are used by the dashboard to avoid rendering a REST/WebSocket duplicate.

Member counts are calculated from backend device presence, not browser guesses.
The backend checks for changed counts every five seconds; a device is considered
active while its last presence update is within 120 seconds. Counts can
therefore take up to one presence-check interval to reflect a change, and
disconnected devices may remain counted until the idle window elapses.

The browser detects its own offline/online events, WebSocket failures, and
missing server heartbeats. It retries with jittered exponential backoff capped
at 30 seconds. While the backend is unreachable, room state and chat/file
operations cannot be confirmed; the UI shows a disconnected/reconnecting state
and does not treat an unacknowledged write as successful. REST history and
authenticated file endpoints remain the fallback when the backend is reachable.
The upload UI supports progress, cancellation, and a fresh retry; interrupted
uploads do not resume and retries may create a second file if the first upload
completed but its response was lost.

WebRTC peer-to-peer file transport/signaling is not implemented yet. File
payloads currently use authenticated HTTP upload/download through the backend.
Bluetooth discovery, Wi-Fi activation, and hotspot setup are not browser
fallbacks; users must configure those through their operating system. These
pages require a LAN-served frontend, reachable backend, compatible browser
WebSocket support, and appropriate firewall/CORS configuration. HTTPS pages
must connect to a trusted HTTPS/WSS backend to avoid mixed-content blocking.

For a two-device smoke test, serve the frontend on the backend host's LAN
interface, allow the configured frontend origin through CORS, create a room
on device A, and join from device B. Verify the live connection indicator,
send a message, and observe it on the other dashboard without refreshing.
Join or leave from a device and observe the backend-derived member count
change (a stale device can take up to 120 seconds to age out). Upload a file
on either device and download it from the other. Temporarily disconnect a
device from Wi-Fi to verify the reconnecting state and automatic reconnect;
no real-device/browser matrix has been executed as part of this change.

## Host recovery and room lifecycle

Room creation now requires `hostRecoveryPin`, exactly four numeric digits.
The backend stores only a salted BCrypt hash of this value. Five incorrect
cross-device recovery attempts (including an incorrect room PIN) lock PIN
recovery for that room for 15 minutes; recovery attempts are serialized on
the room row so concurrent attempts cannot bypass the threshold.

The existing high-entropy host recovery credential and `/api/v1/rooms/recover`
endpoint remain the preferred same-device recovery path. A different device
may recover through `POST /api/v1/rooms/recover-pin` with
`{ "code": "...", "roomPin": "123456", "hostRecoveryPin": "1234" }`.
Room code and room PIN alone are insufficient. Recovery PINs and session
tokens must not be placed in URLs or logs.

`GET /api/v1/rooms/{roomId}/me` returns the authenticated device ID, name, and
role for the current dashboard. `POST /api/v1/rooms/{roomId}/close` is
restricted to the original host device. Closing changes the room state to
`CLOSED`, disconnects its devices, invalidates room access through normal
session validation, and broadcasts a closed-room event. `POST
/api/v1/rooms/{roomId}/leave` disconnects the requesting member and revokes
all of that device's session tokens; the host cannot use this endpoint to
close the room.

When creating a room, include `"hostRecoveryPin":"1234"` in the
`POST /api/v1/rooms` JSON request. The PIN is accepted only in that request
body and is never returned by the API.

## Explicit file send and safe retry

File selection only stages a local preview. The upload starts only after the
user confirms **Upload & Send**. The dashboard sends a fresh UUID in the
`X-Upload-Id` header. A retry with the same room, authenticated uploader,
upload ID, filename, and size returns the existing metadata rather than
creating a second entry. Reusing an upload ID with a different filename or
size returns HTTP 409 (`UPLOAD_ID_CONFLICT`). Omitting the optional header
preserves compatibility with existing API clients.

After the backend commits metadata, it broadcasts that metadata on
`/topic/rooms/{roomId}/files`. The sender and participants render the
confirmed file as a chat attachment and in the Files tab. Clicking the
attachment calls the existing authenticated content-download endpoint; no
direct storage URL or room-crossing file access is exposed.

Flyway migrations V4 and V5 add the host recovery PIN hash/lockout fields and
the nullable file upload idempotency key, respectively. Existing rooms created
before V4 have no recovery PIN hash; their existing same-device recovery
credential still works, but they cannot use cross-device PIN recovery.
