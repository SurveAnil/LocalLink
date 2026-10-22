
const profileName = document.getElementById("profile-name");
const charCount = document.getElementById("char-count");
const profileError = document.getElementById("profile-error");
const themeSelect = document.getElementById("theme-select");
const languageSelect = document.getElementById("language-select");
const clearAfterSelect = document.getElementById("clear-after");
const saveStatus = document.getElementById("save-status");

const SETTINGS_KEY = "locallink-settings";

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
        language: languageSelect.value,
        clearAfter: clearAfterSelect.value
    };

    try {
        localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
        saveStatus.textContent = "Settings saved";
    } catch {
        saveStatus.textContent = "Unable to save settings on this device";
    }
}

function updateCharacterCount() {
    charCount.textContent = `${profileName.value.length}/32`;
}

function applyTheme(theme) {
    const prefersDark = window.matchMedia(
        "(prefers-color-scheme: dark)"
    ).matches;

    const useDark = theme === "dark" ||
        (theme === "system" && prefersDark);

    document.body.classList.toggle("dark-theme", useDark);
}

function loadSettings() {
    const settings = getSettings();

    if (settings.profileName !== undefined) {
        profileName.value = settings.profileName;
    }

    if (["system", "light", "dark"].includes(settings.theme)) {
        themeSelect.value = settings.theme;
    }

    if (["en", "hi", "es", "fr"].includes(settings.language)) {
        languageSelect.value = settings.language;
    }

    const allowedDurations = ["never", "1", "24", "72", "168"];

    if (allowedDurations.includes(settings.clearAfter)) {
        clearAfterSelect.value = settings.clearAfter;
    }

    updateCharacterCount();
    applyTheme(themeSelect.value);
}

profileName.addEventListener("input", () => {
    updateCharacterCount();
    profileError.textContent = "";
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
    applyTheme(themeSelect.value);
    saveSettings();
});

languageSelect.addEventListener("change", saveSettings);
clearAfterSelect.addEventListener("change", saveSettings);

window.matchMedia("(prefers-color-scheme: dark)")
    .addEventListener("change", () => {
        if (themeSelect.value === "system") {
            applyTheme("system");
        }
    });

loadSettings();
