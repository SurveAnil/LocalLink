(() => {
    const settingsForm = document.getElementById("room-settings-form");
    const roomNameInput = document.getElementById("room-name");
    const roomNameCount = document.getElementById("room-name-count");
    const roomNameError = document.getElementById("room-name-error");
    const autoCloseInput = document.getElementById("auto-close");
    const filesAllowedInput = document.getElementById("files-allowed");
    const cancelButton = document.getElementById("cancel-button");
    const saveStatus = document.getElementById("save-status");
    const storageKey = "locallink.roomSettings";

    const defaultSettings = {
        roomName: "Project Meeting",
        autoCloseHours: 24,
        filesAllowed: true
    };

    function updateCharacterCount() {
        roomNameCount.textContent =
            `${roomNameInput.value.length}/${roomNameInput.maxLength}`;
    }

    function loadSettings() {
        const savedSettings = localStorage.getItem(storageKey);

        if (!savedSettings) {
            return defaultSettings;
        }

        const parsedSettings = JSON.parse(savedSettings);
        const validDurations = Array.from(autoCloseInput.options)
            .map((option) => Number(option.value));

        return {
            roomName: typeof parsedSettings.roomName === "string"
                ? parsedSettings.roomName.slice(0, roomNameInput.maxLength)
                : defaultSettings.roomName,
            autoCloseHours: validDurations.includes(
                Number(parsedSettings.autoCloseHours)
            )
                ? Number(parsedSettings.autoCloseHours)
                : defaultSettings.autoCloseHours,
            filesAllowed: typeof parsedSettings.filesAllowed === "boolean"
                ? parsedSettings.filesAllowed
                : defaultSettings.filesAllowed
        };
    }

    try {
        const settings = loadSettings();
        roomNameInput.value = settings.roomName;
        autoCloseInput.value = String(settings.autoCloseHours);
        filesAllowedInput.checked = settings.filesAllowed;
    } catch (error) {
        console.error("Unable to load saved room settings:", error);
        saveStatus.textContent = "Could not load saved room settings.";
        saveStatus.setAttribute("role", "alert");
    }

    updateCharacterCount();

    roomNameInput.addEventListener("input", () => {
        updateCharacterCount();
        roomNameError.textContent = "";
        saveStatus.textContent = "";
        saveStatus.setAttribute("role", "status");
    });

    settingsForm.addEventListener("submit", (event) => {
        event.preventDefault();

        const roomName = roomNameInput.value.trim();

        if (!roomName) {
            roomNameError.textContent = "Enter a room name.";
            roomNameInput.focus();
            return;
        }

        const settings = {
            roomName,
            autoCloseHours: Number(autoCloseInput.value),
            filesAllowed: filesAllowedInput.checked
        };

        try {
            localStorage.setItem(storageKey, JSON.stringify(settings));
            roomNameInput.value = roomName;
            updateCharacterCount();
            roomNameError.textContent = "";
            saveStatus.textContent = "Room settings saved on this device.";
            saveStatus.setAttribute("role", "status");
        } catch (error) {
            console.error("Unable to save room settings:", error);
            saveStatus.textContent = "Could not save room settings.";
            saveStatus.setAttribute("role", "alert");
        }
    });

    cancelButton.addEventListener("click", () => {
        window.history.back();
    });
})();
