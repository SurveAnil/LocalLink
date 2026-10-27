
const profileName = document.getElementById("profile-name");
const charCount = document.getElementById("char-count");
const profileError = document.getElementById("profile-error");
const themeSelect = document.getElementById("theme-select");
const clearAfterSelect = document.getElementById("clear-after");
const saveStatus = document.getElementById("save-status");

const SETTINGS_KEY = "locallink-settings";
let saveStatusTimeout;

function showSaveStatus(message, isError = false) {
    window.clearTimeout(saveStatusTimeout);
    saveStatus.textContent = message;
    saveStatus.classList.toggle("is-error", isError);
    saveStatus.classList.add("is-visible");
    saveStatusTimeout = window.setTimeout(() => {
        saveStatus.classList.remove("is-visible");
    }, 3000);
}

function getSettings() {
    try {
        return JSON.parse(localStorage.getItem(SETTINGS_KEY)) || {};
    } catch {
        return {};
    }
}

function saveSettings() {
    const settings = {
        profileName: profileName.value.trim(),
        theme: themeSelect.value,
        clearAfter: clearAfterSelect.value
    };

    try {
        localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
        showSaveStatus("Settings saved");
    } catch {
        showSaveStatus("Unable to save settings on this device", true);
    }
}

function updateCharacterCount() {
    charCount.textContent = `${profileName.value.length}/32`;
}

function loadSettings() {
    const settings = getSettings();

    if (settings.profileName !== undefined) {
        profileName.value = settings.profileName;
    }

    if (["system", "light", "dark"].includes(settings.theme)) {
        themeSelect.value = settings.theme;
    }

    const allowedDurations = ["never", "1", "24", "72", "168"];

    if (allowedDurations.includes(settings.clearAfter)) {
        clearAfterSelect.value = settings.clearAfter;
    }

    updateCharacterCount();
    window.applyLocalLinkTheme(themeSelect.value);
}

profileName.addEventListener("input", () => {
    updateCharacterCount();
    profileError.textContent = "";
    window.clearTimeout(saveStatusTimeout);
    saveStatus.classList.remove("is-visible", "is-error");
    saveStatus.textContent = "";
});

profileName.addEventListener("change", () => {
    const name = profileName.value.trim();

    if (!name) {
        profileError.textContent = "Name is required.";
        return;
    }

    profileName.value = name;
    updateCharacterCount();
    saveSettings();
});

themeSelect.addEventListener("change", () => {
    saveSettings();
    window.applyLocalLinkTheme(themeSelect.value);
});

clearAfterSelect.addEventListener("change", saveSettings);

loadSettings();
