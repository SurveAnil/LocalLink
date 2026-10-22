(() => {
    const header = document.getElementById("header-container");

    if (!header) {
        return;
    }

    const fallbackMarkup = `
        <a class="logo" href="../../pages/Landing page/landing_page.html">
            <img src="../../assets/logo.png" alt="LocalLink">
        </a>
        <nav class="left-nav" aria-label="Main navigation">
            <a class="help" href="../../pages/Help page/help.html">Help</a>
            <a class="setting" href="../../pages/Setting/setting.html"
               aria-label="Settings">
                <img src="../../assets/setting-icon.png" alt="">
            </a>
        </nav>
    `;

    async function renderHeader() {
        if (window.location.protocol === "file:") {
            header.innerHTML = fallbackMarkup;
        } else {
            try {
                const response = await fetch(
                    "../../components/header/header.html"
                );
                if (!response.ok) {
                    throw new Error(
                        `Header request failed: ${response.status}`
                    );
                }
                header.innerHTML = await response.text();
            } catch (error) {
                console.error("Unable to load the shared header:", error);
                return;
            }
        }

        header.querySelectorAll("a").forEach((link) => {
            const target = new URL(link.href, document.baseURI);
            if (target.pathname === window.location.pathname) {
                link.setAttribute("aria-current", "page");
            }
        });
    }

    renderHeader();
})();