
(() => {
    const roomCodeInput = document.getElementById("room-name");
    const pinInput = document.getElementById("pin");
    const nameInput = document.getElementById("your-name");
    const scannerButton = document.getElementById("open-qr-scanner");
    const pinVisibilityButton = document.querySelector(".eye-btn");
    const form = document.getElementById("join-room-form");
    const nameCounter = nameInput?.parentElement.querySelector(".char-count");
    const recoverHostButton = document.getElementById("recover-host");
    const recoveryStatus = document.getElementById("host-recovery-status");
    const pinRecoveryDialog =
        document.getElementById("host-pin-recovery-dialog");
    const pinRecoveryForm =
        document.getElementById("host-pin-recovery-form");
    const pinRecoveryStatus =
        document.getElementById("host-pin-recovery-status");

    if (
        !roomCodeInput ||
        !pinInput ||
        !nameInput ||
        !form
    ) {
        console.error("Join Room form elements are missing.");
        return;
    }

    const API_BASE_URL = window.LocalLinkConnection.apiBaseUrl();
    const query = new URLSearchParams(window.location.search);

    const roomCode = query.get("room");
    const pin = query.get("pin");
    const roomName = query.get("roomName");

    if (roomCode && /^[a-z0-9][a-z0-9_-]{2,31}$/i.test(roomCode)) {
        roomCodeInput.value = roomCode;
    }

    if (roomName) {
        document.title = `Join ${roomName} | LocalLink`;
    }
    if (pin) {
        pinInput.value = /^\d{6}$/.test(pin) ? pin : "";
    }

    function openHostDashboard(data) {
        sessionStorage.setItem(
            `locallink-session-${data.roomId}`,
            data.sessionToken
        );
        sessionStorage.setItem(
            `locallink-device-${data.roomId}`,
            data.deviceId
        );
        const dashboardUrl = new URL(
            "../Sender dashboard/sender.html",
            window.location.href
        );
        dashboardUrl.searchParams.set("roomId", data.roomId);
        dashboardUrl.searchParams.set("room", data.code);
        dashboardUrl.searchParams.set("role", data.role);
        window.location.assign(dashboardUrl.toString());
    }

    nameInput.addEventListener("input", () => {
        if (nameCounter) {
            nameCounter.textContent =
                `${nameInput.value.length}/${nameInput.maxLength}`;
        }
    });

    scannerButton?.addEventListener("click", () => {
        window.location.assign("../Scan QR code/scan_QR.html");
    });

    pinVisibilityButton?.addEventListener("click", () => {
        const isVisible = pinInput.type === "text";
        pinInput.type = isVisible ? "password" : "text";

        pinVisibilityButton.setAttribute(
            "aria-label",
            isVisible ? "Show PIN" : "Hide PIN"
        );
    });

    recoverHostButton?.addEventListener("click", async () => {
        const code = roomCodeInput.value.trim().toUpperCase();
        if (!/^[A-Z0-9][A-Z0-9_-]{2,31}$/.test(code)) {
            recoveryStatus.textContent = "Enter a valid room code first.";
            recoveryStatus.hidden = false;
            roomCodeInput.focus();
            return;
        }

        let recovery;
        try {
            const stored = localStorage.getItem(
                `locallink-host-recovery-by-code-${code}`
            );
            recovery = stored ? JSON.parse(stored) : null;
        } catch (error) {
            console.error("Unable to read saved host recovery data:", error);
            recoveryStatus.textContent =
                "Saved host recovery data could not be read in this browser.";
            recoveryStatus.hidden = false;
            return;
        }

        if (
            !recovery ||
            typeof recovery.roomId !== "string" ||
            typeof recovery.recoveryCredential !== "string"
        ) {
            recoveryStatus.textContent =
                "No saved host credential is available here. Use the other-device recovery option; room code and room PIN alone are not enough.";
            recoveryStatus.hidden = false;
            return;
        }

        recoverHostButton.disabled = true;
        recoveryStatus.textContent = "Verifying host access…";
        recoveryStatus.hidden = false;

        try {
            const response = await fetch(`${API_BASE_URL}/recover`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    code,
                    recoveryCredential: recovery.recoveryCredential
                })
            });
            const responseText = await response.text();
            let data = {};
            try {
                data = responseText ? JSON.parse(responseText) : {};
            } catch {
                throw new Error("The server returned an invalid response.");
            }

            if (!response.ok) {
                throw new Error(
                    data.message || "Host recovery was rejected by the server."
                );
            }
            if (
                !data.roomId ||
                !data.deviceId ||
                !data.sessionToken ||
                data.role !== "HOST"
            ) {
                throw new Error("The server returned incomplete host credentials.");
            }

            openHostDashboard(data);
        } catch (error) {
            recoveryStatus.textContent = error.message;
            recoveryStatus.textContent +=
                " You can also recover with your host recovery PIN.";
        } finally {
            recoverHostButton.disabled = false;
        }
    });

    document.getElementById("open-host-pin-recovery")
        ?.addEventListener("click", () => {
            document.getElementById("recovery-room-code").value =
                roomCodeInput.value.trim();
            document.getElementById("recovery-room-pin").value =
                pinInput.value.trim();
            pinRecoveryStatus.hidden = true;
            pinRecoveryDialog.showModal();
        });
    document.getElementById("cancel-host-pin-recovery")
        ?.addEventListener("click", () => pinRecoveryDialog.close());

    pinRecoveryForm?.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!pinRecoveryForm.reportValidity()) {
            return;
        }
        const submitButton = pinRecoveryForm.querySelector(
            'button[type="submit"]'
        );
        submitButton.disabled = true;
        pinRecoveryStatus.hidden = true;
        try {
            const response = await fetch(`${API_BASE_URL}/recover-pin`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    code: document.getElementById("recovery-room-code")
                        .value.trim().toUpperCase(),
                    roomPin: document.getElementById("recovery-room-pin")
                        .value.trim(),
                    hostRecoveryPin: document.getElementById("recovery-host-pin")
                        .value.trim()
                })
            });
            const data = await response.json();
            if (!response.ok) {
                throw new Error(data.message
                    || "Host recovery could not be verified.");
            }
            if (!data.roomId || !data.deviceId || !data.sessionToken
                || data.role !== "HOST") {
                throw new Error("The server returned incomplete host credentials.");
            }
            document.getElementById("recovery-host-pin").value = "";
            pinRecoveryDialog.close();
            openHostDashboard(data);
        } catch (error) {
            pinRecoveryStatus.textContent = error.message;
            pinRecoveryStatus.hidden = false;
        } finally {
            submitButton.disabled = false;
        }
    });

    form.addEventListener("submit", async (event) => {
        event.preventDefault();

        if (!form.reportValidity()) {
            return;
        }

        const submitButton = form.querySelector(
            'button[type="submit"]'
        );

        if (submitButton) {
            submitButton.disabled = true;
            submitButton.textContent = "Joining room...";
        }

        try {
            const requestBody = {
                code: roomCodeInput.value.trim().toUpperCase(),
                pin: pinInput.value.trim(),
                deviceName: nameInput.value.trim(),
                platform: "WEB"
            };

            const response = await fetch(
                `${API_BASE_URL}/join`,
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify(requestBody)
                }
            );

            const responseText = await response.text();

            let data = {};
            try {
                data = responseText
                    ? JSON.parse(responseText)
                    : {};
            } catch {
                data = { message: responseText };
            }

            if (!response.ok) {
                const message =
                    data.detail ||
                    data.message ||
                    data.error ||
                    `Unable to join room (HTTP ${response.status}).`;

                alert(message);
                return;
            }

            // Verify that the backend returned the expected data.
            if (
                !data.roomId ||
                !data.deviceId ||
                !data.sessionToken ||
                data.role !== "GUEST"
            ) {
                console.error(
                    "Unexpected join response. Dashboard navigation cancelled.",
                    data
                );

                alert(
                    "The backend response is incomplete. Please check the server."
                );
                return;
            }

            // Navigate only after successful backend validation.
            const dashboardUrl = new URL(
                "../Receiver dashboard/receiver_dashboard.html",
                window.location.href
            );

            dashboardUrl.searchParams.set("roomId", data.roomId);
            dashboardUrl.searchParams.set("room", data.code);
            dashboardUrl.searchParams.set("name", nameInput.value.trim());

            if (roomName) {
                dashboardUrl.searchParams.set("roomName", roomName);
            }

            if (query.has("filesAllowed")) {
                dashboardUrl.searchParams.set(
                    "filesAllowed",
                    query.get("filesAllowed")
                );
            }

            if (query.has("autoCloseHours")) {
                dashboardUrl.searchParams.set(
                    "autoCloseHours",
                    query.get("autoCloseHours")
                );
            }

            // Tokens remain out of URLs and browser logs.
            sessionStorage.setItem(
                `locallink-session-${data.roomId}`,
                data.sessionToken
            );

            sessionStorage.setItem(
                `locallink-device-${data.roomId}`,
                data.deviceId
            );
            sessionStorage.setItem(
                "locallink-join-success",
                data.roomId
            );

            window.location.assign(dashboardUrl.toString());

        } catch (error) {
            console.error("Join Room request failed:", error);

            alert(
                "Could not connect to the LocalLink backend. " +
                "Check that Spring Boot is running and that CORS is configured."
            );
        } finally {
            if (submitButton) {
                submitButton.disabled = false;
                submitButton.textContent = "Join Room";
            }
        }
    });
})();

console.log("Join Room script loaded successfully.");