(() => {
    function apiBaseUrl() {
        const configuredBaseUrl = window.LocalLinkConfig?.apiBaseUrl;
        if (configuredBaseUrl) {
            const url = new URL(configuredBaseUrl);
            if (url.protocol !== "https:" && url.protocol !== "http:") {
                throw new TypeError(
                    "The LocalLink API URL must use HTTP or HTTPS."
                );
            }
            return url.toString().replace(/\/$/, "");
        }

        const url = new URL(window.location.href);
        url.port = "8080";
        url.pathname = "/api/v1/rooms";
        url.search = "";
        url.hash = "";
        return url.toString().replace(/\/$/, "");
    }

    function websocketUrl() {
        const url = new URL(apiBaseUrl());
        url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
        url.pathname = "/ws";
        return url.toString();
    }

    class RoomConnectionManager {
        constructor({
            roomId,
            token,
            onState,
            onMessage,
            onMembers,
            onFiles = () => {}
        }) {
            this.roomId = roomId;
            this.token = token;
            this.onState = onState;
            this.onMessage = onMessage;
            this.onMembers = onMembers;
            this.onFiles = onFiles;
            this.socket = null;
            this.retryTimer = null;
            this.heartbeatTimer = null;
            this.watchdogTimer = null;
            this.retryDelay = 1000;
            this.buffer = "";
            this.stopped = true;
            this.wasConnected = false;
            this.lastServerActivity = 0;
            this.clientId = `locallink-${Date.now()}-${Math.random()
                .toString(16).slice(2)}`;
        }

        start() {
            if (!this.stopped) {
                return;
            }
            this.stopped = false;
            window.addEventListener("online", this.handleOnline);
            window.addEventListener("offline", this.handleOffline);
            this.connect();
        }

        stop() {
            this.stopped = true;
            window.removeEventListener("online", this.handleOnline);
            window.removeEventListener("offline", this.handleOffline);
            window.clearTimeout(this.retryTimer);
            window.clearInterval(this.heartbeatTimer);
            window.clearInterval(this.watchdogTimer);
            if (this.socket) {
                this.socket.onclose = null;
                this.socket.close();
                this.socket = null;
            }
        }

        retryNow() {
            if (this.stopped) {
                return;
            }
            window.clearTimeout(this.retryTimer);
            this.retryDelay = 1000;
            this.connect();
        }

        connect() {
            if (this.stopped || this.socket
                && this.socket.readyState < WebSocket.CLOSING) {
                return;
            }
            if (!navigator.onLine) {
                this.onState("offline");
                this.scheduleRetry();
                return;
            }

            this.onState(this.wasConnected ? "reconnecting" : "connecting");
            this.buffer = "";
            let socket;
            try {
                socket = new WebSocket(websocketUrl());
            } catch (error) {
                this.onState("unavailable", error);
                this.scheduleRetry();
                return;
            }
            this.socket = socket;
            socket.onopen = () => {
                this.lastServerActivity = Date.now();
                socket.send([
                    "CONNECT",
                    "accept-version:1.2",
                    "heart-beat:10000,10000",
                    `Authorization:Bearer ${this.token}`,
                    "",
                    ""
                ].join("\n") + "\0");
            };
            socket.onmessage = (event) => this.receive(event.data);
            socket.onerror = () => {
                this.onState("unavailable");
            };
            socket.onclose = () => {
                this.clearConnectionTimers();
                this.socket = null;
                if (!this.stopped) {
                    this.onState(navigator.onLine ? "reconnecting" : "offline");
                    this.scheduleRetry();
                }
            };
        }

        receive(chunk) {
            this.lastServerActivity = Date.now();
            this.buffer += chunk;
            let boundary = this.buffer.indexOf("\0");
            while (boundary !== -1) {
                const frame = this.buffer.slice(0, boundary);
                this.buffer = this.buffer.slice(boundary + 1);
                if (frame.trim()) {
                    this.handleFrame(frame);
                }
                boundary = this.buffer.indexOf("\0");
            }
        }

        handleFrame(frame) {
            const separator = frame.indexOf("\n\n");
            const lineEnd = frame.indexOf("\n");
            const command = lineEnd < 0 ? frame : frame.slice(0, lineEnd);
            const body = separator < 0 ? "" : frame.slice(separator + 2);
            if (command === "CONNECTED") {
                this.connected();
                return;
            }
            if (command === "ERROR") {
                this.onState("unavailable", new Error(body || "WebSocket rejected the room session."));
                this.socket?.close();
                return;
            }
            if (command !== "MESSAGE") {
                return;
            }

            const destination = frame.slice(lineEnd + 1, separator).split("\n")
                .find((line) => line.startsWith("destination:"))
                ?.slice("destination:".length);
            try {
                const payload = JSON.parse(body);
                if (destination === `/topic/rooms/${this.roomId}/messages`) {
                    this.onMessage(payload);
                } else if (destination === `/topic/rooms/${this.roomId}/members`) {
                    this.onMembers(payload);
                } else if (destination === `/topic/rooms/${this.roomId}/files`) {
                    this.onFiles(payload);
                }
            } catch (error) {
                this.onState("unavailable", error);
            }
        }

        connected() {
            this.wasConnected = true;
            this.retryDelay = 1000;
            this.onState("connected");
            this.subscribe("messages", "messages");
            this.subscribe("members", "members");
            this.subscribe("files", "files");
            this.clearConnectionTimers();
            this.heartbeatTimer = window.setInterval(() => {
                if (this.socket?.readyState === WebSocket.OPEN) {
                    this.socket.send("\n");
                }
            }, 10_000);
            this.watchdogTimer = window.setInterval(() => {
                if (Date.now() - this.lastServerActivity > 35_000) {
                    this.socket?.close();
                }
            }, 5000);
        }

        subscribe(id, topic) {
            this.socket.send([
                "SUBSCRIBE",
                `id:${this.clientId}-${id}`,
                `destination:/topic/rooms/${this.roomId}/${topic}`,
                "ack:auto",
                "",
                ""
            ].join("\n") + "\0");
        }

        sendMessage(message) {
            if (this.socket?.readyState !== WebSocket.OPEN
                || !this.wasConnected) {
                return false;
            }
            const body = JSON.stringify(message);
            this.socket.send([
                "SEND",
                `destination:/app/rooms/${this.roomId}/messages`,
                "content-type:application/json",
                `content-length:${new TextEncoder().encode(body).length}`,
                "",
                body
            ].join("\n") + "\0");
            return true;
        }

        scheduleRetry() {
            if (this.stopped || this.retryTimer) {
                return;
            }
            const jitteredDelay = this.retryDelay
                + Math.floor(Math.random() * this.retryDelay * 0.2);
            this.retryTimer = window.setTimeout(() => {
                this.retryTimer = null;
                this.connect();
            }, jitteredDelay);
            this.retryDelay = Math.min(this.retryDelay * 2, 30_000);
        }

        clearConnectionTimers() {
            window.clearInterval(this.heartbeatTimer);
            window.clearInterval(this.watchdogTimer);
            this.heartbeatTimer = null;
            this.watchdogTimer = null;
        }

        handleOnline = () => this.retryNow();

        handleOffline = () => {
            this.onState("offline");
            this.socket?.close();
        };
    }

    window.LocalLinkConnection = {
        apiBaseUrl,
        websocketUrl,
        RoomConnectionManager
    };
})();
