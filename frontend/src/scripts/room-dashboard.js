(() => {
    const dashboard = document.querySelector(".dashboard");
    const role = dashboard.dataset.role;
    const roomCodeQuery = new URLSearchParams(window.location.search)
        .get("room");
    const roomCode = /^[a-z0-9][a-z0-9_-]{2,31}$/i.test(roomCodeQuery || "")
        ? roomCodeQuery
        : "";
    const pageQuery = new URLSearchParams(window.location.search);
    const roomId = pageQuery.get("roomId") || "";
    const toast = document.getElementById("dashboard-toast");
    const messageList = document.getElementById("message-list");
    const fileList = document.getElementById("file-list");
    const messageForm = document.getElementById("message-form");
    const messageInput = document.getElementById("message-input");
    const fileInput = document.getElementById("file-input");
    const chatTab = document.getElementById("chat-tab");
    const filesTab = document.getElementById("files-tab");
    const chatPanel = document.getElementById("chat-panel");
    const filesPanel = document.getElementById("files-panel");
    const emojiPicker = document.getElementById("emoji-picker");
    const emojiButton = document.getElementById("emoji-button");
    const tokenKey = `locallink-session-${roomId}`;
    const deviceKey = `locallink-device-${roomId}`;
    const recoveryKey = `locallink-host-recovery-${roomId}`;
    const recoveryByCodeKey =
        `locallink-host-recovery-by-code-${(roomCode || "").toUpperCase()}`;
    const pendingMessageKey = `locallink-pending-message-${roomId}`;
    const apiBaseUrl =
        `${window.location.protocol}//${window.location.hostname}:8080/api/v1/rooms`;
    let toastTimeout;
    let messages = [];
    let files = [];
    let sessionToken = "";
    let deviceId = "";
    let roomExpired = false;
    let presenceTimer;
    let expiryTimer;
    let connectionManager;
    let messagesLoaded = false;
    let filesLoaded = false;
    let activeUpload = null;
    let lastFailedUpload = null;
    let pendingUploadFile = null;
    let pendingUploadId = null;
    let roomClosed = false;
    let roomSettings = {
        roomName: "",
        filesAllowed: false
    };

    function initializeRoomDetails() {
        document.querySelectorAll("[data-room-code]").forEach((element) => {
            element.textContent = roomCode || "No room selected";
        });
        updateMemberCount("Loading members…");
    }

    function receiveRealtimeFile(file) {
        if (!file || file.roomId !== roomId || typeof file.id !== "string") {
            return;
        }
        if (!files.some((existing) => existing.id === file.id)) {
            files.unshift(file);
            filesLoaded = true;
            setFileState("");
            setMessageState(messages.length
                ? ""
                : "No messages yet. Start the conversation.");
            renderFiles();
            renderMessages();
        }
    }

    function readPendingMessage() {
        const saved = sessionStorage.getItem(pendingMessageKey);
        if (!saved) {
            return null;
        }
        const pending = JSON.parse(saved);
        if (
            !pending ||
            typeof pending.clientMessageId !== "string" ||
            typeof pending.content !== "string"
        ) {
            throw new TypeError("Saved pending message is invalid.");
        }
        return pending;
    }

    async function apiRequest(
        path, options = {}, authenticated = true, binary = false
    ) {
        const headers = new Headers(options.headers || {});
        if (
            options.body &&
            !(options.body instanceof FormData) &&
            !headers.has("Content-Type")
        ) {
            headers.set("Content-Type", "application/json");
        }
        if (authenticated && sessionToken) {
            headers.set("Authorization", `Bearer ${sessionToken}`);
        }

        const response = await fetch(`${apiBaseUrl}${path}`, {
            ...options,
            headers
        });
        if (response.ok && binary) {
            return response.blob();
        }
        let data = null;
        if (response.status !== 204) {
            const text = await response.text();
            if (text) {
                try {
                    data = JSON.parse(text);
                } catch {
                    data = { message: "The server returned an invalid response." };
                }
            }
        }
        if (!response.ok) {
            const error = new Error(
                data?.message || `Request failed (HTTP ${response.status}).`
            );
            error.status = response.status;
            error.code = data?.error || "REQUEST_FAILED";
            error.expiresAt = data?.expiresAt;
            throw error;
        }
        return data;
    }

    function updateMemberCount(text) {
        document.querySelectorAll("[data-member-count]").forEach((element) => {
            element.textContent = text;
        });
    }

    function updateConnectionState(state) {
        const element = document.querySelector("[data-connection-status]");
        const states = {
            connecting: "Connecting to the room…",
            connected: "Live connection active",
            reconnecting: "Live connection lost. Reconnecting…",
            offline: "Network unavailable. Waiting to reconnect…",
            unavailable: "Room server unavailable. Retrying automatically…"
        };
        element.textContent = states[state] || states.unavailable;
        element.dataset.state = state;
        element.hidden = false;

        if (state === "connected" && messagesLoaded) {
            void loadMessages().catch(() => {});
            void refreshRoomState();
        }
    }

    function receiveRealtimeMessage(message) {
        if (!message || message.roomId !== roomId
            || typeof message.id !== "string") {
            return;
        }
        if (!messages.some((existing) => existing.id === message.id)) {
            messages.unshift(message);
            messagesLoaded = true;
            setMessageState("");
            renderMessages();
        }
    }

    function receiveMemberUpdate(update) {
        if (!update || update.roomId !== roomId
            || !Number.isInteger(update.memberCount)
            || update.memberCount < 0) {
            return;
        }
        if (update.closed) {
            markRoomClosed();
            return;
        }
        updateMemberCount(
            `${update.memberCount} ${update.memberCount === 1 ? "member" : "members"}`
        );
    }

    function startRealtimeConnection() {
        connectionManager = new window.LocalLinkConnection.RoomConnectionManager({
            roomId,
            token: sessionToken,
            onState: updateConnectionState,
            onMessage: receiveRealtimeMessage,
            onMembers: receiveMemberUpdate,
            onFiles: receiveRealtimeFile
        });
        connectionManager.start();
    }

    function setRoomStatus(message) {
        const status = document.querySelector("[data-room-status]");
        status.textContent = message;
        status.hidden = !message;
    }

    function applyRoomDetails(room) {
        roomSettings.roomName = room.roomName;
        roomSettings.filesAllowed = room.allowFileSharing;
        document.querySelectorAll("[data-room-name]").forEach((element) => {
            element.textContent = room.roomName;
        });
        const expiryTime = new Date(room.expiresAt);
        document.querySelectorAll("[data-room-expiry]").forEach((element) => {
            element.dateTime = room.expiresAt;
            element.textContent = Number.isNaN(expiryTime.getTime())
                ? "Unknown"
                : expiryTime.toLocaleString();
        });
        updateMemberCount(
            `${room.memberCount} ${room.memberCount === 1 ? "member" : "members"}`
        );

        const attachButton = document.getElementById("attach-button");
        attachButton.disabled = !roomSettings.filesAllowed;
        fileInput.disabled = !roomSettings.filesAllowed;
        if (!roomSettings.filesAllowed) {
            attachButton.setAttribute("aria-label", "File sharing is disabled");
        } else {
            attachButton.setAttribute("aria-label", "Attach a file");
        }

        const remaining = expiryTime.getTime() - Date.now();
        window.clearTimeout(expiryTimer);
        if (Number.isFinite(remaining) && remaining <= 0) {
            markRoomExpired(room.expiresAt);
        } else if (Number.isFinite(remaining)) {
            expiryTimer = window.setTimeout(() => {
                if (!roomExpired) {
                    markRoomExpired(room.expiresAt);
                }
            }, Math.min(remaining, 2_147_000_000));
        }
    }

    function markRoomExpired(expiresAt) {
        if (roomExpired) {
            return;
        }
        roomExpired = true;
        window.clearInterval(presenceTimer);
        window.clearTimeout(expiryTimer);
        connectionManager?.stop();
        sessionStorage.removeItem(tokenKey);
        sessionStorage.removeItem(deviceKey);
        localStorage.removeItem(recoveryKey);
        localStorage.removeItem(recoveryByCodeKey);
        const message = document.querySelector("[data-room-expired]");
        message.hidden = false;
        if (expiresAt) {
            message.textContent = `This room expired at ${new Date(expiresAt).toLocaleString()}. Chat, file sharing, and room actions are disabled.`;
        }
        document.querySelectorAll(".dashboard button, .dashboard input")
            .forEach((element) => {
                element.disabled = true;
            });
        document.querySelectorAll(".dashboard a").forEach((link) => {
            link.setAttribute("aria-disabled", "true");
            link.addEventListener("click", (event) => event.preventDefault());
        });
        setRoomStatus("");
    }

    function markRoomClosed() {
        if (roomClosed) {
            return;
        }
        roomClosed = true;
        window.clearInterval(presenceTimer);
        window.clearTimeout(expiryTimer);
        connectionManager?.stop();
        sessionStorage.removeItem(tokenKey);
        sessionStorage.removeItem(deviceKey);
        sessionStorage.removeItem(pendingMessageKey);
        localStorage.removeItem(recoveryKey);
        localStorage.removeItem(recoveryByCodeKey);
        document.querySelectorAll(".dashboard button, .dashboard input")
            .forEach((element) => {
                element.disabled = true;
            });
        document.querySelectorAll(".dashboard a").forEach((link) => {
            link.setAttribute("aria-disabled", "true");
            link.addEventListener("click", (event) => event.preventDefault());
        });
        updateMemberCount("Room closed");
        setRoomStatus("This room is closed. Chat, file sharing, and room actions are disabled.");
        setMessageState("");
        setFileState("");
    }

    async function closeRoom() {
        if (role !== "sender" || roomExpired || roomClosed
            || !window.confirm(
                "Close this room for everyone? Participants will lose access."
            )) {
            return;
        }
        const button = document.querySelector("[data-close-room]");
        button.disabled = true;
        try {
            await apiRequest(`/${encodeURIComponent(roomId)}/close`, {
                method: "POST"
            });
            markRoomClosed();
            setRoomStatus("Room closed. All participants have lost access.");
            showToast("Room closed.");
        } catch (error) {
            if (error.status === 410 && error.code === "ROOM_CLOSED") {
                markRoomClosed();
            }
            showToast(error.message || "The room could not be closed.");
            button.disabled = roomExpired || roomClosed;
        }
    }

    async function leaveRoom() {
        if (role !== "receiver" || roomExpired || roomClosed
            || !window.confirm("Leave this room on this device?")) {
            return;
        }
        const button = document.querySelector("[data-leave-room]");
        button.disabled = true;
        try {
            await apiRequest(`/${encodeURIComponent(roomId)}/leave`, {
                method: "POST"
            });
            connectionManager?.stop();
            window.clearInterval(presenceTimer);
            sessionStorage.removeItem(tokenKey);
            sessionStorage.removeItem(deviceKey);
            sessionStorage.removeItem(pendingMessageKey);
            const joinUrl = new URL(
                "../Join Room/join_room.html",
                window.location.href
            );
            joinUrl.searchParams.set("room", roomCode);
            window.location.assign(joinUrl.toString());
        } catch (error) {
            if (error.status === 410 && error.code === "ROOM_EXPIRED") {
                markRoomExpired(error.expiresAt);
                return;
            }
            if (error.status === 410 && error.code === "ROOM_CLOSED") {
                markRoomClosed();
                return;
            }
            button.disabled = false;
            showToast(error.message || "Could not leave the room.");
        }
    }

    async function recoverHostSession() {
        const credential = localStorage.getItem(recoveryKey);
        if (role !== "sender" || !credential) {
            throw new Error("This room session is unavailable. Rejoin the room or recover the host session.");
        }
        const result = await apiRequest("/recover", {
            method: "POST",
            body: JSON.stringify({
                code: roomCode,
                recoveryCredential: credential
            })
        }, false);
        sessionToken = result.sessionToken;
        deviceId = result.deviceId;
        sessionStorage.setItem(tokenKey, sessionToken);
        sessionStorage.setItem(deviceKey, deviceId);
    }

    async function getRoomDetails() {
        return apiRequest(`/${encodeURIComponent(roomId)}`);
    }

    async function loadCurrentMember() {
        const identity = await apiRequest(
            `/${encodeURIComponent(roomId)}/me`
        );
        const expectedRole = role === "sender" ? "HOST" : "GUEST";
        if (!identity || identity.role !== expectedRole
            || typeof identity.deviceId !== "string") {
            throw new Error("The authenticated room identity is unavailable.");
        }
        deviceId = identity.deviceId;
        const participantName = typeof identity.name === "string"
            ? identity.name.trim()
            : "";
        const baseTitle = role === "sender"
            ? "Host Dashboard"
            : "Room Dashboard";
        const title = document.querySelector("[data-dashboard-title]");
        if (title) {
            title.textContent = participantName
                ? `${baseTitle} (${participantName})`
                : baseTitle;
        }
        updateAttachmentPreview();
    }

    async function refreshRoomState() {
        if (!roomId || !sessionToken || roomExpired || roomClosed) {
            return;
        }
        try {
            await apiRequest(`/${encodeURIComponent(roomId)}/presence`, {
                method: "POST"
            });
            const room = await getRoomDetails();
            applyRoomDetails(room);
            setRoomStatus("");
            await Promise.all([
                loadMessages(),
                loadFiles()
            ]);
            try {
                const pending = readPendingMessage();
                if (pending && !messageInput.value) {
                    messageInput.value = pending.content;
                }
            } catch (error) {
                console.error("Unable to restore pending message:", error);
                setMessageState("A pending message could not be restored.");
            }
        } catch (error) {
            if (error.status === 410 && error.code === "ROOM_EXPIRED") {
                markRoomExpired(error.expiresAt);
                return;
            }
            if (error.status === 410 && error.code === "ROOM_CLOSED") {
                markRoomClosed();
                return;
            }
            updateMemberCount("Members unavailable");
            setRoomStatus("Room state could not be refreshed. Check your connection.");
            if (!messagesLoaded) {
                setMessageState("Messages could not be loaded. Check your connection.");
            }
            if (!filesLoaded) {
                setFileState("Files could not be loaded. Check your connection.");
            }
        }
    }

    function setMessageState(message) {
        const state = document.querySelector("[data-message-state]");
        if (!state) {
            return;
        }
        state.textContent = message;
        state.hidden = !message;
    }

    function setFileState(message) {
        const state = document.querySelector("[data-file-state]");
        if (!state) {
            return;
        }
        state.textContent = message;
        state.hidden = !message;
    }

    async function loadMessages() {
        try {
            const result = await apiRequest(
                `/${encodeURIComponent(roomId)}/messages`
            );
            messages = result;
            messagesLoaded = true;
            setMessageState(messages.length || files.length
                ? ""
                : "No messages yet. Start the conversation.");
            renderMessages();
        } catch (error) {
            if (error.status === 410 && error.code === "ROOM_EXPIRED") {
                markRoomExpired(error.expiresAt);
                throw error;
            }
            if (error.status === 410 && error.code === "ROOM_CLOSED") {
                markRoomClosed();
                throw error;
            }
            setMessageState("Messages could not be loaded. Check your connection.");
            throw error;
        }
    }

    async function loadFiles() {
        try {
            const allFiles = [];
            let page = 0;
            let batch;
            do {
                batch = await apiRequest(
                    `/${encodeURIComponent(roomId)}/files?page=${page}&size=100`
                );
                allFiles.push(...batch);
                page += 1;
            } while (batch.length === 100);
            files = Array.from(
                new Map(allFiles.map((file) => [file.id, file])).values()
            );
            filesLoaded = true;
            setFileState(files.length ? "" : "No files have been shared in this room yet.");
            renderFiles();
            renderMessages();
        } catch (error) {
            if (error.status === 410 && error.code === "ROOM_EXPIRED") {
                markRoomExpired(error.expiresAt);
                throw error;
            }
            if (error.status === 410 && error.code === "ROOM_CLOSED") {
                markRoomClosed();
                throw error;
            }
            setFileState("Files could not be loaded. Check your connection.");
            throw error;
        }
    }

    async function initializeRoom() {
        if (!roomId || !roomCode) {
            setRoomStatus("Room information is missing. Open this dashboard from a valid room.");
            updateMemberCount("Members unavailable");
            return;
        }

        sessionToken = sessionStorage.getItem(tokenKey) || "";
        deviceId = sessionStorage.getItem(deviceKey) || "";

        try {
            if (!sessionToken && role === "sender") {
                await recoverHostSession();
            }
            try {
                const room = await getRoomDetails();
                applyRoomDetails(room);
            } catch (error) {
                if (error.status === 401 && role === "sender") {
                    await recoverHostSession();
                    const room = await getRoomDetails();
                    applyRoomDetails(room);
                } else {
                    throw error;
                }
            }
            await loadCurrentMember();
            await refreshRoomState();
            startRealtimeConnection();
            presenceTimer = window.setInterval(refreshRoomState, 15_000);
        } catch (error) {
            if (error.status === 410 && error.code === "ROOM_EXPIRED") {
                markRoomExpired(error.expiresAt);
                return;
            }
            if (error.status === 410 && error.code === "ROOM_CLOSED") {
                markRoomClosed();
                return;
            }
            updateMemberCount("Members unavailable");
            setRoomStatus(error.message);
            const recoveryLink = document.querySelector(
                "[data-host-recovery-link]"
            );
            if (role === "sender" && recoveryLink) {
                recoveryLink.href =
                    `../Join Room/join_room.html?room=${encodeURIComponent(roomCode)}`;
                recoveryLink.hidden = false;
                setRoomStatus(
                    "Host authentication failed. Recover using the saved credential or your host recovery PIN."
                );
            }
            setMessageState("Unable to connect to this room.");
            setFileState("Unable to connect to this room.");
            document.querySelectorAll("#message-form input, #message-form button, #file-input")
                .forEach((element) => {
                    element.disabled = true;
                });
        }
    }

    function formatSize(bytes) {
        if (!Number.isFinite(bytes) || bytes < 0) {
            return "Unknown size";
        }
        if (bytes >= 1024 * 1024) {
            return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
        }
        if (bytes >= 1024) {
            return `${Math.round(bytes / 1024)} KB`;
        }
        return `${bytes} B`;
    }

    function showToast(message) {
        toast.textContent = message;
        toast.classList.add("is-visible");
        window.clearTimeout(toastTimeout);
        toastTimeout = window.setTimeout(() => {
            toast.classList.remove("is-visible");
        }, 2600);
    }

    function createFileCard(file, allowDownload, inChat = false) {
        const card = document.createElement("div");
        card.className = "file-message";

        const typeIcon = document.createElement("span");
        typeIcon.className = "file-type-icon";
        typeIcon.setAttribute("aria-hidden", "true");
        const filename = file.originalFilename || file.name;
        typeIcon.textContent = filename.split(".").pop().slice(0, 3)
            .toUpperCase();

        const details = document.createElement("span");
        details.className = "file-details";
        const name = document.createElement("button");
        name.className = "file-open";
        name.type = "button";
        name.textContent = filename;
        const size = document.createElement("span");
        size.textContent = inChat
            ? formatSize(file.sizeBytes ?? file.size)
            : `${file.uploaderName || "Unknown member"} · ${formatSize(file.sizeBytes ?? file.size)} · ${new Date(file.createdAt).toLocaleString()}`;
        details.append(name, size);
        name.disabled = !allowDownload;
        name.addEventListener("click", () => {
            if (allowDownload) {
                void downloadFile(file);
            }
        });

        const download = document.createElement("button");
        download.className = "download-button";
        download.type = "button";
        download.setAttribute("aria-label", `Download ${filename}`);
        download.innerHTML = `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 3v12m-5-5 5 5 5-5M4 17v3h16v-3"/>
            </svg>`;

        if (!allowDownload) {
            download.disabled = true;
            download.title = "File download is unavailable.";
        } else {
            download.addEventListener("click", () => void downloadFile(file));
        }

        if (inChat) {
            const uploadState = document.createElement("span");
            uploadState.className = "file-upload-state";
            uploadState.textContent = "Uploaded";
            details.append(uploadState);
        }
        card.append(typeIcon, details, download);
        return card;
    }

    function renderMessages() {
        messageList.replaceChildren();
        const timeline = [
            ...messages.map((message) => ({
                kind: "message",
                item: message,
                timestamp: message.createdAt
            })),
            ...files.map((file) => ({
                kind: "file",
                item: file,
                timestamp: file.createdAt
            }))
        ].sort((left, right) =>
            new Date(left.timestamp).getTime()
                - new Date(right.timestamp).getTime()
        );
        timeline.forEach(({ kind, item: message }) => {
            const item = document.createElement("article");
            item.className = "message";

            const isOwn = Boolean(deviceId)
                && (kind === "file"
                    ? message.uploaderDeviceId === deviceId
                    : message.senderDeviceId === deviceId);
            if (isOwn) {
                item.classList.add("is-own");
            }

            const avatar = document.createElement("span");
            avatar.className = "avatar";
            avatar.setAttribute("aria-hidden", "true");
            avatar.textContent =
                (isOwn ? "Y" : message.senderName || "?").slice(0, 1)
                .toUpperCase();

            const body = document.createElement("div");
            body.className = "message-body";
            const meta = document.createElement("p");
            meta.className = "message-meta";
            const author = document.createElement("strong");
            const senderName = kind === "file"
                ? message.uploaderName
                : message.senderName;
            author.textContent = isOwn
                ? "You"
                : typeof senderName === "string" && senderName.trim()
                    ? senderName.trim()
                    : "Unknown member";
            const time = document.createElement("span");
            time.textContent = new Date(message.createdAt).toLocaleTimeString([], {
                hour: "numeric",
                minute: "2-digit"
            });
            meta.append(author, time);

            body.append(meta);
            if (kind === "file") {
                body.append(createFileCard(message, true, true));
            } else if (message.content) {
                const bubble = document.createElement("p");
                bubble.className = "message-bubble";
                bubble.textContent = message.content;
                body.append(bubble);
            }

            item.append(avatar, body);
            messageList.append(item);
        });
        messageList.scrollTop = messageList.scrollHeight;
    }

    function renderFiles() {
        fileList.replaceChildren();
        files.forEach((file) => {
            fileList.append(createFileCard(file, true));
        });
        document.getElementById("files-heading").textContent =
            `${files.length} shared ${files.length === 1 ? "file" : "files"}`;
    }

    function selectTab(selectedTab) {
        const showChat = selectedTab === chatTab;
        chatTab.setAttribute("aria-selected", String(showChat));
        chatTab.tabIndex = showChat ? 0 : -1;
        filesTab.setAttribute("aria-selected", String(!showChat));
        filesTab.tabIndex = showChat ? -1 : 0;
        chatPanel.hidden = !showChat;
        filesPanel.hidden = showChat;

        if (!showChat) {
            renderMessages();
        }
    }

    function updateRoomInvitation() {
        const joinUrl = new URL("../Join Room/join_room.html", window.location.href);
        joinUrl.searchParams.set("room", roomCode);

        const inviteLink = document.querySelector("[data-invite-link]");
        if (!inviteLink) {
            return;
        }

        inviteLink.href = joinUrl.toString();
        inviteLink.textContent = joinUrl.toString();

        document.querySelectorAll("[data-copy-invite]").forEach((button) => {
            button.addEventListener("click", () => copyText(joinUrl.toString()));
        });
        document.querySelectorAll('[data-action="invite"]').forEach((button) => {
            button.addEventListener("click", () => {
                document.getElementById("invite-dialog").showModal();
            });
        });
        document.querySelectorAll('[data-action="qr"]').forEach((button) => {
            button.addEventListener("click", () => openQrDialog(joinUrl));
        });
        document.querySelectorAll('[data-action="share"]').forEach((button) => {
            button.addEventListener("click", shareInvite);
        });
    }

    async function copyText(text) {
        try {
            if (navigator.clipboard && window.isSecureContext) {
                await navigator.clipboard.writeText(text);
            } else {
                const input = document.createElement("textarea");
                input.value = text;
                input.setAttribute("readonly", "");
                input.style.position = "fixed";
                input.style.opacity = "0";
                document.body.append(input);
                input.select();
                const copied = document.execCommand("copy");
                input.remove();

                if (!copied) {
                    throw new Error("Clipboard copy was rejected.");
                }
            }
            showToast("Invite link copied.");
        } catch (error) {
            console.error("Unable to copy invite link:", error);
            showToast("Could not copy the invite link.");
        }
    }

    async function shareInvite() {
        const link = document.querySelector("[data-invite-link]").href;
        const shareData = {
            title: `Join ${roomSettings.roomName || "this room"}`,
            text: `Join LocalLink room ${roomCode}.`,
            url: link
        };

        if (navigator.share) {
            try {
                await navigator.share(shareData);
            } catch (error) {
                if (error.name !== "AbortError") {
                    console.error("Unable to share room invite:", error);
                    showToast("Could not open the sharing menu.");
                }
            }
            return;
        }

        await copyText(`${shareData.text}\n${shareData.url}`);
    }

    function openQrDialog(joinUrl) {
        try {
            if (typeof window.qrcode !== "function") {
                throw new Error("The QR generator could not be loaded.");
            }

            const code = window.qrcode(-1, "M");
            code.addData(joinUrl.toString());
            code.make();
            document.getElementById("room-qr-image").src =
                code.createDataURL(5, 4);
            document.getElementById("qr-dialog").showModal();
        } catch (error) {
            console.error("Unable to create room QR code:", error);
            showToast("Could not create a QR code for this room.");
        }
    }

    function closeDialogs() {
        document.querySelectorAll(".dashboard-dialog").forEach((dialog) => {
            if (dialog.open) {
                dialog.close();
            }
        });
    }

    function downloadFile(file) {
        return apiRequest(
            `/${encodeURIComponent(roomId)}/files/${encodeURIComponent(file.id)}/content`,
            {},
            true,
            true
        ).then((blob) => {
            const objectUrl = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.href = objectUrl;
            link.download = file.originalFilename;
            document.body.append(link);
            link.click();
            link.remove();
            window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
        }).catch((error) => {
            if (error.status === 410 && error.code === "ROOM_EXPIRED") {
                markRoomExpired(error.expiresAt);
            }
            showToast(error.message || "File download failed.");
        });
    }

    async function submitMessage(content) {
        if (!sessionToken || roomExpired || roomClosed) {
            showToast("This room is not available for sending messages.");
            return;
        }
        const submitButton = messageForm.querySelector(
            'button[type="submit"]'
        );
        let pending;
        try {
            pending = readPendingMessage();
        } catch (error) {
            console.error("Unable to read pending message:", error);
            setMessageState("The pending message could not be read; refresh before sending.");
            return false;
        }
        if (pending && pending.content !== content) {
            setMessageState("Retry or clear the pending message before sending another.");
            return false;
        }
        const clientMessageId = pending?.clientMessageId || generateUuid();
        if (!pending) {
            sessionStorage.setItem(pendingMessageKey, JSON.stringify({
                clientMessageId,
                content
            }));
        }
        submitButton.disabled = true;
        messageInput.disabled = true;
        setMessageState("Sending message…");
        try {
            const message = await apiRequest(`/${encodeURIComponent(roomId)}/messages`, {
                method: "POST",
                body: JSON.stringify({ clientMessageId, content })
            });
            sessionStorage.removeItem(pendingMessageKey);
            if (!messages.some((existing) => existing.id === message.id)) {
                messages.unshift(message);
            }
            messagesLoaded = true;
            setMessageState("");
            renderMessages();
            return true;
        } catch (error) {
            if (error.status === 410 && error.code === "ROOM_EXPIRED") {
                markRoomExpired(error.expiresAt);
            } else if (error.status === 410
                && error.code === "ROOM_CLOSED") {
                markRoomClosed();
            }
            setMessageState(error.message || "Message could not be sent.");
            return false;
        } finally {
            submitButton.disabled = roomExpired;
            messageInput.disabled = roomExpired;
        }
    }

    function generateUuid() {
        if (window.crypto?.randomUUID) {
            return window.crypto.randomUUID();
        }
        if (window.crypto?.getRandomValues) {
            const bytes = window.crypto.getRandomValues(new Uint8Array(16));
            bytes[6] = (bytes[6] & 0x0f) | 0x40;
            bytes[8] = (bytes[8] & 0x3f) | 0x80;
            return Array.from(bytes, (byte) =>
                byte.toString(16).padStart(2, "0")
            ).join("").replace(
                /^(.{8})(.{4})(.{4})(.{4})(.{12})$/,
                "$1-$2-$3-$4-$5"
            );
        }
        return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(
            /[xy]/g,
            (character) => {
                const random = Math.floor(Math.random() * 16);
                return (character === "x" ? random : (random & 0x3) | 0x8)
                    .toString(16);
            }
        );
    }

    function updateAttachmentPreview() {
        const preview = document.querySelector("[data-attachment-preview]");
        const uploadButton = document.querySelector("[data-upload-send]");
        const name = document.querySelector("[data-attachment-name]");
        const size = document.querySelector("[data-attachment-size]");
        const status = document.querySelector("[data-attachment-status]");
        if (!preview || !uploadButton || !name || !size) {
            return;
        }
        preview.hidden = !pendingUploadFile;
        if (!pendingUploadFile) {
            name.textContent = "";
            size.textContent = "";
            if (status) {
                status.textContent = "";
            }
            return;
        }
        name.textContent = pendingUploadFile.name;
        size.textContent = formatSize(pendingUploadFile.size);
        uploadButton.textContent = activeUpload
            ? "Uploading…"
            : "Upload & Send";
        uploadButton.disabled = Boolean(activeUpload)
            || roomExpired || roomClosed || !sessionToken;
    }

    function setAttachmentStatus(message) {
        const status = document.querySelector("[data-attachment-status]");
        if (status) {
            status.textContent = message;
        }
    }

    async function uploadFile(file, clientUploadId = pendingUploadId) {
        if (!sessionToken || roomExpired || roomClosed) {
            showToast("This room is not available for file sharing.");
            return;
        }
        if (!roomSettings.filesAllowed) {
            showToast("File sharing is disabled for this room.");
            return;
        }
        if (activeUpload || !file || !clientUploadId) {
            return;
        }

        const attachButton = document.getElementById("attach-button");
        const progress = document.querySelector("[data-upload-progress]");
        const cancelButton = document.querySelector("[data-cancel-upload]");
        const retryButton = document.querySelector("[data-retry-upload]");
        attachButton.disabled = true;
        lastFailedUpload = null;
        retryButton.hidden = true;
        progress.value = 0;
        progress.hidden = false;
        cancelButton.hidden = false;
        setFileState(`Uploading ${file.name} (0%)…`);
        setAttachmentStatus("Upload in progress: 0%");
        updateAttachmentPreview();
        try {
            const formData = new FormData();
            formData.append("file", file, file.name);
            const uploaded = await uploadWithProgress(
                file,
                formData,
                clientUploadId,
                (percent) => {
                    progress.value = percent;
                    setFileState(`Uploading ${file.name} (${percent}%)…`);
                    setAttachmentStatus(`Upload in progress: ${percent}%`);
                });
            if (!uploaded || typeof uploaded.id !== "string") {
                throw new Error("The server did not confirm the file upload.");
            }
            if (!files.some((existing) => existing.id === uploaded.id)) {
                files.unshift(uploaded);
            }
            filesLoaded = true;
            renderFiles();
            renderMessages();
            setFileState("File uploaded successfully.");
            showToast(`${file.name} uploaded.`);
            pendingUploadFile = null;
            pendingUploadId = null;
            updateAttachmentPreview();
            void loadFiles().catch((error) => {
                setFileState(error.message
                    || "File uploaded, but the Files list could not refresh.");
            });
        } catch (error) {
            if (error.status === 410 && error.code === "ROOM_EXPIRED") {
                markRoomExpired(error.expiresAt);
            } else if (error.status === 410
                && error.code === "ROOM_CLOSED") {
                markRoomClosed();
            }
            if (error.name === "AbortError") {
                setFileState("Upload cancelled. You can retry or remove the selected file.");
                setAttachmentStatus(
                    "Upload cancelled. Retry or remove this file."
                );
            } else {
                setFileState(error.message || "File upload failed.");
                setAttachmentStatus(
                    error.message || "Upload failed. You can retry."
                );
                lastFailedUpload = file;
                retryButton.hidden = false;
            }
        } finally {
            activeUpload = null;
            progress.hidden = true;
            cancelButton.hidden = true;
            attachButton.disabled = roomExpired || !roomSettings.filesAllowed;
            updateAttachmentPreview();
        }
    }

    function uploadWithProgress(file, formData, clientUploadId, onProgress) {
        return new Promise((resolve, reject) => {
            const request = new XMLHttpRequest();
            activeUpload = request;
            request.open(
                "POST",
                `${apiBaseUrl}/${encodeURIComponent(roomId)}/files`
            );
            request.setRequestHeader(
                "Authorization", `Bearer ${sessionToken}`
            );
            request.setRequestHeader("X-Upload-Id", clientUploadId);
            request.upload.addEventListener("progress", (event) => {
                if (event.lengthComputable && event.total > 0) {
                    onProgress(Math.min(
                        99,
                        Math.floor(event.loaded / event.total * 100)
                    ));
                }
            });
            request.addEventListener("load", () => {
                let response = {};
                try {
                    response = request.responseText
                        ? JSON.parse(request.responseText)
                        : {};
                } catch {
                    reject(new Error("The server returned an invalid upload response."));
                    return;
                }
                if (request.status >= 200 && request.status < 300) {
                    onProgress(100);
                    resolve(response);
                    return;
                }
                const error = new Error(
                    response.message || `Upload failed (HTTP ${request.status}).`
                );
                error.status = request.status;
                error.code = response.error || "UPLOAD_FAILED";
                error.expiresAt = response.expiresAt;
                reject(error);
            });
            request.addEventListener("error", () => {
                reject(new Error("Upload interrupted. Check the connection and retry."));
            });
            request.addEventListener("abort", () => {
                const error = new Error("Upload cancelled.");
                error.name = "AbortError";
                reject(error);
            });
            request.send(formData);
        });
    }

    chatTab.addEventListener("click", () => selectTab(chatTab));
    filesTab.addEventListener("click", () => selectTab(filesTab));
    [chatTab, filesTab].forEach((tab) => {
        tab.addEventListener("keydown", (event) => {
            if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
                event.preventDefault();
                const nextTab = tab === chatTab ? filesTab : chatTab;
                selectTab(nextTab);
                nextTab.focus();
            }
        });
    });

    messageForm.addEventListener("submit", (event) => {
        event.preventDefault();
        const value = messageInput.value.trim();
        if (!value) {
            messageInput.focus();
            return;
        }

        void submitMessage(value).then((sent) => {
            if (sent) {
                messageInput.value = "";
                messageInput.focus();
            }
        });
    });

    document.getElementById("attach-button").addEventListener("click", () => {
        if (roomSettings.filesAllowed && !roomExpired) {
            fileInput.click();
        } else {
            showToast("File sharing is disabled for this room.");
        }
    });

    fileInput.addEventListener("change", () => {
        const selectedFile = fileInput.files?.[0] || null;
        fileInput.value = "";
        if (!selectedFile) {
            return;
        }
        if (activeUpload) {
            showToast("Wait for the current upload to finish or cancel it.");
            return;
        }
        pendingUploadFile = selectedFile;
        pendingUploadId = generateUuid();
        setAttachmentStatus("Ready. Choose Upload & Send to share this file.");
        updateAttachmentPreview();
    });
    document.querySelector("[data-upload-send]").addEventListener(
        "click",
        () => {
            if (pendingUploadFile) {
                void uploadFile(pendingUploadFile, pendingUploadId);
            }
        }
    );
    document.querySelector("[data-remove-attachment]").addEventListener(
        "click",
        () => {
            activeUpload?.abort();
            pendingUploadFile = null;
            pendingUploadId = null;
            lastFailedUpload = null;
            document.querySelector("[data-retry-upload]").hidden = true;
            updateAttachmentPreview();
            setFileState("");
        }
    );
    document.querySelector("[data-cancel-upload]").addEventListener(
        "click",
        () => activeUpload?.abort()
    );
    document.querySelector("[data-retry-upload]").addEventListener(
        "click",
        () => {
            if (lastFailedUpload && pendingUploadId) {
                void uploadFile(lastFailedUpload, pendingUploadId);
            }
        }
    );

    document.querySelector("[data-close-room]")
        ?.addEventListener("click", () => void closeRoom());
    document.querySelector("[data-leave-room]")
        ?.addEventListener("click", () => void leaveRoom());

    emojiButton.addEventListener("click", () => {
        const expanded = emojiButton.getAttribute("aria-expanded") === "true";
        emojiButton.setAttribute("aria-expanded", String(!expanded));
        emojiPicker.hidden = expanded;
    });

    emojiPicker.addEventListener("click", (event) => {
        const choice = event.target.closest(".emoji-choice");
        if (!choice) {
            return;
        }

        const start = messageInput.selectionStart;
        const end = messageInput.selectionEnd;
        const value = messageInput.value;
        messageInput.value =
            value.slice(0, start) + choice.textContent + value.slice(end);
        messageInput.setSelectionRange(
            start + choice.textContent.length,
            start + choice.textContent.length
        );
        messageInput.focus();
        emojiPicker.hidden = true;
        emojiButton.setAttribute("aria-expanded", "false");
    });

    document.querySelectorAll("[data-close-dialog]").forEach((button) => {
        button.addEventListener("click", closeDialogs);
    });
    document.querySelector("[data-connection-help]").addEventListener(
        "click",
        () => document.getElementById("connection-help-dialog").showModal()
    );
    document.querySelector("[data-connection-retry]").addEventListener(
        "click",
        () => {
            document.getElementById("connection-help-dialog").close();
            connectionManager?.retryNow();
            void refreshRoomState();
        }
    );
    document.querySelectorAll(".dashboard-dialog").forEach((dialog) => {
        dialog.addEventListener("click", (event) => {
            if (event.target === dialog) {
                dialog.close();
            }
        });
    });

    initializeRoomDetails();
    const connectedBanner = document.querySelector(".connected-banner");
    const joinedRoomId = sessionStorage.getItem("locallink-join-success");
    if (connectedBanner && joinedRoomId === roomId) {
        connectedBanner.hidden = false;
        sessionStorage.removeItem("locallink-join-success");
    }
    updateRoomInvitation();
    void initializeRoom();

    window.addEventListener("pagehide", () => {
        window.clearInterval(presenceTimer);
        connectionManager?.stop();
        if (!sessionToken || !roomId || roomExpired) {
            return;
        }
        void fetch(`${apiBaseUrl}/${encodeURIComponent(roomId)}/leave`, {
            method: "POST",
            headers: {
                Authorization: `Bearer ${sessionToken}`
            },
            keepalive: true
        });
    });
})();
