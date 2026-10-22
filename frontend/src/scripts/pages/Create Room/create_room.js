
(() => {
    const form = document.getElementById("create-room-form");

    if (!form) {
        return;
    }

    const roomNameInput = document.getElementById("room-name");
    const userNameInput = document.getElementById("your-name");
    const hostRecoveryPinInput =
        document.getElementById("host-recovery-pin");
    const filesAllowedInput = document.getElementById("files-allowed");
    const autoCloseEnabledInput = document.getElementById("auto-close-enabled");
    const autoCloseInput = document.getElementById("auto-close");
    const submitButton = form.querySelector('button[type="submit"]');

    [roomNameInput, userNameInput].forEach((input) => {
        const counter = input.parentElement.querySelector(".char-count");

        const updateCounter = () => {
            if (counter) {
                counter.textContent =
                    `${input.value.length}/${input.maxLength}`;
            }

            input.setCustomValidity(
                input.value.trim() ? "" : "This field is required."
            );
        };

        input.addEventListener("input", updateCounter);
        updateCounter();
    });

    autoCloseEnabledInput.addEventListener("change", () => {
        autoCloseInput.disabled = !autoCloseEnabledInput.checked;
    });

    autoCloseInput.disabled = !autoCloseEnabledInput.checked;

    form.addEventListener("submit", async (event) => {
        event.preventDefault();

        if (!form.reportValidity()) {
            return;
        }

        const originalButtonText = submitButton.textContent;

        try {
            submitButton.disabled = true;
            submitButton.textContent = "Creating room...";

            const selectedHours = autoCloseEnabledInput.checked
                ? Number(autoCloseInput.value)
                : 0;

            // The backend accepts TTL in minutes, not hours.
            // The backend allows 5–1440 minutes.
            const ttlMinutes = selectedHours === 0
                ? 60
                : selectedHours * 60;

            const requestBody = {
                roomName: roomNameInput.value.trim(),
                deviceName: userNameInput.value.trim(),
                platform: navigator.platform || "Unknown",
                allowFileSharing: filesAllowedInput.checked,
                maxMembers: 8,
                ttlMinutes: ttlMinutes,
                quickJoin: false,
                hostRecoveryPin: hostRecoveryPinInput.value,
                publicKey: null
            };

            const response = await fetch(
                `${window.LocalLinkConnection.apiBaseUrl()}`,
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify(requestBody)
                }
            );


            const responseText = await response.text();

            let data;
            try {
                data = JSON.parse(responseText);
            } catch {
                throw new Error(
                    responseText || `Server returned HTTP ${response.status}`
                );
            }

            if (!response.ok) {
                throw new Error(
                    data.message || data.error || `Server returned HTTP ${response.status}`
                );
            }

            if (!data.code || !/^\d{6}$/.test(String(data.pin || ""))) {
                throw new Error("Backend response is missing a valid room code or six-digit PIN.");
            }


            // Use the real response returned by Spring Boot.
            const roomUrl = new URL(
                "../Room Created/room_created.html",
                window.location.href
            );

            roomUrl.searchParams.set("room", data.code);
            roomUrl.searchParams.set("pin", data.pin);
            roomUrl.searchParams.set(
                "roomName",
                requestBody.roomName
            );
            roomUrl.searchParams.set(
                "host",
                requestBody.deviceName
            );
            roomUrl.searchParams.set(
                "filesAllowed",
                String(requestBody.allowFileSharing)
            );
            roomUrl.searchParams.set(
                "autoCloseHours",
                String(selectedHours)
            );
            roomUrl.searchParams.set(
                "roomId",
                data.roomId
            );
            roomUrl.searchParams.set(
                "expiresAt",
                data.expiresAt
            );
            roomUrl.searchParams.set(
                "deviceId",
                data.deviceId
            );
            if (!data.sessionToken) {
                throw new Error("The server did not return a session token.");
            }
            if (!data.hostRecoveryCredential) {
                throw new Error("The server did not return a host recovery credential.");
            }

            sessionStorage.setItem(
                `locallink-session-${data.roomId}`,
                data.sessionToken
            );
            sessionStorage.setItem(
                `locallink-device-${data.roomId}`,
                data.deviceId
            );
            localStorage.setItem(
                `locallink-host-recovery-${data.roomId}`,
                data.hostRecoveryCredential
            );
            localStorage.setItem(
                `locallink-host-recovery-by-code-${data.code.toUpperCase()}`,
                JSON.stringify({
                    roomId: data.roomId,
                    recoveryCredential: data.hostRecoveryCredential
                })
            );
            sessionStorage.setItem(
                "locallink-room-created-success",
                data.roomId
            );
            roomUrl.searchParams.set(
                "role",
                data.role
            );

            window.location.assign(roomUrl.toString());

        } catch (error) {
            console.error("Create room error:", error);

            alert(
                "Unable to create the room from the backend. No room code or PIN was generated by the frontend.\n\n" +
                error.message +
                "\n\nCheck the browser console for the request and backend response JSON."
            );
        } finally {
            submitButton.disabled = false;
            submitButton.textContent = originalButtonText;
        }
    });
})();
