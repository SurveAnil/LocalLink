(() => {
    const SETTINGS_KEY = "locallink-settings";

    function getSavedTheme() {
        try {
            const settings = JSON.parse(
                localStorage.getItem(SETTINGS_KEY) || "{}"
            );
            return ["system", "light", "dark"].includes(settings.theme)
                ? settings.theme
                : "system";
        } catch (error) {
            console.error("Unable to read the saved theme:", error);
            return "system";
        }
    }

    function applyTheme(theme = getSavedTheme()) {
        const prefersDark = window.matchMedia(
            "(prefers-color-scheme: dark)"
        ).matches;
        document.body.classList.toggle(
            "dark-theme",
            theme === "dark" || (theme === "system" && prefersDark)
        );
    }

    window.applyLocalLinkTheme = applyTheme;
    applyTheme();

    window.matchMedia("(prefers-color-scheme: dark)")
        .addEventListener("change", () => {
            if (getSavedTheme() === "system") {
                applyTheme("system");
            }
        });

    window.addEventListener("storage", (event) => {
        if (event.key === SETTINGS_KEY) {
            applyTheme();
        }
    });
})();
