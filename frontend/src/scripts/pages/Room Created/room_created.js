
// LocalLink - Room Created Page

const roomCodeElement = document.getElementById("room-code");
const roomPinElement = document.getElementById("room-pin");
const toastElement = document.getElementById("toast");

const settingsDialog = document.getElementById("settings-dialog");
const settingsForm = document.getElementById("settings-form");

const filesAllowedInput = document.getElementById("files-allowed");
const autoCloseInput = document.getElementById("auto-close");
const settingsSummary = document.getElementById("settings-summary");

const joinLinkButton = document.getElementById("copy-join-link");
const shareQrButton = document.getElementById("share-qr");
const shareViaButton = document.getElementById("share-via");

let toastTimeout;


const query = new URLSearchParams(window.location.search);

const requestedRoomCode = query.get("room") || "";
const requestedPin = query.get("pin") || "";

if (!/^[A-Z0-9_-]{3,32}$/i.test(requestedRoomCode)) {
    document.querySelector(".room-card").textContent =
        "Room code is missing or invalid. Please create the room again.";
    throw new Error("Missing or invalid room code.");
}

if (!/^\d{6}$/.test(requestedPin)) {
    document.querySelector(".room-card").textContent =
        "Room PIN is missing or invalid. Please create the room again.";
    throw new Error("Missing or invalid room PIN.");
}

if (!query.get("roomId") || !query.get("deviceId") || !query.get("expiresAt")) {
    document.querySelector(".room-card").textContent =
        "The backend response is incomplete. Please create the room again.";
    throw new Error("Backend response is missing room metadata.");
}

const room = {
    code: requestedRoomCode,
    pin: requestedPin,
    roomName: query.get("roomName") || "",
    filesAllowed: query.get("filesAllowed") !== "false",
    autoCloseHours: ["0", "1", "12", "24"].includes(
        query.get("autoCloseHours")
    )
        ? Number(query.get("autoCloseHours"))
        : 1
};

const savedRecoveryCredential = localStorage.getItem(
    `locallink-host-recovery-${query.get("roomId")}`
);
if (savedRecoveryCredential) {
    localStorage.setItem(
        `locallink-host-recovery-by-code-${room.code.toUpperCase()}`,
        JSON.stringify({
            roomId: query.get("roomId"),
            recoveryCredential: savedRecoveryCredential
        })
    );
}


function getJoinUrl() {
    const url = new URL("../Join Room/join_room.html", window.location.href);

    url.searchParams.set("room", room.code);
    url.searchParams.set("pin", room.pin);
    url.searchParams.set("roomName", room.roomName);
    url.searchParams.set("filesAllowed", String(room.filesAllowed));
    url.searchParams.set("autoCloseHours", String(room.autoCloseHours));
    url.searchParams.set("roomId", query.get("roomId"));

    return url.toString();
}

function getDashboardUrl() {
    const url = new URL(
        "../Sender dashboard/sender.html",
        window.location.href
    );
    url.searchParams.set("room", room.code);
    url.searchParams.set("roomId", query.get("roomId"));
    url.searchParams.set("expiresAt", query.get("expiresAt"));
    url.searchParams.set("deviceId", query.get("deviceId"));
    url.searchParams.set("role", query.get("role") || "HOST");
    url.searchParams.set("roomName", room.roomName);
    url.searchParams.set("filesAllowed", String(room.filesAllowed));
    url.searchParams.set("autoCloseHours", String(room.autoCloseHours));
    return url.toString();
}

const roomCreatedBanner = document.querySelector(
    "[data-room-created-banner]"
);
if (roomCreatedBanner) {
    const successRoomId = sessionStorage.getItem(
        "locallink-room-created-success"
    );
    roomCreatedBanner.hidden = successRoomId !== query.get("roomId");
    if (!roomCreatedBanner.hidden) {
        sessionStorage.removeItem("locallink-room-created-success");
    }
}

/*
 * Update the room details on the page.
 */
function renderRoomDetails() {
    roomCodeElement.textContent = room.code;
    roomPinElement.textContent =
        room.pin.replace(/(\d{3})(\d{3})/, "$1 $2");

    updateSettingsSummary();

    const qrContainer = document.getElementById("qr-code");
    if (typeof qrcode === "function") {
        const qr = qrcode(0, "M");
        qr.addData(getJoinUrl());
        qr.make();
        qrContainer.innerHTML = qr.createImgTag(5, 8);
        qrContainer.querySelector("img").alt = "QR code to join this room";
    } else {
        console.error("QR code generator is unavailable.");
        qrContainer.textContent = "QR code unavailable";
    }
}

/*
 * Display a short status message.
 */
function showToast(message) {
    toastElement.textContent = message;
    toastElement.classList.add("show");

    clearTimeout(toastTimeout);

    toastTimeout = setTimeout(() => {
        toastElement.classList.remove("show");
    }, 2500);
}

/*
 * Copy text using the Clipboard API.
 * A secure context (usually HTTPS or localhost) is required.
 */
async function copyText(text, successMessage) {
    try {
        if (navigator.clipboard && window.isSecureContext) {
            await navigator.clipboard.writeText(text);
        } else {
            const textarea = document.createElement("textarea");

            textarea.value = text;
            textarea.setAttribute("readonly", "");
            textarea.style.position = "fixed";
            textarea.style.opacity = "0";

            document.body.appendChild(textarea);

            textarea.select();

            const copied = document.execCommand("copy");

            textarea.remove();

            if (!copied) {
                throw new Error("Copy operation failed");
            }
        }

        showToast(successMessage);
    } catch (error) {
        console.error("Clipboard operation failed:", error);
        showToast("Copy failed. Please copy the text manually.");
    }
}

/*
 * Copy room code or PIN.
 */
document.querySelectorAll("[data-copy]").forEach((button) => {
    button.addEventListener("click", () => {
        const element = document.getElementById(button.dataset.copy);

        if (!element) {
            return;
        }

        const value = element.textContent.replace(/\s/g, "");

        copyText(value, "Copied successfully!");
    });
});

/*
 * Copy the room invitation URL.
 */
joinLinkButton.addEventListener("click", () => {
    copyText(getJoinUrl(), "Join link copied!");
});

/*
 * Share via supported device sharing options.
 */
shareViaButton.addEventListener("click", async () => {
    const shareData = {
        title: "Join my LocalLink room",
        text: `Join my room using code ${room.code} and PIN ${room.pin}.`,
        url: getJoinUrl()
    };

    if (navigator.share) {
        try {
            await navigator.share(shareData);
        } catch (error) {
            if (error.name !== "AbortError") {
                console.error("Sharing failed:", error);
                showToast("Unable to open the sharing menu.");
            }
        }
    } else {
        copyText(
            `${shareData.text}\n${shareData.url}`,
            "Invitation copied. You can share it now."
        );
    }
});

document.getElementById("open-room-dashboard")
    .addEventListener("click", () => {
        window.location.assign(getDashboardUrl());
    });

/*
 * Share the generated QR image when the browser supports file sharing.
 */
shareQrButton.addEventListener("click", async () => {
    const qrImage = document.querySelector(
        "#qr-code img, #qr-code canvas, #qr-code svg"
    );

    if (!qrImage) {
        showToast("QR code is not available yet.");
        return;
    }

    try {
        let blob;

        if (qrImage instanceof HTMLCanvasElement) {
            blob = await new Promise((resolve) => {
                qrImage.toBlob(resolve, "image/png");
            });
        } else if (qrImage instanceof HTMLImageElement) {
            const response = await fetch(qrImage.src);
            blob = await response.blob();
        } else {
            const svgText = new XMLSerializer().serializeToString(qrImage);

            blob = new Blob([svgText], {
                type: "image/svg+xml"
            });
        }

        if (!blob) {
            throw new Error("Unable to create QR image");
        }

        const file = new File(
            [blob],
            "locallink-qr.png",
            { type: blob.type }
        );

        if (
            navigator.share &&
            navigator.canShare &&
            navigator.canShare({ files: [file] })
        ) {
            await navigator.share({
                title: "LocalLink Room QR Code",
                text: `Join room ${room.code}`,
                files: [file]
            });
        } else {
            copyText(
                getJoinUrl(),
                "Image sharing is unavailable. Join link copied instead."
            );
        }
    } catch (error) {
        if (error.name !== "AbortError") {
            console.error("QR sharing failed:", error);
            showToast("Unable to share the QR code.");
        }
    }
});

/*
 * Open room settings.
 */
document.getElementById("edit-settings").addEventListener("click", () => {
    filesAllowedInput.checked = room.filesAllowed;
    autoCloseInput.value = String(room.autoCloseHours);

    settingsDialog.showModal();
});

/*
 * Close room settings.
 */
document.getElementById("close-settings").addEventListener("click", () => {
    settingsDialog.close();
});

/*
 * Close the dialog when the backdrop is clicked.
 */
settingsDialog.addEventListener("click", (event) => {
    if (event.target === settingsDialog) {
        settingsDialog.close();
    }
});

/*
 * Save settings locally for this page session.
 * Connect this handler to your backend API for persistence.
 */
settingsForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    room.filesAllowed = filesAllowedInput.checked;
    room.autoCloseHours = Number(autoCloseInput.value);
    query.set("filesAllowed", String(room.filesAllowed));
    query.set("autoCloseHours", String(room.autoCloseHours));
    const queryString = query.toString();
    window.history.replaceState(
        null,
        "",
        `${window.location.pathname}?${queryString}`
    );

    updateSettingsSummary();

    settingsDialog.close();

    showToast("Settings updated on this page.");
});

/*
 * Update the settings summary.
 */
function updateSettingsSummary() {
    const fileText = room.filesAllowed
        ? "Files allowed"
        : "Files disabled";

    settingsSummary.replaceChildren();

    const autoCloseText = room.autoCloseHours === 0
        ? "No auto close"
        : `Auto close: ${room.autoCloseHours} ${
            room.autoCloseHours === 1 ? "hour" : "hours"
        }`;
    settingsSummary.textContent = `${fileText} • ${autoCloseText}`;
}

/*
 * Initial page setup.
 */
renderRoomDetails();
