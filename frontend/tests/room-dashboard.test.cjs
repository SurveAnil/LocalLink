const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const root = path.resolve(__dirname, "..");
const read = (relativePath) =>
    fs.readFileSync(path.join(root, relativePath), "utf8");

test("dashboard has no demo room, demo PIN, fake sender, or seeded message", () => {
    const dashboard = read("src/scripts/room-dashboard.js");
    const sender = read("src/pages/Sender dashboard/sender.html");
    const receiver = read("src/pages/Receiver dashboard/receiver_dashboard.html");

    for (const source of [dashboard, sender, receiver]) {
        assert.doesNotMatch(source, /Project Meeting|MEETING-4829|735921|seededOwn/);
    }
    assert.match(dashboard, /let messages = \[\]/);
    assert.match(dashboard, /messageList\.replaceChildren\(\)/);
});

test("message identity uses the authenticated device and actual sender name", () => {
    const dashboard = read("src/scripts/room-dashboard.js");

    assert.match(
        dashboard,
        /message\.senderDeviceId === deviceId/
    );
    assert.match(dashboard, /const senderName = kind === "file"/);
    assert.match(dashboard, /isOwn\s*\?\s*"You"\s*:/);
    assert.match(dashboard, /senderName\.trim\(\)/);
    assert.match(dashboard, /message\.senderDeviceId === deviceId/);
});

test("dashboard uses authenticated backend APIs for persisted chat and files", () => {
    const dashboard = read("src/scripts/room-dashboard.js");

    assert.match(dashboard, /\/messages/);
    assert.match(dashboard, /clientMessageId,\s*content/);
    assert.match(dashboard, /\/files/);
    assert.match(dashboard, /formData\.append\("file", file, file\.name\)/);
    assert.match(dashboard, /options\.body instanceof FormData/);
    assert.match(dashboard, /\/content/);
    assert.match(dashboard, /response\.blob\(\)/);
    assert.match(dashboard, /setInterval\(refreshRoomState, 15_000\)/);
    assert.match(dashboard, /new window\.LocalLinkConnection\.RoomConnectionManager/);
    assert.match(dashboard, /receiveRealtimeMessage/);
    assert.match(dashboard, /locallink-pending-message-\$\{roomId\}/);
    assert.match(dashboard, /pending\?\.clientMessageId \|\| generateUuid\(\)/);
    assert.match(dashboard, /Retry or clear the pending message before sending another/);
    assert.doesNotMatch(dashboard, /localStorage\.setItem\(storageKey/);
});

test("join banner is set only after a successful join and consumed once", () => {
    const join = read("src/scripts/pages/Join Room/join_room.js");
    const dashboard = read("src/scripts/room-dashboard.js");

    assert.ok(join.indexOf("if (!response.ok)") < join.indexOf('"locallink-join-success"'));
    assert.match(join, /sessionStorage\.setItem\(\s*"locallink-join-success"/);
    assert.match(dashboard, /joinedRoomId === roomId/);
    assert.match(dashboard, /sessionStorage\.removeItem\("locallink-join-success"\)/);
    assert.match(
        read("src/pages/Receiver dashboard/receiver_dashboard.html"),
        /class="connected-banner"[^>]*hidden/
    );
});

test("room-created success banner is one-time after successful room creation", () => {
    const create = read("src/scripts/pages/Create Room/create_room.js");
    const roomCreated = read("src/scripts/pages/Room Created/room_created.js");
    const page = read("src/pages/Room Created/room_created.html");

    assert.ok(create.indexOf("if (!response.ok)") < create.indexOf('"locallink-room-created-success"'));
    assert.match(create, /sessionStorage\.setItem\(\s*"locallink-room-created-success"/);
    assert.match(page, /data-room-created-banner hidden/);
    assert.match(roomCreated, /successRoomId !== query\.get\("roomId"\)/);
    assert.match(roomCreated, /sessionStorage\.removeItem\(\s*"locallink-room-created-success"\)/);
});

test("host dashboard navigation includes room identity and title omits back button", () => {
    const roomCreated = read("src/scripts/pages/Room Created/room_created.js");
    const sender = read("src/pages/Sender dashboard/sender.html");

    assert.match(roomCreated, /url\.searchParams\.set\("roomId", query\.get\("roomId"\)\)/);
    assert.match(roomCreated, /url\.searchParams\.set\("deviceId", query\.get\("deviceId"\)\)/);
    assert.match(roomCreated, /url\.searchParams\.set\("role", query\.get\("role"\) \|\| "HOST"\)/);
    assert.match(sender, /<h1 id="dashboard-title" data-dashboard-title>Host Dashboard<\/h1>/);
    assert.doesNotMatch(sender, /class="back-button"/);
});

test("host and member dashboards share one surface with role-specific actions", () => {
    const host = read("src/pages/Sender dashboard/sender.html");
    const member = read("src/pages/Receiver dashboard/receiver_dashboard.html");
    const css = read("src/styles/room-dashboard.css");

    for (const page of [host, member]) {
        const main = page.slice(page.indexOf('<main class="dashboard"'), page.indexOf("</main>"));
        for (const selector of [
            'data-dashboard-title',
            'class="room-summary"',
            'data-connection-status',
            'class="dashboard-actions"',
            'class="dashboard-tabs"',
            'chat-panel"',
            'message-list"',
            'class="composer"'
        ]) {
            assert.ok(main.includes(selector), `${selector} must stay in the dashboard surface`);
        }
    }
    assert.match(host, /<span>Invite<\/span>/);
    assert.match(host, /<span>Share QR<\/span>/);
    assert.match(host, /<span>Share Link<\/span>/);
    assert.match(host, /data-close-room/);
    assert.doesNotMatch(host, /data-leave-room/);
    assert.match(member, /data-leave-room[\s\S]*?Leave Room/);
    assert.doesNotMatch(member, /data-close-room|data-action="invite"|data-action="qr"|data-action="share"/);
    assert.match(css, /width:\s*min\(calc\(100% - 48px\),\s*1120px\)/);
    assert.match(css, /background:\s*#ffffff/);
    assert.match(css, /\.message-list\s*\{[^}]*overflow-y:\s*auto/s);
    assert.match(css, /\.composer\s*\{[^}]*margin-top:\s*auto/s);
    assert.match(css, /\.file-details strong,[\s\S]*?overflow-wrap:\s*anywhere/);
    assert.match(css, /\.leave-room-action\s*\{[^}]*background:\s*#f8faff/s);
    assert.doesNotMatch(css, /\.dashboard-action:last-child,\s*\.dashboard-action:nth-last-child\(2\)/);
});

test("join room uses dashboard-width layout", () => {
    const styles = read("src/styles/pages/Join Room/style.css");

    assert.match(styles, /max-width:\s*720px/);
    assert.match(styles, /background:\s*var\(--color-surface\)/);
    assert.match(styles, /@media \(max-width:\s*480px\)/);
    assert.match(styles, /\.join-room-container #join-room-form,[\s\S]*?\.join-room-container \.Scan-QR-section\s*\{\s*width:\s*100%/);
});

test("Edit Room Settings uses the shared surface and accessible control sizing", () => {
    const styles = read("src/styles/pages/Edit Room setting/style.css");

    assert.match(styles, /width:\s*min\(100% - 32px,\s*520px\)/);
    assert.match(styles, /background:\s*var\(--color-surface\)/);
    assert.match(styles, /min-height:\s*44px/);
    assert.match(styles, /@media \(max-width:\s*360px\)/);
});

test("landing actions are semantic links and settings do not seed a demo profile", () => {
    const landing = read("src/pages/Landing page/landing_page.html");
    const landingStyles = read("src/styles/pages/Landing page/style.css");
    const settings = read("src/pages/Setting/setting.html");
    const helpStyles = read("src/styles/pages/Help page/style.css");
    const help = read("src/pages/Help page/help.html");

    assert.match(landing, /class="landing-action landing-action-primary"[\s\S]*?Create a Room<\/a>/);
    assert.match(landing, /class="landing-action landing-action-secondary"[\s\S]*?Join a Room<\/a>/);
    assert.match(landing, /Room PIN-protected<br>access/);
    assert.match(landing, /Works in modern<br>browsers/);
    assert.doesNotMatch(landing, /<a[^>]*>\s*<button/);
    assert.match(landingStyles, /\.landing-action:focus-visible/);
    assert.doesNotMatch(settings, /value="Alice"/);
    assert.match(settings, /id="char-count">0\/32/);
    assert.match(helpStyles, /\.topic-title\s*\{[^}]*font-size:\s*14px/s);
    assert.match(helpStyles, /\.topic-description\s*\{[^}]*font-size:\s*12px/s);
    assert.match(help, /uploaded to the room's LocalLink\s+backend/);
    assert.match(help, /this version does not provide end-to-end\s+encryption/);
    assert.doesNotMatch(help, /AES-GCM|direct transfers over a local/);
});

test("room entry forms do not prefill a fake member name", () => {
    const create = read("src/pages/Create Room/create_room.html");
    const join = read("src/pages/Join Room/join_room.html");

    assert.doesNotMatch(create, /id="your-name"[^>]*value=/);
    assert.doesNotMatch(join, /id="your-name"[^>]*value=/);
});

test("host can recover from join page using locally stored credential and server validation", () => {
    const joinPage = read("src/pages/Join Room/join_room.html");
    const joinScript = read("src/scripts/pages/Join Room/join_room.js");
    const createScript = read("src/scripts/pages/Create Room/create_room.js");

    assert.match(joinPage, /id="recover-host"/);
    assert.match(createScript, /locallink-host-recovery-by-code-\$\{data\.code\.toUpperCase\(\)\}/);
    assert.match(joinScript, /locallink-host-recovery-by-code-\$\{code\}/);
    assert.match(joinScript, /fetch\(`\$\{API_BASE_URL\}\/recover`/);
    assert.match(joinScript, /recoveryCredential:\s*recovery\.recoveryCredential/);
    assert.match(joinScript, /data\.role !== "HOST"/);
    assert.match(joinScript, /locallink-session-\$\{data\.roomId\}/);
    assert.match(joinScript, /room code and room PIN alone are not enough/);
    assert.doesNotMatch(joinScript, /pin:\s*pinInput\.value.*recover/s);
});

test("expired-room response displays expiry and disables room controls", () => {
    const dashboard = read("src/scripts/room-dashboard.js");
    const pages = [
        read("src/pages/Sender dashboard/sender.html"),
        read("src/pages/Receiver dashboard/receiver_dashboard.html")
    ];

    assert.match(dashboard, /error\.status === 410 && error\.code === "ROOM_EXPIRED"/);
    assert.match(dashboard, /markRoomExpired\(error\.expiresAt\)/);
    assert.match(dashboard, /element\.disabled = true/);
    assert.match(dashboard, /message\.textContent = `This room expired at/);
    for (const page of pages) {
        assert.match(page, /data-room-expired role="alert" hidden/);
        assert.match(page, /data-room-expiry/);
    }
});

test("member count and room expiry are sourced from backend room details", () => {
    const dashboard = read("src/scripts/room-dashboard.js");

    assert.match(dashboard, /room\.memberCount/);
    assert.match(dashboard, /room\.expiresAt/);
    assert.match(dashboard, /\/presence/);
    assert.match(dashboard, /\/leave/);
});

test("file selection previews first and only uploads after explicit confirmation", () => {
    const dashboard = read("src/scripts/room-dashboard.js");
    const receiver = read("src/pages/Receiver dashboard/receiver_dashboard.html");
    const sender = read("src/pages/Sender dashboard/sender.html");
    const selectionStart = dashboard.indexOf('fileInput.addEventListener("change"');
    const selectionEnd = dashboard.indexOf(
        'document.querySelector("[data-upload-send]")',
        selectionStart
    );
    const selectionHandler = dashboard.slice(selectionStart, selectionEnd);

    assert.doesNotMatch(selectionHandler, /uploadFile\(/);
    assert.match(selectionHandler, /pendingUploadFile = selectedFile/);
    assert.match(sender, /data-attachment-preview/);
    assert.match(receiver, /data-attachment-preview/);
    for (const page of [sender, receiver]) {
        assert.match(page, /data-attachment-name/);
        assert.match(page, /data-attachment-size/);
        assert.match(page, /Upload &amp; Send/);
        assert.match(page, /data-remove-attachment/);
        assert.doesNotMatch(page, /id="file-input"[^>]*multiple/);
    }
    assert.match(dashboard, /request\.setRequestHeader\("X-Upload-Id", clientUploadId\)/);
});

test("uploaded files render in chat and Files tab from backend metadata", () => {
    const dashboard = read("src/scripts/room-dashboard.js");
    const css = read("src/styles/room-dashboard.css");

    assert.match(dashboard, /onFiles:\s*receiveRealtimeFile/);
    assert.match(dashboard, /files\.map\(\(file\) => \(\{/);
    assert.match(dashboard, /createFileCard\(message, true, true\)/);
    assert.match(dashboard, /\/files\/\$\{encodeURIComponent\(file\.id\)\}\/content/);
    assert.match(dashboard, /pendingUploadId = generateUuid\(\)/);
    assert.match(css, /\.file-upload-state/);
});

test("QR invitation includes only the validated room code and room PIN", () => {
    const roomCreated = read("src/scripts/pages/Room Created/room_created.js");
    const joinPage = read("src/pages/Join Room/join_room.html");
    const join = read("src/scripts/pages/Join Room/join_room.js");

    assert.match(roomCreated, /searchParams\.set\("room", room\.code\)/);
    assert.match(roomCreated, /searchParams\.set\("pin", room\.pin\)/);
    assert.match(roomCreated, /function getJoinUrl\(\)/);
    assert.doesNotMatch(roomCreated.slice(
        roomCreated.indexOf("function getJoinUrl"),
        roomCreated.indexOf("function getDashboardUrl")
    ), /recoveryCredential|sessionToken|hostRecoveryPin/);
    assert.match(joinPage, /id="room-name"/);
    assert.match(joinPage, /id="pin"/);
    assert.match(join, /roomCode && \/\^\[a-z0-9\]/);
    assert.match(join, /pinInput\.value = \/\^\\d\{6\}\$\/\.test\(pin\) \? pin : ""/);
});

test("host recovery supports same-device credential and PIN-based cross-device flow", () => {
    const createPage = read("src/pages/Create Room/create_room.html");
    const create = read("src/scripts/pages/Create Room/create_room.js");
    const joinPage = read("src/pages/Join Room/join_room.html");
    const join = read("src/scripts/pages/Join Room/join_room.js");
    const backend = read("../backend/src/main/java/com/locallink/backend/room/RoomService.java");
    const migration = read("../backend/src/main/resources/db/migration/V4__add_host_recovery_pin.sql");

    assert.match(createPage, /host-recovery-pin[^>]*pattern="\[0-9\]\{4\}" required/);
    assert.match(create, /hostRecoveryPin: hostRecoveryPinInput\.value/);
    assert.match(joinPage, /Recover Host Access on another device/);
    assert.match(joinPage, /id="recovery-host-pin"/);
    assert.match(join, /fetch\(`\$\{API_BASE_URL\}\/recover-pin`/);
    assert.match(join, /recoveryCredential: recovery\.recoveryCredential/);
    assert.match(backend, /new BCryptPasswordEncoder\(12\)/);
    assert.match(backend, /MAX_HOST_PIN_ATTEMPTS = 5/);
    assert.match(backend, /findByCodeForUpdate/);
    assert.match(migration, /host_recovery_pin_hash/);
    assert.match(migration, /host_recovery_pin_locked_until/);
});

test("host close and member leave use backend actions with confirmation", () => {
    const dashboard = read("src/scripts/room-dashboard.js");
    const host = read("src/pages/Sender dashboard/sender.html");
    const member = read("src/pages/Receiver dashboard/receiver_dashboard.html");
    const backend = read("../backend/src/main/java/com/locallink/backend/room/RoomController.java");

    assert.match(host, /data-close-room/);
    assert.doesNotMatch(host, /<span>Room Settings<\/span>/);
    assert.match(member, /data-leave-room[\s\S]*?Leave Room/);
    assert.match(dashboard, /window\.confirm\(\s*"Close this room for everyone/);
    assert.match(dashboard, /\/close/);
    assert.match(dashboard, /window\.confirm\("Leave this room on this device/);
    assert.match(dashboard, /\/leave/);
    assert.match(dashboard, /connectionManager\?\.stop\(\)/);
    assert.match(backend, /roomService\.closeRoom\(roomId, authenticatedDevice\(request\)\)/);
});

test("both dashboards use the authenticated room identity for participant names", () => {
    const dashboard = read("src/scripts/room-dashboard.js");
    const sender = read("src/pages/Sender dashboard/sender.html");
    const receiver = read("src/pages/Receiver dashboard/receiver_dashboard.html");
    const backend = read("../backend/src/main/java/com/locallink/backend/room/RoomController.java");

    assert.match(dashboard, /\/me/);
    assert.match(dashboard, /identity\.name/);
    assert.match(dashboard, /Room Dashboard/);
    assert.match(sender, /data-dashboard-title/);
    assert.match(receiver, /data-dashboard-title/);
    assert.match(backend, /new RoomMemberIdentity\(\s*device\.getId\(\), device\.getName\(\), device\.getRole\(\)\.name\(\)/);
});

test("incoming messages use a stronger contrast while own-message blue remains", () => {
    const css = read("src/styles/room-dashboard.css");
    assert.match(css, /background:\s*#dce8f7/);
    assert.match(css, /\.message\.is-own \.message-bubble\s*\{[^}]*background:\s*var\(--primary\)/s);
});

test("Create Room follows the shared form-surface and responsive design system", () => {
    const page = read("src/pages/Create Room/create_room.html");
    const css = read("src/styles/pages/Create Room/style.css");
    const global = read("src/styles/global.css");

    assert.match(page, /<main class="create-room-container">/);
    assert.match(page, /<h1 id="create-room-title">Create Room<\/h1>/);
    assert.match(page, /aria-labelledby="options-title"/);
    assert.match(page, /id="auto-close" class="time-select"[\s\S]*?aria-label="Room expiration"/);
    assert.match(css, /width:\s*min\(100% - 32px,\s*560px\)/);
    assert.match(css, /background:\s*var\(--color-surface\)/);
    assert.match(css, /min-height:\s*44px/);
    assert.match(css, /@media \(max-width:\s*480px\)/);
    assert.match(css, /@media \(max-width:\s*360px\)/);
    for (const token of [
        "--color-page-bg",
        "--color-surface",
        "--color-text-primary",
        "--color-primary",
        "--color-border",
        "--color-focus"
    ]) {
        assert.ok(global.includes(token), `missing design token ${token}`);
    }
});
